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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Expense soft delete hota hai (is_deleted): har query me "e.deleted = false" — warna
// deleted kharcha Dashboard / P&L / Analytics / Payment Hierarchy me gina jata.
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @EntityGraph(attributePaths = {"category", "customer", "invoice"})
    @Query("select e from Expense e where (:company is null or 1=1) and e.deleted = false order by e.expenseDate desc, e.id desc")
    List<Expense> findByCompanyOrderByExpenseDateDescIdDesc(@Param("company") Company company);

    // Reports: date range DB me filter.
    @EntityGraph(attributePaths = {"category", "customer", "invoice"})
    @Query("""
            select e from Expense e
            where e.deleted = false
              and (:startDate is null or e.expenseDate >= :startDate)
              and (:endDate is null or e.expenseDate <= :endDate)
            order by e.expenseDate desc, e.id desc
            """)
    List<Expense> findInRange(@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    // Payment hierarchy: invoice-linked expense ka customer bhi saath (warna har invoice par alag lazy select).
    @EntityGraph(attributePaths = {"category", "customer", "invoice", "invoice.customer"})
    @Query("""
            select e from Expense e
            where e.deleted = false
              and (:startDate is null or e.expenseDate >= :startDate)
              and (:endDate is null or e.expenseDate <= :endDate)
            order by e.expenseDate desc, e.id desc
            """)
    List<Expense> findInRangeWithInvoiceCustomer(@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    // Dashboard KPI: range ka kul kharcha DB me (entity + category/customer/invoice graph load kiye bina).
    @Query("""
            select coalesce(sum(e.amount), 0)
            from Expense e
            where e.deleted = false
              and (:startDate is null or e.expenseDate >= :startDate)
              and (:endDate is null or e.expenseDate <= :endDate)
            """)
    BigDecimal sumActiveAmountInRange(@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    // Dashboard net revenue card: din-wise [expenseDate, sum(amount)].
    @Query("""
            select e.expenseDate, coalesce(sum(e.amount), 0)
            from Expense e
            where e.deleted = false
              and (:startDate is null or e.expenseDate >= :startDate)
              and (:endDate is null or e.expenseDate <= :endDate)
            group by e.expenseDate
            """)
    List<Object[]> sumActiveAmountByDate(@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    @EntityGraph(attributePaths = {"category", "customer", "invoice"})
    @Query("select e from Expense e where e.deleted = false order by e.expenseDate desc, e.id desc")
    List<Expense> findAllByOrderByExpenseDateDescIdDesc();

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.deleted = false")
    BigDecimal sumTotalAmount();

    /**
     * Expenses page ke KPI cards: SQL aggregate (pehle UI 1000 rows mangwa ke JS me jodta tha —
     * 1000 se zyada kharche par total galat + bhaari payload). Null param = filter nahi.
     */
    @Query("""
            select coalesce(sum(e.amount), 0) from Expense e
            where e.deleted = false
              and (:startDate is null or e.expenseDate >= :startDate)
              and (:endDate is null or e.expenseDate <= :endDate)
              and (:categoryId is null or e.category.id = :categoryId)
            """)
    BigDecimal sumActiveAmount(@Param("startDate") LocalDate startDate,
                               @Param("endDate") LocalDate endDate,
                               @Param("categoryId") Long categoryId);

    @Query("""
            select count(e) from Expense e
            where e.deleted = false
              and (:categoryId is null or e.category.id = :categoryId)
            """)
    long countActiveByCategory(@Param("categoryId") Long categoryId);

    /** Customer profitability: customer ka kharcha seedha SQL sum (pehle range ke saare expenses Java me filter hote the). */
    @Query("""
            select coalesce(sum(e.amount), 0) from Expense e
            where e.deleted = false
              and e.customer.id = :customerId
              and (:startDate is null or e.expenseDate >= :startDate)
              and (:endDate is null or e.expenseDate <= :endDate)
            """)
    BigDecimal sumActiveAmountByCustomer(@Param("customerId") Long customerId,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    /**
     * deleted: false = sirf active (default), true = sirf deleted, null = sab.
     * EntityGraph: list me har row ka category/customer/invoice naam chahiye — bina iske
     * har row par 3 alag SELECT (N+1) chalte the.
     */
    @EntityGraph(attributePaths = {"category", "customer", "invoice"})
    @Query(
            value = """
                    select e from Expense e
                    left join e.category category
                    left join e.customer customer
                    left join e.invoice invoice
                    where (:company is null or 1=1)
                      and (:deleted is null or e.deleted = :deleted)
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
                        where 1=1
                          and (str(u.id) = e.createdBy or lower(u.email) = lower(e.createdBy))
                          and u.role = :createdByRole
                      ))
                    """,
            countQuery = """
                    select count(e) from Expense e
                    left join e.category category
                    left join e.customer customer
                    left join e.invoice invoice
                    where (:company is null or 1=1)
                      and (:deleted is null or e.deleted = :deleted)
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
                        where 1=1
                          and (str(u.id) = e.createdBy or lower(u.email) = lower(e.createdBy))
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
                                 @Param("deleted") Boolean deleted,
                                 Pageable pageable);
}
