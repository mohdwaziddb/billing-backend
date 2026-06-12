package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByCompanyOrderByExpenseDateDescIdDesc(Company company);
}
