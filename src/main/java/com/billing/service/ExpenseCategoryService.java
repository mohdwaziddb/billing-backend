package com.billing.service;

import com.billing.dto.PageResponse;
import com.billing.dto.expense.ExpenseCategoryRequest;
import com.billing.dto.expense.ExpenseCategoryResponse;
import com.billing.entity.Company;
import com.billing.entity.ExpenseCategory;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.ExpenseCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExpenseCategoryService {

    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;
    private final AuditNameResolver auditNameResolver;

    @Transactional(readOnly = true)
    public PageResponse<ExpenseCategoryResponse> page(String email, String search, Boolean active, int page, int size) {
        Company company = accessControlService.getCurrentCompany(email);
        return PageResponse.from(expenseCategoryRepository.findPageByCompanyWithFilters(company, active, normalizeSearch(search), pageRequest(page, size))
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public ExpenseCategoryResponse get(String email, Long categoryId) {
        Company company = accessControlService.getCurrentCompany(email);
        return toResponse(getCategoryOrThrow(company, categoryId));
    }

    @Transactional
    public ExpenseCategoryResponse create(String email, ExpenseCategoryRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        String categoryName = normalizeName(request.getCategoryName());
        if (expenseCategoryRepository.existsByCompanyAndCategoryNameIgnoreCase(company, categoryName)) {
            throw new BadRequestException("Expense category already exists");
        }
        ExpenseCategory category = ExpenseCategory.builder()
                .company(company)
                .categoryName(categoryName)
                .description(blankToNull(request.getDescription()))
                .active(request.getActive() == null || Boolean.TRUE.equals(request.getActive()))
                .build();
        ExpenseCategory saved = expenseCategoryRepository.save(category);
        auditLogService.logCreate(email, company, "Expense Category", "ExpenseCategory", saved.getId(), snapshot(saved));
        return toResponse(saved);
    }

    @Transactional
    public ExpenseCategoryResponse update(String email, Long categoryId, ExpenseCategoryRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        ExpenseCategory category = getCategoryOrThrow(company, categoryId);
        Map<String, Object> oldData = snapshot(category);
        String categoryName = normalizeName(request.getCategoryName());
        if (expenseCategoryRepository.existsByCompanyAndCategoryNameIgnoreCaseAndIdNot(company, categoryName, categoryId)) {
            throw new BadRequestException("Expense category already exists");
        }
        category.setCategoryName(categoryName);
        category.setDescription(blankToNull(request.getDescription()));
        category.setActive(request.getActive() == null || Boolean.TRUE.equals(request.getActive()));
        ExpenseCategory saved = expenseCategoryRepository.save(category);
        auditLogService.logUpdate(email, company, "Expense Category", "ExpenseCategory", saved.getId(), oldData, snapshot(saved));
        return toResponse(saved);
    }

    @Transactional
    public void delete(String email, Long categoryId) {
        Company company = accessControlService.getCurrentCompany(email);
        ExpenseCategory category = getCategoryOrThrow(company, categoryId);
        Map<String, Object> oldData = snapshot(category);
        category.setActive(false);
        ExpenseCategory saved = expenseCategoryRepository.save(category);
        auditLogService.logDelete(email, company, "Expense Category", "ExpenseCategory", saved.getId(), oldData);
    }

    public ExpenseCategory getActiveByIdOrThrow(Company company, Long categoryId) {
        if (categoryId == null) {
            throw new BadRequestException("Expense category is required");
        }
        ExpenseCategory category = getCategoryOrThrow(company, categoryId);
        if (!category.isActive()) {
            throw new BadRequestException("Select an active expense category");
        }
        return category;
    }

    private ExpenseCategory getCategoryOrThrow(Company company, Long categoryId) {
        return expenseCategoryRepository.findByIdAndCompany(categoryId, company)
                .orElseThrow(() -> new ResourceNotFoundException("Expense category not found"));
    }

    private ExpenseCategoryResponse toResponse(ExpenseCategory category) {
        return ExpenseCategoryResponse.builder()
                .id(category.getId())
                .categoryName(category.getCategoryName())
                .description(category.getDescription())
                .active(category.isActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .createdBy(auditNameResolver.displayName(category.getCreatedBy()))
                .updatedBy(auditNameResolver.displayName(category.getUpdatedBy()))
                .build();
    }

    private Map<String, Object> snapshot(ExpenseCategory category) {
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
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 1000)));
    }
}
