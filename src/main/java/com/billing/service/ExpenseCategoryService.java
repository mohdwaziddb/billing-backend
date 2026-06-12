package com.billing.service;

import com.billing.entity.Company;
import com.billing.entity.ExpenseCategory;
import com.billing.repository.ExpenseCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseCategoryService {

    private static final List<String> DEFAULT_CATEGORIES = List.of(
            "Rent",
            "Utilities",
            "Transport",
            "Salary",
            "Marketing",
            "Maintenance"
    );

    private final ExpenseCategoryRepository expenseCategoryRepository;

    @Transactional
    public void seedDefaults(Company company) {
        for (String categoryName : DEFAULT_CATEGORIES) {
            expenseCategoryRepository.findByCompanyAndCategoryNameIgnoreCase(company, categoryName)
                    .orElseGet(() -> expenseCategoryRepository.save(ExpenseCategory.builder()
                            .company(company)
                            .categoryName(categoryName)
                            .active(true)
                            .build()));
        }
    }
}
