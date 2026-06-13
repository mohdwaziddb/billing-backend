package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.Expense;
import com.billing.entity.enums.ExpenseType;
import com.billing.entity.enums.RoleName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @EntityGraph(attributePaths = {"category", "customer", "invoice"})
    List<Expense> findByCompanyOrderByExpenseDateDescIdDesc(Company company);

    Optional<Expense> findByIdAndCompany(Long id, Company company);

    @Query(
            value = """
                    select e from Expense e
                    left join e.category category
                    left join e.customer customer
                    left join e.invoice invoice
                    where e.company = :company
                      and (:search is null
                        or lower(coalesce(e.description, '')) like lower(concat('%', :search, '%'))
                        or lower(category.categoryName) like lower(concat('%', :search, '%'))
                        or lower(coalesce(customer.name, '')) like lower(concat('%', :search, '%'))
                        or lower(coalesce(invoice.invoiceNo, '')) like lower(concat('%', :search, '%')))
                      and (:expenseType is null or e.expenseType = :expenseType)
                      and (:categoryId is null or category.id = :categoryId)
                      and (:customerId is null or customer.id = :customerId)
                      and (:invoiceId is null or invoice.id = :invoiceId)
                      and (:startDate is null or e.expenseDate >= :startDate)
                      and (:endDate is null or e.expenseDate <= :endDate)
                      and (:createdByRole is null or exists (
                        select 1 from User u
                        where u.company = e.company
                          and lower(u.email) = lower(e.createdBy)
                          and u.role = :createdByRole
                      ))
                    """,
            countQuery = """
                    select count(e) from Expense e
                    left join e.category category
                    left join e.customer customer
                    left join e.invoice invoice
                    where e.company = :company
                      and (:search is null
                        or lower(coalesce(e.description, '')) like lower(concat('%', :search, '%'))
                        or lower(category.categoryName) like lower(concat('%', :search, '%'))
                        or lower(coalesce(customer.name, '')) like lower(concat('%', :search, '%'))
                        or lower(coalesce(invoice.invoiceNo, '')) like lower(concat('%', :search, '%')))
                      and (:expenseType is null or e.expenseType = :expenseType)
                      and (:categoryId is null or category.id = :categoryId)
                      and (:customerId is null or customer.id = :customerId)
                      and (:invoiceId is null or invoice.id = :invoiceId)
                      and (:startDate is null or e.expenseDate >= :startDate)
                      and (:endDate is null or e.expenseDate <= :endDate)
                      and (:createdByRole is null or exists (
                        select 1 from User u
                        where u.company = e.company
                          and lower(u.email) = lower(e.createdBy)
                          and u.role = :createdByRole
                      ))
                    """
    )
    Page<Expense> searchExpenses(@Param("company") Company company,
                                 @Param("search") String search,
                                 @Param("expenseType") ExpenseType expenseType,
                                 @Param("categoryId") Long categoryId,
                                 @Param("customerId") Long customerId,
                                 @Param("invoiceId") Long invoiceId,
                                 @Param("startDate") LocalDate startDate,
                                 @Param("endDate") LocalDate endDate,
                                 @Param("createdByRole") RoleName createdByRole,
                                 Pageable pageable);
}
