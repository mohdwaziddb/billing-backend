package com.billing.config;

import com.billing.entity.Company;
import com.billing.entity.InventoryLedgerEntry;
import com.billing.entity.InvoiceItem;
import com.billing.entity.InvoiceItemAllocation;
import com.billing.entity.Product;
import com.billing.entity.ProductBatch;
import com.billing.entity.Purchase;
import com.billing.entity.PurchaseItem;
import com.billing.entity.enums.InventoryLedgerMovementType;
import com.billing.entity.enums.ProductBatchSourceType;
import com.billing.entity.enums.ProductBatchStatus;
import com.billing.repository.CompanyRepository;
import com.billing.repository.InvoiceItemAllocationRepository;
import com.billing.repository.InvoiceItemRepository;
import com.billing.repository.ProductBatchRepository;
import com.billing.repository.ProductRepository;
import com.billing.repository.PurchaseItemRepository;
import com.billing.repository.PurchaseRepository;
import com.billing.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LegacyInventoryBackfillInitializer implements ApplicationRunner {

    private final CompanyRepository companyRepository;
    private final ProductRepository productRepository;
    private final ProductBatchRepository productBatchRepository;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final InvoiceItemAllocationRepository invoiceItemAllocationRepository;
    private final InventoryService inventoryService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!legacyColumnsAvailable()) {
            return;
        }
        for (Company company : companyRepository.findAll()) {
            for (Product product : productRepository.findByCompanyOrderByCreatedAtDesc(company)) {
                if (!productBatchRepository.findByCompanyAndProductOrderByBatchDateAscIdAsc(company, product).isEmpty()) {
                    continue;
                }
                LegacyRow legacy = loadLegacyRow(product.getId());
                if (legacy == null) {
                    continue;
                }
                List<InvoiceItem> historicalItems = invoiceItemRepository.findHistoricalByCompanyAndProduct(company, product);
                int soldQty = historicalItems.stream().mapToInt(InvoiceItem::getQty).sum();
                int openingQty = Math.max(0, legacy.stockQty()) + soldQty;
                if (openingQty <= 0 && scale(legacy.purchasePrice()).compareTo(BigDecimal.ZERO) == 0 && scale(legacy.sellingPrice()).compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }

                LocalDate openingDate = resolveOpeningDate(legacy.createdAt(), historicalItems);
                String purchaseNo = "OPEN-PUR-" + product.getId();
                BigDecimal purchaseRate = scale(legacy.purchasePrice());
                BigDecimal sellingRate = scale(legacy.sellingPrice());
                BigDecimal purchaseTotal = scale(purchaseRate.multiply(BigDecimal.valueOf(openingQty)));

                Purchase purchase = purchaseRepository.findByCompanyAndPurchaseNo(company, purchaseNo)
                        .orElseGet(() -> purchaseRepository.saveAndFlush(Purchase.builder()
                                .company(company)
                                .purchaseNo(purchaseNo)
                                .purchaseDate(openingDate)
                                .supplierName("Opening Balance")
                                .remarks("System generated legacy inventory migration")
                                .subtotal(purchaseTotal)
                                .totalAmount(purchaseTotal)
                                .active(true)
                                .build()));

                PurchaseItem purchaseItem = purchase.getItems().stream()
                        .filter(item -> item.getProduct() != null && item.getProduct().getId().equals(product.getId()))
                        .findFirst()
                        .orElseGet(() -> {
                            PurchaseItem createdItem = purchaseItemRepository.saveAndFlush(PurchaseItem.builder()
                                    .company(company)
                                    .purchase(purchase)
                                    .product(product)
                                    .qty(openingQty)
                                    .purchaseRate(purchaseRate)
                                    .sellingRate(sellingRate)
                                    .lineTotal(purchaseTotal)
                                    .build());
                            purchase.getItems().add(createdItem);
                            return createdItem;
                        });

                ProductBatch batch = inventoryService.createBatchForPurchase(purchase, purchaseItem, "OPEN-BATCH-" + product.getId(), ProductBatchSourceType.OPENING_BALANCE);

                for (InvoiceItem historicalItem : historicalItems) {
                    int nextBalance = batch.getRemainingQty() - historicalItem.getQty();
                    batch.setRemainingQty(nextBalance);
                    batch.setBatchStatus(nextBalance > 0 ? ProductBatchStatus.ACTIVE : ProductBatchStatus.EXHAUSTED);
                    productBatchRepository.save(batch);

                    InvoiceItemAllocation allocation = invoiceItemAllocationRepository.save(InvoiceItemAllocation.builder()
                            .company(company)
                            .invoice(historicalItem.getInvoice())
                            .invoiceItem(historicalItem)
                            .product(product)
                            .productBatch(batch)
                            .allocatedQty(historicalItem.getQty())
                            .purchaseRate(purchaseRate)
                            .sellingRate(sellingRate)
                            .costAmount(scale(purchaseRate.multiply(BigDecimal.valueOf(historicalItem.getQty()))))
                            .active(true)
                            .build());

                    inventoryService.createLedgerEntry(InventoryLedgerEntry.builder()
                            .company(company)
                            .product(product)
                            .productBatch(batch)
                            .invoice(historicalItem.getInvoice())
                            .invoiceItem(historicalItem)
                            .invoiceAllocation(allocation)
                            .movementType(InventoryLedgerMovementType.SALE)
                            .entryDate(historicalItem.getInvoice().getInvoiceDate())
                            .qtyIn(0)
                            .qtyOut(historicalItem.getQty())
                            .balanceAfter(nextBalance)
                            .unitCost(purchaseRate)
                            .unitPrice(scale(historicalItem.getPrice()))
                            .referenceNo(historicalItem.getInvoice().getInvoiceNo())
                            .remarks("Legacy batch cost allocation")
                            .build());
                }
            }
        }
    }

    private boolean legacyColumnsAvailable() {
        try {
            jdbcTemplate.queryForObject("select purchase_price from products limit 1", BigDecimal.class);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private LegacyRow loadLegacyRow(Long productId) {
        try {
            return jdbcTemplate.query("select purchase_price, selling_price, stock_qty, created_at from products where id = ?",
                    rs -> rs.next()
                            ? new LegacyRow(
                            rs.getBigDecimal("purchase_price"),
                            rs.getBigDecimal("selling_price"),
                            rs.getInt("stock_qty"),
                            rs.getTimestamp("created_at"))
                            : null,
                    productId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDate resolveOpeningDate(Timestamp createdAt, List<InvoiceItem> historicalItems) {
        if (!historicalItems.isEmpty()) {
            return historicalItems.get(0).getInvoice().getInvoiceDate();
        }
        return createdAt != null ? createdAt.toLocalDateTime().toLocalDate() : LocalDate.now();
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private record LegacyRow(BigDecimal purchasePrice, BigDecimal sellingPrice, int stockQty, Timestamp createdAt) {
    }
}
