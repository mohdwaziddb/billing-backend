package com.billing.service;

import com.billing.entity.Company;
import com.billing.dto.PageResponse;
import com.billing.exception.ResourceNotFoundException;
import com.billing.dto.product.ProductRequest;
import com.billing.dto.product.ProductResponse;
import com.billing.entity.Product;
import com.billing.entity.ProductCategory;
import com.billing.exception.BadRequestException;
import com.billing.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final AccessControlService accessControlService;
    private final ProductCategoryService productCategoryService;
    private final AuditLogService auditLogService;
    private final AuditNameResolver auditNameResolver;

    @Transactional
    public ProductResponse create(String email, ProductRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        validateProduct(company, request, null);
        ProductCategory productCategory = productCategoryService.getActiveByIdOrThrow(company, request.getCategoryId());

        Product product = Product.builder()
                .company(company)
                .name(request.getName())
                .productCategory(productCategory)
                .brand(request.getBrand())
                .sku(request.getSku())
                .hsnCode(request.getHsnCode())
                .purchasePrice(scale(request.getPurchasePrice()))
                .sellingPrice(scale(request.getSellingPrice()))
                .stockQty(normalizeCount(request.getStockQty()))
                .minStockQty(normalizeCount(request.getMinStockQty()))
                .taxPercent(scalePercent(request.getTaxPercent()))
                .active(Boolean.TRUE.equals(request.getActive()))
                .build();

        Product saved = productRepository.save(product);
        auditLogService.logCreate(email, company, "Product", "Product", saved.getId(), snapshot(saved));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list(String email, String search, Boolean active) {
        Company company = accessControlService.getCurrentCompany(email);
        return productRepository.findAllByCompanyWithFilters(company, active, normalizeSearch(search)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> page(String email, String search, Boolean active, int page, int size) {
        Company company = accessControlService.getCurrentCompany(email);
        return PageResponse.from(productRepository.findPageByCompanyWithFilters(company, active, normalizeSearch(search), pageRequest(page, size))
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(String email, Long productId) {
        Company company = accessControlService.getCurrentCompany(email);
        return toResponse(getProductOrThrow(company, productId));
    }

    @Transactional
    public ProductResponse update(String email, Long productId, ProductRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        validateProduct(company, request, productId);
        Product product = getProductOrThrow(company, productId);
        Map<String, Object> oldData = snapshot(product);
        ProductCategory productCategory = productCategoryService.getActiveByIdOrThrow(company, request.getCategoryId());

        product.setName(request.getName());
        product.setProductCategory(productCategory);
        product.setBrand(request.getBrand());
        product.setSku(request.getSku());
        product.setHsnCode(request.getHsnCode());
        product.setPurchasePrice(scale(request.getPurchasePrice()));
        product.setSellingPrice(scale(request.getSellingPrice()));
        product.setStockQty(normalizeCount(request.getStockQty()));
        product.setMinStockQty(normalizeCount(request.getMinStockQty()));
        product.setTaxPercent(scalePercent(request.getTaxPercent()));
        product.setActive(Boolean.TRUE.equals(request.getActive()));

        Product saved = productRepository.save(product);
        auditLogService.logUpdate(email, company, "Product", "Product", saved.getId(), oldData, snapshot(saved));
        return toResponse(saved);
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
                    // check if exists but different company
                    java.util.Optional<Product> exists = productRepository.findById(id);
                    if (exists.isPresent()) {
                        failures.put(id, "company_mismatch");
                        failed++;
                    } else {
                        failures.put(id, "not_found");
                        failed++;
                    }
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
        if (scale(request.getSellingPrice()).compareTo(scale(request.getPurchasePrice())) < 0) {
            throw new BadRequestException("Selling price cannot be less than purchase price");
        }
        if (productId == null) {
            if (productRepository.existsByCompanyAndSkuIgnoreCase(company, request.getSku())) {
                throw new BadRequestException("SKU already exists in your company");
            }
        } else if (productRepository.existsByCompanyAndSkuIgnoreCaseAndIdNot(company, request.getSku(), productId)) {
            throw new BadRequestException("SKU already exists in your company");
        }
    }

    private ProductResponse toResponse(Product product) {
        ProductCategory productCategory = product.getProductCategory();
        String categoryName = productCategory != null ? productCategory.getCategoryName() : null;
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .categoryId(productCategory != null ? productCategory.getId() : null)
                .categoryName(categoryName)
                .category(categoryName)
                .brand(product.getBrand())
                .sku(product.getSku())
                .hsnCode(product.getHsnCode())
                .purchasePrice(scale(product.getPurchasePrice()))
                .sellingPrice(scale(product.getSellingPrice()))
                .stockQty(product.getStockQty())
                .minStockQty(product.getMinStockQty())
                .taxPercent(scalePercent(product.getTaxPercent()))
                .active(product.isActive())
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

    private Map<String, Object> snapshot(Product product) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", product.getName());
        data.put("category", product.getProductCategory() != null ? product.getProductCategory().getCategoryName() : null);
        data.put("brand", product.getBrand());
        data.put("sku", product.getSku());
        data.put("purchasePrice", scale(product.getPurchasePrice()));
        data.put("sellingPrice", scale(product.getSellingPrice()));
        data.put("stockQty", product.getStockQty());
        data.put("minStockQty", product.getMinStockQty());
        data.put("taxPercent", scalePercent(product.getTaxPercent()));
        data.put("active", product.isActive());
        return data;
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
    }
}
