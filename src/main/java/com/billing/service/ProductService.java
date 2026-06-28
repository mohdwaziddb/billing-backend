package com.billing.service;

import com.billing.dto.PageResponse;
import com.billing.dto.inventory.ProductBatchSummaryResponse;
import com.billing.dto.product.ProductRequest;
import com.billing.dto.product.ProductResponse;
import com.billing.entity.Company;
import com.billing.entity.Product;
import com.billing.entity.ProductCategory;
import com.billing.entity.ProductSubCategory;
import com.billing.entity.TaxMaster;
import com.billing.entity.User;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final AccessControlService accessControlService;
    private final ProductCategoryService productCategoryService;
    private final ProductSubCategoryService productSubCategoryService;
    private final TaxMasterService taxMasterService;
    private final AuditLogService auditLogService;
    private final AuditNameResolver auditNameResolver;
    private final TaxValidator taxValidator;
    private final InventoryService inventoryService;

    @Transactional
    public ProductResponse create(String email, ProductRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        validateProduct(company, request, null);
        ProductCategory productCategory = productCategoryService.getActiveByIdOrThrow(company, request.getCategoryId());
        ProductSubCategory productSubCategory = productSubCategoryService.getActiveByIdOrThrow(company, productCategory.getId(), request.getSubCategoryId());
        boolean taxable = !Boolean.FALSE.equals(request.getTaxable());
        TaxMaster taxMaster = taxMasterService.resolveForProduct(company, request.getTaxMasterId(), null, taxable);
        taxValidator.requireProductTax(taxMaster, taxable);

        Product product = Product.builder()
                .company(company)
                .name(request.getName())
                .productCategory(productCategory)
                .productSubCategory(productSubCategory)
                .brand(blankToNull(request.getBrand()))
                .sku(request.getSku())
                .hsnCode(blankToNull(request.getHsnCode()))
                .minStockQty(normalizeCount(request.getMinStockQty()))
                .taxMaster(taxMaster)
                .taxable(taxable)
                .active(Boolean.TRUE.equals(request.getActive()))
                .build();

        Product saved = productRepository.save(product);
        auditLogService.logCreate(email, company, "Product", "Product", saved.getId(), snapshot(saved));
        return toResponse(saved, inventoryService.summarize(company, saved, true));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list(String email, Long categoryId, Long subCategoryId, String search, Boolean active) {
        Company company = companyScope(email);
        List<Product> products = productRepository.findAllByCompanyWithFilters(company, active, categoryId, subCategoryId, normalizeSearch(search));
        Map<Long, InventoryService.ProductInventorySnapshot> inventoryByProduct = inventoryService.summarize(company, products, false);
        return products.stream()
                .map(product -> toResponse(product, inventoryByProduct.getOrDefault(product.getId(), InventoryService.ProductInventorySnapshot.empty())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> page(String email, Long categoryId, Long subCategoryId, String search, Boolean active, int page, int size) {
        Company company = companyScope(email);
        org.springframework.data.domain.Page<Product> productPage = productRepository.findPageByCompanyWithFilters(company, active, categoryId, subCategoryId, normalizeSearch(search), pageRequest(page, size));
        Map<Long, InventoryService.ProductInventorySnapshot> inventoryByProduct = inventoryService.summarize(company, productPage.getContent(), false);
        return PageResponse.from(productPage.map(product -> toResponse(product, inventoryByProduct.getOrDefault(product.getId(), InventoryService.ProductInventorySnapshot.empty()))));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(String email, Long productId) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        Product product = getProductOrThrow(company, productId);
        return toResponse(product, inventoryService.summarize(company, product, true));
    }

    @Transactional
    public ProductResponse update(String email, Long productId, ProductRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        validateProduct(company, request, productId);
        Product product = getProductOrThrow(company, productId);
        Map<String, Object> oldData = snapshot(product);
        ProductCategory productCategory = productCategoryService.getActiveByIdOrThrow(company, request.getCategoryId());
        ProductSubCategory productSubCategory = productSubCategoryService.getActiveByIdOrThrow(company, productCategory.getId(), request.getSubCategoryId());
        boolean taxable = !Boolean.FALSE.equals(request.getTaxable());
        TaxMaster taxMaster = taxMasterService.resolveForProduct(company, request.getTaxMasterId(), null, taxable);
        taxValidator.requireProductTax(taxMaster, taxable);

        product.setName(request.getName());
        product.setProductCategory(productCategory);
        product.setProductSubCategory(productSubCategory);
        product.setBrand(blankToNull(request.getBrand()));
        product.setSku(request.getSku());
        product.setHsnCode(blankToNull(request.getHsnCode()));
        product.setMinStockQty(normalizeCount(request.getMinStockQty()));
        product.setTaxMaster(taxMaster);
        product.setTaxable(taxable);
        product.setActive(Boolean.TRUE.equals(request.getActive()));

        Product saved = productRepository.save(product);
        auditLogService.logUpdate(email, company, "Product", "Product", saved.getId(), oldData, snapshot(saved));
        return toResponse(saved, inventoryService.summarize(company, saved, true));
    }

    @Transactional
    public void delete(String email, Long productId) {
        Company company = accessControlService.getCurrentCompany(email);
        Product product = getProductOrThrow(company, productId);
        Map<String, Object> oldData = snapshot(product);
        product.setActive(false);
        Product saved = productRepository.save(product);
        auditLogService.logDelete(email, company, "Product", "Product", saved.getId(), oldData);
    }

    public com.billing.dto.BulkDeleteResponse deleteBulk(String email, java.util.List<Long> ids) {
        Company company = accessControlService.getCurrentCompany(email);
        int deleted = 0;
        int failed = 0;
        java.util.Map<Long, String> failures = new java.util.LinkedHashMap<>();

        for (Long id : ids) {
            try {
                java.util.Optional<Product> opt = productRepository.findByIdAndCompany(id, company);
                if (opt.isEmpty()) {
                    failures.put(id, "not_found");
                    failed++;
                    continue;
                }

                Product product = opt.get();
                Map<String, Object> oldData = snapshot(product);
                product.setActive(false);
                Product saved = productRepository.save(product);
                auditLogService.logDelete(email, company, "Product", "Product", saved.getId(), oldData);
                deleted++;
            } catch (Exception ex) {
                failures.put(id, ex.getMessage() != null ? ex.getMessage() : "error");
                failed++;
            }
        }

        return com.billing.dto.BulkDeleteResponse.builder().deleted(deleted).failed(failed).failures(failures).build();
    }

    public Product getProductOrThrow(Company company, Long productId) {
        return productRepository.findByIdAndCompany(productId, company)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private void validateProduct(Company company, ProductRequest request, Long productId) {
        if (productId == null) {
            if (productRepository.existsByCompanyAndSkuIgnoreCase(company, request.getSku())) {
                throw new BadRequestException("SKU already exists in your company");
            }
        } else if (productRepository.existsByCompanyAndSkuIgnoreCaseAndIdNot(company, request.getSku(), productId)) {
            throw new BadRequestException("SKU already exists in your company");
        }
    }

    private ProductResponse toResponse(Product product, InventoryService.ProductInventorySnapshot inventorySnapshot) {
        ProductCategory productCategory = product.getProductCategory();
        ProductSubCategory productSubCategory = product.getProductSubCategory();
        TaxMaster taxMaster = product.getTaxMaster();
        String categoryName = productCategory != null ? productCategory.getCategoryName() : null;
        String subCategoryName = productSubCategory != null ? productSubCategory.getSubCategoryName() : null;
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .categoryId(productCategory != null ? productCategory.getId() : null)
                .categoryName(categoryName)
                .category(categoryName)
                .subCategoryId(productSubCategory != null ? productSubCategory.getId() : null)
                .subCategoryName(subCategoryName)
                .subCategory(subCategoryName)
                .brand(product.getBrand())
                .sku(product.getSku())
                .hsnCode(product.getHsnCode())
                .taxMasterId(taxMaster != null ? taxMaster.getId() : null)
                .taxName(taxMaster != null ? taxMaster.getTaxName() : null)
                .taxCode(taxMaster != null ? taxMaster.getTaxCode() : null)
                .taxType(taxMaster != null ? taxMaster.getTaxType().name() : null)
                .sellingPrice(scale(inventorySnapshot.getDefaultSellingPrice()))
                .stockQty(inventorySnapshot.getCurrentStock())
                .inventoryValue(scale(inventorySnapshot.getInventoryValue()))
                .minStockQty(product.getMinStockQty())
                .taxPercent(scalePercent(product.getTaxMaster() != null ? product.getTaxMaster().getRate() : BigDecimal.ZERO))
                .taxable(product.isTaxable())
                .active(product.isActive())
                .batches(inventorySnapshot.getBatches())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .createdBy(auditNameResolver.displayName(product.getCreatedBy()))
                .updatedBy(auditNameResolver.displayName(product.getUpdatedBy()))
                .build();
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scalePercent(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private Integer normalizeCount(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalizeSearch(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, Object> snapshot(Product product) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", product.getName());
        data.put("category", product.getProductCategory() != null ? product.getProductCategory().getCategoryName() : null);
        data.put("subCategory", product.getProductSubCategory() != null ? product.getProductSubCategory().getSubCategoryName() : null);
        data.put("brand", product.getBrand());
        data.put("sku", product.getSku());
        data.put("hsnCode", product.getHsnCode());
        data.put("minStockQty", product.getMinStockQty());
        data.put("taxMasterId", product.getTaxMaster() != null ? product.getTaxMaster().getId() : null);
        data.put("taxName", product.getTaxMaster() != null ? product.getTaxMaster().getTaxName() : null);
        data.put("taxPercent", scalePercent(product.getTaxMaster() != null ? product.getTaxMaster().getRate() : BigDecimal.ZERO));
        data.put("taxable", product.isTaxable());
        data.put("active", product.isActive());
        return data;
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
    }

    private Company companyScope(String email) {
        User user = accessControlService.getCurrentUser(email);
        return accessControlService.requireCompany(user);
    }
}
