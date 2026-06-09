package com.billing.service;

import com.billing.dto.productcategory.ProductCategoryRequest;
import com.billing.dto.productcategory.ProductCategoryResponse;
import com.billing.dto.PageResponse;
import com.billing.entity.Company;
import com.billing.entity.ProductCategory;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.ProductCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductCategoryService {

    private final ProductCategoryRepository productCategoryRepository;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<ProductCategoryResponse> list(String email, String search, Boolean active) {
        Company company = accessControlService.getCurrentCompany(email);
        return productCategoryRepository.findAllByCompanyWithFilters(company, active, normalizeSearch(search)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductCategoryResponse> page(String email, String search, Boolean active, int page, int size) {
        Company company = accessControlService.getCurrentCompany(email);
        return PageResponse.from(productCategoryRepository.findPageByCompanyWithFilters(company, active, normalizeSearch(search), pageRequest(page, size))
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public ProductCategoryResponse get(String email, Long categoryId) {
        Company company = accessControlService.getCurrentCompany(email);
        return toResponse(getCategoryOrThrow(company, categoryId));
    }

    @Transactional
    public ProductCategoryResponse create(String email, ProductCategoryRequest request) {
        Company company = requireOwnerCompany(email);
        String categoryName = normalizeName(request.getCategoryName());
        if (productCategoryRepository.existsByCompanyAndCategoryNameIgnoreCase(company, categoryName)) {
            throw new BadRequestException("Product category already exists");
        }

        ProductCategory category = ProductCategory.builder()
                .company(company)
                .categoryName(categoryName)
                .description(blankToNull(request.getDescription()))
                .active(Boolean.TRUE.equals(request.getActive()))
                .build();
        ProductCategory saved = productCategoryRepository.save(category);
        auditLogService.logCreate(email, company, "Product Category", "ProductCategory", saved.getId(), snapshot(saved));
        return toResponse(saved);
    }

    @Transactional
    public ProductCategoryResponse update(String email, Long categoryId, ProductCategoryRequest request) {
        Company company = requireOwnerCompany(email);
        ProductCategory category = getCategoryOrThrow(company, categoryId);
        Map<String, Object> oldData = snapshot(category);
        String categoryName = normalizeName(request.getCategoryName());
        if (productCategoryRepository.existsByCompanyAndCategoryNameIgnoreCaseAndIdNot(company, categoryName, categoryId)) {
            throw new BadRequestException("Product category already exists");
        }

        category.setCategoryName(categoryName);
        category.setDescription(blankToNull(request.getDescription()));
        category.setActive(Boolean.TRUE.equals(request.getActive()));
        ProductCategory saved = productCategoryRepository.save(category);
        auditLogService.logUpdate(email, company, "Product Category", "ProductCategory", saved.getId(), oldData, snapshot(saved));
        return toResponse(saved);
    }

    @Transactional
    public void delete(String email, Long categoryId) {
        Company company = requireOwnerCompany(email);
        ProductCategory category = getCategoryOrThrow(company, categoryId);
        Map<String, Object> oldData = snapshot(category);
        category.setActive(false);
        ProductCategory saved = productCategoryRepository.save(category);
        auditLogService.logDelete(email, company, "Product Category", "ProductCategory", saved.getId(), oldData);
    }

    public ProductCategory getActiveByNameOrThrow(Company company, String categoryName) {
        return productCategoryRepository.findByCompanyAndCategoryNameIgnoreCaseAndActiveTrue(company, normalizeName(categoryName))
                .orElseThrow(() -> new BadRequestException("Select an active product category"));
    }

    public ProductCategory getActiveByIdOrThrow(Company company, Long categoryId) {
        if (categoryId == null) {
            throw new BadRequestException("Product category is required");
        }
        ProductCategory category = getCategoryOrThrow(company, categoryId);
        if (!category.isActive()) {
            throw new BadRequestException("Select an active product category");
        }
        return category;
    }

    private ProductCategory getCategoryOrThrow(Company company, Long categoryId) {
        return productCategoryRepository.findByIdAndCompany(categoryId, company)
                .orElseThrow(() -> new ResourceNotFoundException("Product category not found"));
    }

    private Company requireOwnerCompany(String email) {
        return accessControlService.requireOwnerCompany(email);
    }

    private ProductCategoryResponse toResponse(ProductCategory category) {
        return ProductCategoryResponse.builder()
                .id(category.getId())
                .categoryName(category.getCategoryName())
                .description(category.getDescription())
                .active(category.isActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .createdBy(category.getCreatedBy())
                .updatedBy(category.getUpdatedBy())
                .build();
    }

    private Map<String, Object> snapshot(ProductCategory category) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("categoryName", category.getCategoryName());
        data.put("description", category.getDescription());
        data.put("active", category.isActive());
        return data;
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Category name is required");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeSearch(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
    }
}
