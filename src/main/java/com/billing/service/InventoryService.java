package com.billing.service;

import com.billing.dto.PageResponse;
import com.billing.dto.inventory.InventoryLedgerEntryResponse;
import com.billing.dto.inventory.ProductBatchSummaryResponse;
import com.billing.entity.Company;
import com.billing.entity.InventoryLedgerEntry;
import com.billing.entity.Invoice;
import com.billing.entity.InvoiceItem;
import com.billing.entity.InvoiceItemAllocation;
import com.billing.entity.Product;
import com.billing.entity.ProductBatch;
import com.billing.entity.Purchase;
import com.billing.entity.PurchaseItem;
import com.billing.entity.enums.InventoryLedgerMovementType;
import com.billing.entity.enums.ProductBatchSourceType;
import com.billing.entity.enums.ProductBatchStatus;
import com.billing.exception.BadRequestException;
import com.billing.repository.InventoryLedgerRepository;
import com.billing.repository.InvoiceItemAllocationRepository;
import com.billing.repository.ProductBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductBatchRepository productBatchRepository;
    private final InventoryLedgerRepository inventoryLedgerRepository;
    private final InvoiceItemAllocationRepository invoiceItemAllocationRepository;
    private final AuditNameResolver auditNameResolver;

    @Transactional(readOnly = true)
    public PageResponse<InventoryLedgerEntryResponse> ledgerPage(String email, Company company, Long productId, LocalDate startDate, LocalDate endDate, String search, int page, int size) {
        return PageResponse.from(inventoryLedgerRepository.search(company, productId, startDate, endDate, normalizeSearch(search), PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100))))
                .map(this::toLedgerResponse));
    }

    @Transactional(readOnly = true)
    public ProductInventorySnapshot summarize(Company company, Product product, boolean includeBatches) {
        return summarize(company, List.of(product), includeBatches).getOrDefault(product.getId(), ProductInventorySnapshot.empty());
    }

    @Transactional(readOnly = true)
    public Map<Long, ProductInventorySnapshot> summarize(Company company, Collection<Product> products, boolean includeBatches) {
        if (products.isEmpty()) {
            return Map.of();
        }
        List<ProductBatch> batches = productBatchRepository.findByCompanyAndProductInOrderByProductIdAscBatchDateAscIdAsc(company, products);
        Map<Long, List<ProductBatch>> grouped = new LinkedHashMap<>();
        for (Product product : products) {
            grouped.put(product.getId(), new ArrayList<>());
        }
        for (ProductBatch batch : batches) {
            grouped.computeIfAbsent(batch.getProduct().getId(), ignored -> new ArrayList<>()).add(batch);
        }

        Map<Long, ProductInventorySnapshot> response = new HashMap<>();
        for (Product product : products) {
            List<ProductBatch> productBatches = grouped.getOrDefault(product.getId(), List.of());
            int currentStock = productBatches.stream().mapToInt(ProductBatch::getRemainingQty).sum();
            BigDecimal inventoryValue = productBatches.stream()
                    .map(batch -> scale(batch.getPurchaseRate()).multiply(BigDecimal.valueOf(batch.getRemainingQty())))
                    .reduce(zero(), BigDecimal::add);
            ProductBatch latestBatch = productBatches.stream()
                    .max(Comparator.comparing(ProductBatch::getBatchDate).thenComparing(ProductBatch::getId))
                    .orElse(null);
            List<ProductBatchSummaryResponse> batchResponses = includeBatches
                    ? productBatches.stream()
                    .sorted(Comparator.comparing(ProductBatch::getBatchDate).reversed().thenComparing(ProductBatch::getId, Comparator.reverseOrder()))
                    .map(this::toBatchResponse)
                    .toList()
                    : List.of();
            response.put(product.getId(), ProductInventorySnapshot.builder()
                    .currentStock(currentStock)
                    .inventoryValue(scale(inventoryValue))
                    .defaultSellingPrice(scale(latestBatch != null ? latestBatch.getSellingRate() : zero()))
                    .batches(batchResponses)
                    .build());
        }
        return response;
    }

    @Transactional(readOnly = true)
    public void assertSufficientStock(Company company, Product product, int requiredQty) {
        int available = currentStock(company, product);
        if (available < requiredQty) {
            throw new BadRequestException("Insufficient stock for product " + product.getName());
        }
    }

    @Transactional(readOnly = true)
    public int currentStock(Company company, Product product) {
        Integer remaining = productBatchRepository.sumRemainingQty(company, product);
        return remaining == null ? 0 : remaining;
    }

    @Transactional
    public ProductBatch createBatchForPurchase(Purchase purchase, PurchaseItem purchaseItem, String batchNo, ProductBatchSourceType sourceType) {
        ProductBatch batch = ProductBatch.builder()
                .company(purchase.getCompany())
                .product(purchaseItem.getProduct())
                .purchase(purchase)
                .purchaseItem(purchaseItem)
                .batchNo(batchNo)
                .batchDate(purchase.getPurchaseDate())
                .purchaseQty(purchaseItem.getQty())
                .remainingQty(purchaseItem.getQty())
                .purchaseRate(scale(purchaseItem.getPurchaseRate()))
                .sellingRate(scale(purchaseItem.getSellingRate()))
                .batchStatus(purchaseItem.getQty() > 0 ? ProductBatchStatus.ACTIVE : ProductBatchStatus.EXHAUSTED)
                .sourceType(sourceType)
                .build();
        ProductBatch saved = productBatchRepository.save(batch);
        createLedgerEntry(InventoryLedgerEntry.builder()
                .company(saved.getCompany())
                .product(saved.getProduct())
                .productBatch(saved)
                .purchase(purchase)
                .purchaseItem(purchaseItem)
                .movementType(InventoryLedgerMovementType.PURCHASE)
                .entryDate(purchase.getPurchaseDate())
                .qtyIn(saved.getPurchaseQty())
                .qtyOut(0)
                .balanceAfter(saved.getRemainingQty())
                .unitCost(scale(saved.getPurchaseRate()))
                .unitPrice(scale(saved.getSellingRate()))
                .referenceNo(purchase.getPurchaseNo())
                .remarks(sourceType == ProductBatchSourceType.OPENING_BALANCE ? "Opening inventory batch" : "Inventory replenishment purchase")
                .build());
        return saved;
    }

    @Transactional
    public Map<Long, List<InvoiceItemAllocation>> allocateForInvoice(Invoice invoice) {
        Map<Long, List<InvoiceItemAllocation>> allocationsByItem = new LinkedHashMap<>();
        Map<Long, Integer> batchBalances = new HashMap<>();

        for (InvoiceItem item : invoice.getItems()) {
            List<ProductBatch> fifoBatches = productBatchRepository.findByCompanyAndProductOrderByBatchDateAscIdAsc(invoice.getCompany(), item.getProduct()).stream()
                    .filter(batch -> batch.getRemainingQty() > 0)
                    .toList();
            int pendingQty = item.getQty();
            List<InvoiceItemAllocation> itemAllocations = new ArrayList<>();
            for (ProductBatch batch : fifoBatches) {
                int available = batchBalances.getOrDefault(batch.getId(), batch.getRemainingQty());
                if (available <= 0) {
                    continue;
                }
                int allocatedQty = Math.min(available, pendingQty);
                if (allocatedQty <= 0) {
                    continue;
                }
                int nextBalance = available - allocatedQty;
                batch.setRemainingQty(nextBalance);
                batch.setBatchStatus(nextBalance > 0 ? ProductBatchStatus.ACTIVE : ProductBatchStatus.EXHAUSTED);
                productBatchRepository.save(batch);
                batchBalances.put(batch.getId(), nextBalance);

                InvoiceItemAllocation allocation = invoiceItemAllocationRepository.save(InvoiceItemAllocation.builder()
                        .company(invoice.getCompany())
                        .invoice(invoice)
                        .invoiceItem(item)
                        .product(item.getProduct())
                        .productBatch(batch)
                        .allocatedQty(allocatedQty)
                        .purchaseRate(scale(batch.getPurchaseRate()))
                        .sellingRate(scale(batch.getSellingRate()))
                        .costAmount(scale(batch.getPurchaseRate().multiply(BigDecimal.valueOf(allocatedQty))))
                        .active(true)
                        .build());
                createLedgerEntry(InventoryLedgerEntry.builder()
                        .company(invoice.getCompany())
                        .product(item.getProduct())
                        .productBatch(batch)
                        .invoice(invoice)
                        .invoiceItem(item)
                        .invoiceAllocation(allocation)
                        .movementType(InventoryLedgerMovementType.SALE)
                        .entryDate(invoice.getInvoiceDate())
                        .qtyIn(0)
                        .qtyOut(allocatedQty)
                        .balanceAfter(nextBalance)
                        .unitCost(scale(batch.getPurchaseRate()))
                        .unitPrice(scale(item.getPrice()))
                        .referenceNo(invoice.getInvoiceNo())
                        .remarks("FIFO sale allocation")
                        .build());
                itemAllocations.add(allocation);
                pendingQty -= allocatedQty;
                if (pendingQty == 0) {
                    break;
                }
            }
            if (pendingQty > 0) {
                throw new BadRequestException("Insufficient stock for product " + item.getProduct().getName());
            }
            allocationsByItem.put(item.getId(), itemAllocations);
        }
        return allocationsByItem;
    }

    @Transactional
    public void releaseInvoiceAllocations(Invoice invoice, String remarks) {
        List<InvoiceItemAllocation> allocations = invoiceItemAllocationRepository.findByCompanyAndInvoiceAndActiveTrueOrderByIdAsc(invoice.getCompany(), invoice);
        for (InvoiceItemAllocation allocation : allocations) {
            ProductBatch batch = allocation.getProductBatch();
            int nextBalance = batch.getRemainingQty() + allocation.getAllocatedQty();
            batch.setRemainingQty(nextBalance);
            batch.setBatchStatus(ProductBatchStatus.ACTIVE);
            productBatchRepository.save(batch);

            allocation.setActive(false);
            invoiceItemAllocationRepository.save(allocation);

            createLedgerEntry(InventoryLedgerEntry.builder()
                    .company(invoice.getCompany())
                    .product(allocation.getProduct())
                    .productBatch(batch)
                    .invoice(invoice)
                    .invoiceItem(allocation.getInvoiceItem())
                    .invoiceAllocation(allocation)
                    .movementType(InventoryLedgerMovementType.SALE_REVERSAL)
                    .entryDate(invoice.getInvoiceDate())
                    .qtyIn(allocation.getAllocatedQty())
                    .qtyOut(0)
                    .balanceAfter(nextBalance)
                    .unitCost(scale(allocation.getPurchaseRate()))
                    .unitPrice(scale(allocation.getSellingRate()))
                    .referenceNo(invoice.getInvoiceNo())
                    .remarks(remarks)
                    .build());
        }
    }

    @Transactional
    public void createLedgerEntry(InventoryLedgerEntry entry) {
        inventoryLedgerRepository.save(entry);
    }

    private ProductBatchSummaryResponse toBatchResponse(ProductBatch batch) {
        return ProductBatchSummaryResponse.builder()
                .id(batch.getId())
                .batchNo(batch.getBatchNo())
                .batchDate(batch.getBatchDate())
                .purchaseQty(batch.getPurchaseQty())
                .remainingQty(batch.getRemainingQty())
                .purchaseRate(scale(batch.getPurchaseRate()))
                .sellingRate(scale(batch.getSellingRate()))
                .stockValue(scale(batch.getPurchaseRate().multiply(BigDecimal.valueOf(batch.getRemainingQty()))))
                .batchStatus(batch.getBatchStatus().name())
                .sourceType(batch.getSourceType().name())
                .purchaseId(batch.getPurchase() != null ? batch.getPurchase().getId() : null)
                .purchaseNo(batch.getPurchase() != null ? batch.getPurchase().getPurchaseNo() : null)
                .build();
    }

    private InventoryLedgerEntryResponse toLedgerResponse(InventoryLedgerEntry entry) {
        return InventoryLedgerEntryResponse.builder()
                .id(entry.getId())
                .entryDate(entry.getEntryDate())
                .movementType(entry.getMovementType().name())
                .productId(entry.getProduct().getId())
                .productName(entry.getProduct().getName())
                .batchId(entry.getProductBatch() != null ? entry.getProductBatch().getId() : null)
                .batchNo(entry.getProductBatch() != null ? entry.getProductBatch().getBatchNo() : null)
                .qtyIn(entry.getQtyIn())
                .qtyOut(entry.getQtyOut())
                .balanceAfter(entry.getBalanceAfter())
                .unitCost(scale(entry.getUnitCost()))
                .unitPrice(scale(entry.getUnitPrice()))
                .referenceNo(entry.getReferenceNo())
                .remarks(entry.getRemarks())
                .createdAt(entry.getCreatedAt())
                .createdBy(auditNameResolver.displayName(entry.getCreatedBy()))
                .build();
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? zero() : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeSearch(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @lombok.Builder
    @lombok.Getter
    public static class ProductInventorySnapshot {
        private int currentStock;
        private BigDecimal inventoryValue;
        private BigDecimal defaultSellingPrice;
        private List<ProductBatchSummaryResponse> batches;

        static ProductInventorySnapshot empty() {
            return ProductInventorySnapshot.builder()
                    .currentStock(0)
                    .inventoryValue(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .defaultSellingPrice(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .batches(List.of())
                    .build();
        }
    }
}
