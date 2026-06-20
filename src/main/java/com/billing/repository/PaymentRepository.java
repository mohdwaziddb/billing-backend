package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.Invoice;
import com.billing.entity.Payment;
import com.billing.entity.enums.RoleName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDate;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @EntityGraph(attributePaths = {"customer", "invoice"})
    @Query("select p from Payment p where p.company = :company and p.deleted = false order by p.paymentDate desc, p.id desc")
    List<Payment> findByCompanyOrderByPaymentDateDescIdDesc(@Param("company") Company company);
    @EntityGraph(attributePaths = {"customer", "invoice"})
    @Query("select p from Payment p where p.deleted = false order by p.paymentDate desc, p.id desc")
    List<Payment> findAllByOrderByPaymentDateDescIdDesc();
    @EntityGraph(attributePaths = {"customer", "invoice"})
    @Query("select p from Payment p where p.company = :company and p.deleted = false")
    Page<Payment> findByCompany(@Param("company") Company company, Pageable pageable);
    Optional<Payment> findByIdAndCompany(Long id, Company company);
    @Query("select p from Payment p where p.company = :company and p.customer = :customer and p.deleted = false order by p.paymentDate desc, p.id desc")
    List<Payment> findByCompanyAndCustomerOrderByPaymentDateDescIdDesc(@Param("company") Company company, @Param("customer") Customer customer);
    @Query("select p from Payment p where p.company = :company and p.customer = :customer and p.amount > :amount and p.deleted = false order by p.paymentDate desc, p.id desc")
    List<Payment> findByCompanyAndCustomerAndAmountGreaterThanOrderByPaymentDateDescIdDesc(@Param("company") Company company, @Param("customer") Customer customer, @Param("amount") BigDecimal amount);
    boolean existsByInvoiceAndDeletedFalse(Invoice invoice);

    @Query(
            value = """
                    select p from Payment p
                    left join p.invoice invoice
                    where (:company is null or p.company = :company)
                      and (:deleted is null or p.deleted = :deleted)
                      and (:search is null
                        or str(p.id) like concat('%', :search, '%')
                        or lower(coalesce(invoice.invoiceNo, '')) like lower(concat('%', :search, '%'))
                        or lower(p.customer.name) like lower(concat('%', :search, '%'))
                        or p.customer.mobile like concat('%', :search, '%'))
                      and (:startDate is null or p.paymentDate >= :startDate)
                      and (:endDate is null or p.paymentDate <= :endDate)
                      and (:minAmount is null or p.amount >= :minAmount)
                      and (:maxAmount is null or p.amount <= :maxAmount)
                      and (:mode is null or p.mode = :mode)
                      and (:invoiceLinked is null
                        or (:invoiceLinked = true and p.invoice is not null)
                        or (:invoiceLinked = false and p.invoice is null))
                      and (:createdByRole is null or exists (
                        select 1 from User u
                        where u.company = p.company
                          and (str(u.id) = p.createdBy or lower(u.email) = lower(p.createdBy))
                          and u.role = :createdByRole
                      ))
                    """,
            countQuery = """
                    select count(p) from Payment p
                    left join p.invoice invoice
                    where (:company is null or p.company = :company)
                      and (:deleted is null or p.deleted = :deleted)
                      and (:search is null
                        or str(p.id) like concat('%', :search, '%')
                        or lower(coalesce(invoice.invoiceNo, '')) like lower(concat('%', :search, '%'))
                        or lower(p.customer.name) like lower(concat('%', :search, '%'))
                        or p.customer.mobile like concat('%', :search, '%'))
                      and (:startDate is null or p.paymentDate >= :startDate)
                      and (:endDate is null or p.paymentDate <= :endDate)
                      and (:minAmount is null or p.amount >= :minAmount)
                      and (:maxAmount is null or p.amount <= :maxAmount)
                      and (:mode is null or p.mode = :mode)
                      and (:invoiceLinked is null
                        or (:invoiceLinked = true and p.invoice is not null)
                        or (:invoiceLinked = false and p.invoice is null))
                      and (:createdByRole is null or exists (
                        select 1 from User u
                        where u.company = p.company
                          and (str(u.id) = p.createdBy or lower(u.email) = lower(p.createdBy))
                          and u.role = :createdByRole
                      ))
                    """
    )
    Page<Payment> searchPayments(@Param("company") Company company,
                                 @Param("deleted") Boolean deleted,
                                 @Param("search") String search,
                                 @Param("startDate") LocalDate startDate,
                                 @Param("endDate") LocalDate endDate,
                                 @Param("minAmount") BigDecimal minAmount,
                                 @Param("maxAmount") BigDecimal maxAmount,
                                 @Param("mode") String mode,
                                 @Param("invoiceLinked") Boolean invoiceLinked,
                                 @Param("createdByRole") RoleName createdByRole,
                                 Pageable pageable);
}
