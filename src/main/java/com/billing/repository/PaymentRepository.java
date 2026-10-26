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
    @Query("select p from Payment p where (:company is null or 1=1) and p.deleted = false order by p.paymentDate desc, p.id desc")
    List<Payment> findByCompanyOrderByPaymentDateDescIdDesc(@Param("company") Company company);
    @EntityGraph(attributePaths = {"customer", "invoice"})
    @Query("select p from Payment p where p.deleted = false order by p.paymentDate desc, p.id desc")
    List<Payment> findAllByOrderByPaymentDateDescIdDesc();    // Reports: date range DB me filter.
    @EntityGraph(attributePaths = {"customer", "invoice"})
    @Query("""
            select p from Payment p
            where p.deleted = false
              and (:startDate is null or p.paymentDate >= :startDate)
              and (:endDate is null or p.paymentDate <= :endDate)
            order by p.paymentDate desc, p.id desc
            """)
    List<Payment> findActiveInRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Dashboard/Analytics KPIs: collection total DB me (pehle poori payments history memory me aati thi).
    @Query("""
            select coalesce(sum(p.amount), 0)
            from Payment p
            where p.deleted = false
              and (:startDate is null or p.paymentDate >= :startDate)
              and (:endDate is null or p.paymentDate <= :endDate)
            """)
    BigDecimal sumActiveAmountInRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Charts/trend buckets: din-wise [paymentDate, sum(amount)].
    @Query("""
            select p.paymentDate, coalesce(sum(p.amount), 0)
            from Payment p
            where p.deleted = false
              and (:startDate is null or p.paymentDate >= :startDate)
              and (:endDate is null or p.paymentDate <= :endDate)
            group by p.paymentDate
            """)
    List<Object[]> sumActiveAmountByDate(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Dashboard outstanding (purani date): invoice-wise paid amount endDate tak [invoiceId, sum(amount)].
    @Query("""
            select p.invoice.id, coalesce(sum(p.amount), 0)
            from Payment p
            where p.deleted = false
              and p.invoice is not null
              and (:endDate is null or p.paymentDate <= :endDate)
            group by p.invoice.id
            """)
    List<Object[]> sumActiveAmountByInvoiceUpTo(@Param("endDate") LocalDate endDate);

    @EntityGraph(attributePaths = {"customer", "invoice"})
    @Query("select p from Payment p where (:company is null or 1=1) and p.deleted = false")
    Page<Payment> findByCompany(@Param("company") Company company, Pageable pageable);
    @Query("select p from Payment p where p.id = :id and (:company is null or 1=1)")
    Optional<Payment> findByIdAndCompany(@Param("id") Long id, @Param("company") Company company);
    @Query("select p from Payment p where (:company is null or 1=1) and p.customer = :customer and p.deleted = false order by p.paymentDate desc, p.id desc")
    List<Payment> findByCompanyAndCustomerOrderByPaymentDateDescIdDesc(@Param("company") Company company, @Param("customer") Customer customer);
    @Query("select p from Payment p where p.customer = :customer and p.deleted = false order by p.paymentDate desc, p.id desc")
    List<Payment> findByCustomerOrderByPaymentDateDescIdDesc(@Param("customer") Customer customer);
    @Query("select p from Payment p where (:company is null or 1=1) and p.customer = :customer and p.amount > :amount and p.deleted = false order by p.paymentDate desc, p.id desc")
    List<Payment> findByCompanyAndCustomerAndAmountGreaterThanOrderByPaymentDateDescIdDesc(@Param("company") Company company, @Param("customer") Customer customer, @Param("amount") BigDecimal amount);
    @Query("select p from Payment p where p.customer = :customer and p.amount > :amount and p.deleted = false order by p.paymentDate desc, p.id desc")
    List<Payment> findByCustomerAndAmountGreaterThanOrderByPaymentDateDescIdDesc(@Param("customer") Customer customer, @Param("amount") BigDecimal amount);
    boolean existsByInvoiceAndDeletedFalse(Invoice invoice);

    /** Customer list metrics: diye gaye customers ka total paid SQL me (pehle poori payments table memory me). Row: [customerId, sum(amount)]. */
    @Query("""
            select p.customer.id, sum(p.amount)
            from Payment p
            where p.deleted = false and p.customer.id in :customerIds
            group by p.customer.id
            """)
    List<Object[]> sumActiveAmountByCustomerIds(@Param("customerIds") java.util.Collection<Long> customerIds);

    // List page: customer / invoice / gateway txn ek hi query me fetch (pehle har row par
    // 3 lazy select + toResponse me = 20 rows par ~60 extra queries, export ke 1000 rows par hazaaron).
    @Query(
            value = """
                    select p from Payment p
                    join fetch p.customer customer
                    left join fetch p.invoice invoice
                    left join fetch p.paymentTransaction paymentTransaction
                    where (:company is null or 1=1)
                      and (:deleted is null or p.deleted = :deleted)
                      and (:search is null
                        or str(p.id) like concat('%', :search, '%')
                        or lower(coalesce(invoice.invoiceNo, '')) like lower(concat('%', :search, '%'))
                        or lower(customer.name) like lower(concat('%', :search, '%'))
                        or customer.mobile like concat('%', :search, '%')
                        or lower(coalesce(p.referenceNo, '')) like lower(concat('%', :search, '%')))
                      and (:startDate is null or p.paymentDate >= :startDate)
                      and (:endDate is null or p.paymentDate <= :endDate)
                      and (:minAmount is null or p.amount >= :minAmount)
                      and (:maxAmount is null or p.amount <= :maxAmount)
                      and (:mode is null or p.mode = :mode)
                      and (:invoiceLinked is null
                        or (:invoiceLinked = true and p.invoice is not null)
                        or (:invoiceLinked = false and p.invoice is null))
                      and (:invoiceId is null or invoice.id = :invoiceId)
                      and (:createdByRole is null or exists (
                        select 1 from User u
                        where 1=1
                          and (str(u.id) = p.createdBy or lower(u.email) = lower(p.createdBy))
                          and u.role = :createdByRole
                      ))
                    """,
            countQuery = """
                    select count(p) from Payment p
                    left join p.invoice invoice
                    where (:company is null or 1=1)
                      and (:deleted is null or p.deleted = :deleted)
                      and (:search is null
                        or str(p.id) like concat('%', :search, '%')
                        or lower(coalesce(invoice.invoiceNo, '')) like lower(concat('%', :search, '%'))
                        or lower(p.customer.name) like lower(concat('%', :search, '%'))
                        or p.customer.mobile like concat('%', :search, '%')
                        or lower(coalesce(p.referenceNo, '')) like lower(concat('%', :search, '%')))
                      and (:startDate is null or p.paymentDate >= :startDate)
                      and (:endDate is null or p.paymentDate <= :endDate)
                      and (:minAmount is null or p.amount >= :minAmount)
                      and (:maxAmount is null or p.amount <= :maxAmount)
                      and (:mode is null or p.mode = :mode)
                      and (:invoiceLinked is null
                        or (:invoiceLinked = true and p.invoice is not null)
                        or (:invoiceLinked = false and p.invoice is null))
                      and (:invoiceId is null or invoice.id = :invoiceId)
                      and (:createdByRole is null or exists (
                        select 1 from User u
                        where 1=1
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
                                 @Param("invoiceId") Long invoiceId,
                                 Pageable pageable);

    // KPI cards (Total Payments / Collection / mode-wise): wahi filters, SQL me group by.
    // Pehle frontend saare pages (50k rows tak) laakar browser me jodta tha.
    @Query("""
            select p.mode, count(p), coalesce(sum(p.amount), 0)
            from Payment p
            left join p.invoice invoice
            where (:deleted is null or p.deleted = :deleted)
              and (:search is null
                or str(p.id) like concat('%', :search, '%')
                or lower(coalesce(invoice.invoiceNo, '')) like lower(concat('%', :search, '%'))
                or lower(p.customer.name) like lower(concat('%', :search, '%'))
                or p.customer.mobile like concat('%', :search, '%')
                or lower(coalesce(p.referenceNo, '')) like lower(concat('%', :search, '%')))
              and (:startDate is null or p.paymentDate >= :startDate)
              and (:endDate is null or p.paymentDate <= :endDate)
              and (:minAmount is null or p.amount >= :minAmount)
              and (:maxAmount is null or p.amount <= :maxAmount)
              and (:mode is null or p.mode = :mode)
              and (:invoiceLinked is null
                or (:invoiceLinked = true and p.invoice is not null)
                or (:invoiceLinked = false and p.invoice is null))
              and (:invoiceId is null or invoice.id = :invoiceId)
              and (:createdByRole is null or exists (
                select 1 from User u
                where 1=1
                  and (str(u.id) = p.createdBy or lower(u.email) = lower(p.createdBy))
                  and u.role = :createdByRole
              ))
            group by p.mode
            """)
    List<Object[]> summarizePaymentsByMode(@Param("deleted") Boolean deleted,
                                           @Param("search") String search,
                                           @Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate,
                                           @Param("minAmount") BigDecimal minAmount,
                                           @Param("maxAmount") BigDecimal maxAmount,
                                           @Param("mode") String mode,
                                           @Param("invoiceLinked") Boolean invoiceLinked,
                                           @Param("createdByRole") RoleName createdByRole,
                                           @Param("invoiceId") Long invoiceId);
}
