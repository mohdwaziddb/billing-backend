package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    List<ExpenseCategory> findByCompanyOrderByCategoryNameAsc(Company company);

    Optional<ExpenseCategory> findByCompanyAndCategoryNameIgnoreCase(Company company, String categoryName);
}
