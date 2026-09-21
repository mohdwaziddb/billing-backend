package com.billing.service;

import com.billing.dto.PageResponse;
import com.billing.dto.purchase.PurchaseItemRequest;
import com.billing.dto.purchase.PurchaseItemResponse;
import com.billing.dto.purchase.PurchaseRequest;
import com.billing.dto.purchase.PurchaseResponse;
import com.billing.entity.Company;
import com.billing.entity.Product;
import com.billing.entity.ProductBatch;
import com.billing.entity.Purchase;
import com.billing.entity.PurchaseItem;
import com.billing.entity.enums.ProductBatchSourceType;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.InventoryLedgerRepository;
import com.billing.repository.InvoiceItemAllocationRepository;
import com.billing.repository.PurchaseItemRepository;
import com.billing.repository.PurchaseRepository;
import com.billing.repository.ProductBatchRepository;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final ProductBatchRepository productBatchRepository;
    private final InventoryLedgerRepository inventoryLedgerRepository;
    private final InvoiceItemAllocationRepository invoiceItemAllocationRepository;
    private final AccessControlService accessControlService;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final AuditLogService auditLogService;
    private final AuditNameResolver auditNameResolver;

    @Transactional
    public PurchaseResponse create(String email, PurchaseRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        validateRequest(request);

        Purchase purchase = Purchase.builder()
                .company(company)
                .purchaseNo(generatePurchaseNo(company, request.getPurchaseDate()))
                .purchaseDate(request.getPurchaseDate())
                .supplierName(blankToNull(request.getSupplierName()))
                .remarks(blankToNull(request.getRemarks()))
                .subtotal(zero())
                .totalAmount(zero())
                .active(true)
                .build();

        List<PurchaseItem> items = new ArrayList<>();
        BigDecimal subtotal = zero();
        for (PurchaseItemRequest itemRequest : request.getItems()) {
            Product product = productService.getProductOrThrow(company, itemRequest.getProductId());
            BigDecimal purchaseRate = scale(itemRequest.getPurchaseRate());
            BigDecimal sellingRate = scale(itemRequest.getSellingRate());
            if (sellingRate.compareTo(purchaseRate) < 0) {
                throw new BadRequestException("Selling rate cannot be less than purchase rate for " + product.getName());
            }
            PurchaseItem item = PurchaseItem.builder()
                    .company(company)
                    .purchase(purchase)
                    .product(product)
                    .qty(itemRequest.getQty())
                    .purchaseRate(purchaseRate)
                    .sellingRate(sellingRate)
                    .lineTotal(scale(purchaseRate.multiply(BigDecimal.valueOf(itemRequest.getQty()))))
                    .build();
            items.add(item);
            subtotal = subtotal.add(item.getLineTotal());
        }

        purchase.setSubtotal(scale(subtotal));
        purchase.setTotalAmount(scale(subtotal));
        purchase.getItems().clear();
        purchase.getItems().addAll(items);

        Purchase saved = purchaseRepository.saveAndFlush(purchase);
        List<PurchaseItem> persistedItems = purchaseItemRepository.saveAllAndFlush(saved.getItems());
        saved.getItems().clear();
        saved.getItems().addAll(persistedItems);

        Map<Long, ProductBatch> batchesByPurchaseItemId = new LinkedHashMap<>();
        int lineNo = 1;
        for (PurchaseItem item : saved.getItems()) {
            String batchNo = saved.getPurchaseNo() + "-B" + String.format("%03d", lineNo++);
            ProductBatch batch = inventoryService.createBatchForPurchase(saved, item, batchNo, ProductBatchSourceType.PURCHASE);
            batchesByPurchaseItemId.put(item.getId(), batch);
        }

        auditLogService.logCreate(email, company, "Purchase", "Purchase", saved.getId(), snapshot(saved));
        return toResponse(saved, batchesByPurchaseItemId);
    }

    @Transactional
    public PurchaseResponse create(Map<String, Object> param, String email) {
        PurchaseRequest purchaseRequest = mapToPurchaseRequest(param);
        return create(email, purchaseRequest);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseResponse> page(String email, Boolean active, String search, LocalDate startDate, LocalDate endDate, int page, int size) {
        Company company = accessControlService.getCurrentCompany(email);
        return PageResponse.from(purchaseRepository.search(company, active, normalizeSearch(search), startDate, endDate, org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100))))
                .map(purchase -> toResponse(purchase, Map.of())));
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseResponse> page(Map<String, Object> param, String email) {
        String searchFilter = DataTypeUtility.stringValue(param.get("search"));
        if (searchFilter.length() == 0) {
            searchFilter = null;
        }
        Boolean activeStatus = null;
        Object activeObject = param.get("active");
        if (activeObject != null) {
            if (activeObject instanceof Boolean) {
                activeStatus = (Boolean) activeObject;
            } else {
                String activeString = DataTypeUtility.stringValue(activeObject);
                if (activeString.length() > 0) {
                    activeStatus = DataTypeUtility.booleanValue(activeString);
                }
            }
        }
        String startDateString = DataTypeUtility.stringValue(param.get("startDate"));
        if (startDateString.length() == 0) {
            startDateString = DataTypeUtility.stringValue(param.get("start_date"));
        }
        LocalDate startDateValue = null;
        if (startDateString.length() > 0) {
            startDateValue = DataTypeUtility.parseLocalDate(startDateString, "yyyy-MM-dd");
            if (startDateValue == null) {
                startDateValue = DataTypeUtility.parseLocalDate(startDateString, "dd-MM-yyyy");
            }
            if (startDateValue == null) {
                startDateValue = DataTypeUtility.parseLocalDate(startDateString, "yyyy/MM/dd");
            }
        }
        String endDateString = DataTypeUtility.stringValue(param.get("endDate"));
        if (endDateString.length() == 0) {
            endDateString = DataTypeUtility.stringValue(param.get("end_date"));
        }
        LocalDate endDateValue = null;
        if (endDateString.length() > 0) {
            endDateValue = DataTypeUtility.parseLocalDate(endDateString, "yyyy-MM-dd");
            if (endDateValue == null) {
                endDateValue = DataTypeUtility.parseLocalDate(endDateString, "dd-MM-yyyy");
            }
            if (endDateValue == null) {
                endDateValue = DataTypeUtility.parseLocalDate(endDateString, "yyyy/MM/dd");
            }
        }
        int pageNumber = DataTypeUtility.integerValue(param.get("page"));
        int pageSize = DataTypeUtility.integerValue(param.get("size"));
        if (pageSize == 0) {
            pageSize = 20;
        }
        return page(email, activeStatus, searchFilter, startDateValue, endDateValue, pageNumber, pageSize);
    }

    @Transactional(readOnly = true)
    public PurchaseResponse get(String email, Long purchaseId) {
        Company company = accessControlService.getCurrentCompany(email);
        Purchase purchase = purchaseRepository.findByIdWithItemsAndCompany(purchaseId, company)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found"));
        return toResponse(purchase, batchesByPurchaseItemId(company, purchase));
    }

    @Transactional
    public void delete(String email, Long purchaseId) {
        Company company = accessControlService.getCurrentCompany(email);
        Purchase purchase = purchaseRepository.findByIdWithItemsAndCompany(purchaseId, company)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found"));
        if (!purchase.isActive()) {
            throw new BadRequestException("This purchase is already inactive.");
        }
        List<ProductBatch> batches = productBatchRepository.findByCompanyAndPurchaseOrderByBatchDateAscIdAsc(company, purchase);
        if (!batches.isEmpty() && invoiceItemAllocationRepository.countByCompanyAndProductBatchInAndActiveTrue(company, batches) > 0) {
            throw new BadRequestException("This purchase cannot be deleted because some inventory from its batches has already been used in sales.");
        }

        Map<String, Object> oldData = snapshot(purchase);
        inventoryLedgerRepository.deleteAll(inventoryLedgerRepository.findByCompanyAndPurchaseOrderByEntryDateAscIdAsc(company, purchase));
        productBatchRepository.deleteAll(batches);
        purchase.setActive(false);
        purchaseRepository.save(purchase);
        auditLogService.logDelete(email, company, "Purchase", "Purchase", purchaseId, oldData);
    }

    private PurchaseRequest mapToPurchaseRequest(Map<String, Object> param) {
        PurchaseRequest purchaseRequest = new PurchaseRequest();
        String purchaseDateString = DataTypeUtility.stringValue(param.get("purchaseDate"));
        if (purchaseDateString.length() == 0) {
            purchaseDateString = DataTypeUtility.stringValue(param.get("purchase_date"));
        }
        LocalDate purchaseDateValue = null;
        if (purchaseDateString.length() > 0) {
            purchaseDateValue = DataTypeUtility.parseLocalDate(purchaseDateString, "yyyy-MM-dd");
            if (purchaseDateValue == null) {
                purchaseDateValue = DataTypeUtility.parseLocalDate(purchaseDateString, "dd-MM-yyyy");
            }
            if (purchaseDateValue == null) {
                purchaseDateValue = DataTypeUtility.parseLocalDate(purchaseDateString, "yyyy/MM/dd");
            }
        }
        purchaseRequest.setPurchaseDate(purchaseDateValue);
        String supplierNameValue = DataTypeUtility.stringValue(param.get("supplierName"));
        if (supplierNameValue.length() == 0) {
            supplierNameValue = DataTypeUtility.stringValue(param.get("supplier_name"));
        }
        if (supplierNameValue.length() == 0) {
            supplierNameValue = null;
        }
        purchaseRequest.setSupplierName(supplierNameValue);
        String remarksValue = DataTypeUtility.stringValue(param.get("remarks"));
        if (remarksValue.length() == 0) {
            remarksValue = null;
        }
        purchaseRequest.setRemarks(remarksValue);
        List<PurchaseItemRequest> purchaseItemList = new ArrayList<>();
        Object itemsObject = param.get("items");
        if (itemsObject instanceof List) {
            for (Object itemObject : (List<?>) itemsObject) {
                if (itemObject instanceof Map) {
                    Map<String, Object> itemMap = (Map<String, Object>) itemObject;
                    PurchaseItemRequest purchaseItemRequest = new PurchaseItemRequest();
                    Long productIdValue = DataTypeUtility.getForeignKeyValue(itemMap.get("productId"));
                    if (productIdValue == null) {
                        productIdValue = DataTypeUtility.getForeignKeyValue(itemMap.get("product_id"));
                    }
                    purchaseItemRequest.setProductId(productIdValue);
                    Integer qtyValue = DataTypeUtility.integerNullValue(itemMap.get("qty"));
                    if (qtyValue == null) {
                        qtyValue = DataTypeUtility.integerNullValue(itemMap.get("quantity"));
                    }
                    if (qtyValue == null) {
                        qtyValue = DataTypeUtility.integerNullValue(itemMap.get("qtyValue"));
                    }
                    purchaseItemRequest.setQty(qtyValue);
                    BigDecimal purchaseRateValue = DataTypeUtility.bigDecimalObjectValue(itemMap.get("purchaseRate"));
                    if (purchaseRateValue == null) {
                        purchaseRateValue = DataTypeUtility.bigDecimalObjectValue(itemMap.get("purchase_rate"));
                    }
                    purchaseItemRequest.setPurchaseRate(purchaseRateValue);
                    BigDecimal sellingRateValue = DataTypeUtility.bigDecimalObjectValue(itemMap.get("sellingRate"));
                    if (sellingRateValue == null) {
                        sellingRateValue = DataTypeUtility.bigDecimalObjectValue(itemMap.get("selling_rate"));
                    }
                    purchaseItemRequest.setSellingRate(sellingRateValue);
                    purchaseItemList.add(purchaseItemRequest);
                }
            }
        }
        purchaseRequest.setItems(purchaseItemList);
        return purchaseRequest;
    }

    private PurchaseResponse toResponse(Purchase purchase, Map<Long, ProductBatch> createdBatches) {
        return PurchaseResponse.builder()
                .id(purchase.getId())
                .purchaseNo(purchase.getPurchaseNo())
                .purchaseDate(purchase.getPurchaseDate())
                .supplierName(purchase.getSupplierName())
                .remarks(purchase.getRemarks())
                .subtotal(scale(purchase.getSubtotal()))
                .totalAmount(scale(purchase.getTotalAmount()))
                .active(purchase.isActive())
                .items(purchase.getItems().stream().map(item -> {
                    ProductBatch batch = createdBatches.get(item.getId());
                    return PurchaseItemResponse.builder()
                            .id(item.getId())
                            .productId(item.getProduct().getId())
                            .productName(item.getProduct().getName())
                            .qty(item.getQty())
                            .purchaseRate(scale(item.getPurchaseRate()))
                            .sellingRate(scale(item.getSellingRate()))
                            .lineTotal(scale(item.getLineTotal()))
                            .batchId(batch != null ? batch.getId() : null)
                            .batchNo(batch != null ? batch.getBatchNo() : null)
                            .build();
                }).toList())
                .createdAt(purchase.getCreatedAt())
                .updatedAt(purchase.getUpdatedAt())
                .createdBy(auditNameResolver.displayName(purchase.getCreatedBy()))
                .createdByRef(purchase.getCreatedBy())
                .updatedBy(auditNameResolver.displayName(purchase.getUpdatedBy()))
                .build();
    }

    private Map<Long, ProductBatch> batchesByPurchaseItemId(Company company, Purchase purchase) {
        Map<Long, ProductBatch> batchesByPurchaseItemId = new LinkedHashMap<>();
        for (ProductBatch batch : productBatchRepository.findByCompanyAndPurchaseOrderByBatchDateAscIdAsc(company, purchase)) {
            if (batch.getPurchaseItem() != null) {
                batchesByPurchaseItemId.put(batch.getPurchaseItem().getId(), batch);
            }
        }
        return batchesByPurchaseItemId;
    }

    private String generatePurchaseNo(Company company, LocalDate purchaseDate) {
        long sequence = purchaseRepository.countTodayByCompany(company) + 1;
        return "PUR-" + purchaseDate.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%03d", sequence);
    }

    private void validateRequest(PurchaseRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Add at least one purchase item");
        }
    }

    private Map<String, Object> snapshot(Purchase purchase) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("purchaseNo", purchase.getPurchaseNo());
        data.put("purchaseDate", purchase.getPurchaseDate());
        data.put("supplierName", purchase.getSupplierName());
        data.put("remarks", purchase.getRemarks());
        data.put("subtotal", scale(purchase.getSubtotal()));
        data.put("totalAmount", scale(purchase.getTotalAmount()));
        data.put("active", purchase.isActive());
        data.put("items", purchase.getItems().stream().map(item -> Map.of(
                "productId", item.getProduct().getId(),
                "productName", item.getProduct().getName(),
                "qty", item.getQty(),
                "purchaseRate", scale(item.getPurchaseRate()),
                "sellingRate", scale(item.getSellingRate()),
                "lineTotal", scale(item.getLineTotal())
        )).toList());
        return data;
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
