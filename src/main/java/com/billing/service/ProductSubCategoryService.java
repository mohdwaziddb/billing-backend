package com.billing.service;

import com.billing.dto.PageResponse;
import com.billing.dto.productsubcategory.ProductSubCategoryRequest;
import com.billing.dto.productsubcategory.ProductSubCategoryResponse;
import com.billing.entity.Company;
import com.billing.entity.ProductCategory;
import com.billing.entity.ProductSubCategory;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.ProductSubCategoryRepository;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductSubCategoryService {

    private final ProductSubCategoryRepository productSubCategoryRepository;
    private final ProductCategoryService productCategoryService;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;
    private final AuditNameResolver auditNameResolver;
    private final ProductSubCategoryMapper productSubCategoryMapper;

    @Transactional(readOnly = true)
    public List<ProductSubCategoryResponse> list(String email, Long categoryId, String search, Boolean active) {
        Company company = accessControlService.getCurrentCompany(email);
        return productSubCategoryRepository.findAllByCompanyWithFilters(company, categoryId, active, normalizeSearch(search)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSubCategoryResponse> page(String email, Long categoryId, String search, Boolean active, int page, int size) {
        Company company = accessControlService.getCurrentCompany(email);
        return PageResponse.from(productSubCategoryRepository.findPageByCompanyWithFilters(company, categoryId, active, normalizeSearch(search), pageRequest(page, size))
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSubCategoryResponse> page(Map<String, Object> param, String email) {
        Long categoryId = DataTypeUtility.getForeignKeyValue(param.get("categoryId"));
        if (categoryId == null) {
            categoryId = DataTypeUtility.getForeignKeyValue(param.get("category_id"));
        }
        String search = DataTypeUtility.stringValue(param.get("search"));
        if (search.length() == 0) {
            search = null;
        }
        Boolean active = null;
        Object activeRaw = param.get("active");
        if (activeRaw != null) {
            String activeStr = DataTypeUtility.stringValue(activeRaw);
            if (activeStr.length() > 0) {
                active = DataTypeUtility.booleanValue(activeRaw);
            }
        }
        int page = DataTypeUtility.integerValue(param.get("page"));
        int size = DataTypeUtility.integerValue(param.get("size"));
        if (size == 0) {
            size = 20;
        }
        return page(email, categoryId, search, active, page, size);
    }

    @Transactional(readOnly = true)
    public ProductSubCategoryResponse get(String email, Long subCategoryId) {
        Company company = accessControlService.getCurrentCompany(email);
        return toResponse(getSubCategoryOrThrow(company, subCategoryId));
    }

    @Transactional
    public ProductSubCategoryResponse create(String email, ProductSubCategoryRequest request) {
        Company company = accessControlService.requireOwnerCompany(email);
        ProductCategory category = productCategoryService.getCategoryOrThrow(company, request.getCategoryId());
        String subCategoryName = normalizeName(request.getSubCategoryName());
        if (productSubCategoryRepository.existsByCompanyAndProductCategoryAndSubCategoryNameIgnoreCase(company, category, subCategoryName)) {
            throw new BadRequestException("Product sub category already exists in this category");
        }

        ProductSubCategory subCategory = ProductSubCategory.builder()
                .company(company)
                .productCategory(category)
                .subCategoryName(subCategoryName)
                .description(blankToNull(request.getDescription()))
                .active(Boolean.TRUE.equals(request.getActive()))
                .build();
        ProductSubCategory saved = productSubCategoryRepository.save(subCategory);
        auditLogService.logCreate(email, company, "Product Sub Category", "ProductSubCategory", saved.getId(), productSubCategoryMapper.snapshot(saved));
        return toResponse(saved);
    }

    @Transactional
    public ProductSubCategoryResponse update(String email, Long subCategoryId, ProductSubCategoryRequest request) {
        Company company = accessControlService.requireOwnerCompany(email);
        ProductSubCategory subCategory = getSubCategoryOrThrow(company, subCategoryId);
        ProductCategory category = productCategoryService.getCategoryOrThrow(company, request.getCategoryId());
        String subCategoryName = normalizeName(request.getSubCategoryName());
        if (productSubCategoryRepository.existsByCompanyAndProductCategoryAndSubCategoryNameIgnoreCaseAndIdNot(company, category, subCategoryName, subCategoryId)) {
            throw new BadRequestException("Product sub category already exists in this category");
        }

        Map<String, Object> oldData = productSubCategoryMapper.snapshot(subCategory);
        subCategory.setProductCategory(category);
        subCategory.setSubCategoryName(subCategoryName);
        subCategory.setDescription(blankToNull(request.getDescription()));
        subCategory.setActive(Boolean.TRUE.equals(request.getActive()));
        ProductSubCategory saved = productSubCategoryRepository.save(subCategory);
        auditLogService.logUpdate(email, company, "Product Sub Category", "ProductSubCategory", saved.getId(), oldData, productSubCategoryMapper.snapshot(saved));
        return toResponse(saved);
    }

    @Transactional
    public void delete(String email, Long subCategoryId) {
        Company company = accessControlService.requireOwnerCompany(email);
        ProductSubCategory subCategory = getSubCategoryOrThrow(company, subCategoryId);
        Map<String, Object> oldData = productSubCategoryMapper.snapshot(subCategory);
        subCategory.setActive(false);
        ProductSubCategory saved = productSubCategoryRepository.save(subCategory);
        auditLogService.logDelete(email, company, "Product Sub Category", "ProductSubCategory", saved.getId(), oldData);
    }

    @Transactional
    public ProductSubCategoryResponse create(Map<String, Object> param, String email) {
        ProductSubCategoryRequest productSubCategoryRequest = mapToProductSubCategoryRequest(param);
        return create(email, productSubCategoryRequest);
    }

    @Transactional
    public ProductSubCategoryResponse update(Map<String, Object> param, Long subCategoryId, String email) {
        ProductSubCategoryRequest productSubCategoryRequest = mapToProductSubCategoryRequest(param);
        return update(email, subCategoryId, productSubCategoryRequest);
    }

    @Transactional(readOnly = true)
    public List<ProductSubCategoryResponse> activeByCategory(String email, Long categoryId) {
        Company company = accessControlService.getCurrentCompany(email);
        ProductCategory category = productCategoryService.getCategoryOrThrow(company, categoryId);
        return productSubCategoryRepository.findByCompanyAndProductCategoryAndActiveTrueOrderBySubCategoryNameAsc(company, category).stream()
                .map(this::toResponse)
                .toList();
    }

    public ProductSubCategory getActiveByIdOrThrow(Company company, Long categoryId, Long subCategoryId) {
        if (subCategoryId == null) {
            throw new BadRequestException("Product sub category is required");
        }
        ProductSubCategory subCategory = getSubCategoryOrThrow(company, subCategoryId);
        if (!subCategory.isActive()) {
            throw new BadRequestException("Select an active product sub category");
        }
        if (categoryId != null && (subCategory.getProductCategory() == null || !categoryId.equals(subCategory.getProductCategory().getId()))) {
            throw new BadRequestException("Select a product sub category from the selected category");
        }
        return subCategory;
    }

    public ProductSubCategory getActiveByCategoryAndNameOrThrow(Company company, ProductCategory category, String subCategoryName) {
        return productSubCategoryRepository.findByCompanyAndProductCategoryAndSubCategoryNameIgnoreCaseAndActiveTrue(company, category, normalizeName(subCategoryName))
                .orElseThrow(() -> new BadRequestException("Select an active product sub category"));
    }

    ProductSubCategory getSubCategoryOrThrow(Company company, Long subCategoryId) {
        return productSubCategoryRepository.findByIdAndCompany(subCategoryId, company)
                .orElseThrow(() -> new ResourceNotFoundException("Product sub category not found"));
    }

    private ProductSubCategoryResponse toResponse(ProductSubCategory subCategory) {
        return productSubCategoryMapper.toResponse(subCategory, auditNameResolver);
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Sub category name is required");
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

    private ProductSubCategoryRequest mapToProductSubCategoryRequest(Map<String, Object> param) {
        ProductSubCategoryRequest request = new ProductSubCategoryRequest();
        Long categoryId = DataTypeUtility.getForeignKeyValue(param.get("categoryId"));
        if (categoryId == null) {
            categoryId = DataTypeUtility.getForeignKeyValue(param.get("category_id"));
        }
        request.setCategoryId(categoryId);
        String subCategoryName = DataTypeUtility.stringValue(param.get("subCategoryName"));
        if (subCategoryName.length() == 0) {
            subCategoryName = DataTypeUtility.stringValue(param.get("sub_category_name"));
        }
        if (subCategoryName.length() == 0) {
            subCategoryName = DataTypeUtility.stringValue(param.get("name"));
        }
        if (subCategoryName.length() == 0) {
            subCategoryName = null;
        }
        request.setSubCategoryName(subCategoryName);
        String description = DataTypeUtility.stringValue(param.get("description"));
        if (description.length() == 0) {
            description = null;
        }
        request.setDescription(description);
        Object activeRaw = param.get("active");
        if (activeRaw == null) {
            activeRaw = param.get("isActive");
        }
        Boolean active = null;
        if (activeRaw != null) {
            String activeStr = DataTypeUtility.stringValue(activeRaw);
            if (activeStr.length() > 0) {
                active = DataTypeUtility.booleanValue(activeRaw);
            }
        }
        request.setActive(active);
        return request;
    }
}
