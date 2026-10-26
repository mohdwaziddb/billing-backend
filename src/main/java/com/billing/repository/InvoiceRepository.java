package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.Invoice;
import com.billing.entity.enums.InvoiceStatus;
import com.billing.entity.enums.RoleName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    @EntityGraph(attributePaths = {"customer", "customer.stateMaster", "referByUser", "items", "items.product", "items.taxMaster"})
    @Query("select i from Invoice i where (:company is null or 1=1) and i.deleted = false order by i.invoiceDate desc, i.id desc")
    List<Invoice> findByCompanyOrderByInvoiceDateDescIdDesc(@Param("company") Company company);
    @EntityGraph(attributePaths = {"customer", "customer.stateMaster", "referByUser", "items", "items.product", "items.taxMaster"})
    @Query("select i from Invoice i where i.deleted = false order by i.invoiceDate desc, i.id desc")
    List<Invoice> findAllByOrderByInvoiceDateDescIdDesc();
    @EntityGraph(attributePaths = {"customer", "customer.stateMaster", "referByUser", "items", "items.product", "items.taxMaster"})
    @Query("select i from Invoice i where (:company is null or 1=1) and i.deleted = false")
    Page<Invoice> findByCompany(@Param("company") Company company, Pageable pageable);
    @Query("select i from Invoice i where i.id = :id and (:company is null or 1=1)")
    Optional<Invoice> findByIdAndCompany(@Param("id") Long id, @Param("company") Company company);

    // DataPort duplicate guard: sirf 4 columns [customerId, invoiceDate, grandTotal, invoiceNo].
    // Pehle poora invoice graph (items + product + tax) har preview/revalidate par aata tha.
    @Query("select i.customer.id, i.invoiceDate, i.grandTotal, i.invoiceNo from Invoice i where i.deleted = false and i.customer is not null order by i.invoiceDate desc, i.id desc")
    List<Object[]> findActiveDuplicateGuardRows();

    // DataPort: file ke saare invoice numbers ek query me (pehle har row par alag query).
    // invoiceNos lowercase me bhejo.
    @Query("select i from Invoice i join fetch i.customer where lower(i.invoiceNo) in :invoiceNos and i.deleted = false")
    List<Invoice> findActiveByInvoiceNoLowerIn(@Param("invoiceNos") Collection<String> invoiceNos);

    // DataPort (payments/expenses): Excel me invoice number aata hai, id nahi.
    @Query("select i from Invoice i join fetch i.customer where lower(i.invoiceNo) = lower(:invoiceNo) and i.deleted = false")
    List<Invoice> findActiveByInvoiceNo(@Param("invoiceNo") String invoiceNo);
    @Query("select i from Invoice i where (:company is null or 1=1) order by i.id desc")
    Optional<Invoice> findTopByCompanyOrderByIdDesc(@Param("company") Company company);
    @Query("select i from Invoice i where (:company is null or 1=1) and i.customer = :customer and i.deleted = false order by i.invoiceDate desc, i.id desc")
    List<Invoice> findByCompanyAndCustomerOrderByInvoiceDateDescIdDesc(@Param("company") Company company, @Param("customer") Customer customer);
    @Query("select i from Invoice i where i.customer = :customer and i.deleted = false order by i.invoiceDate desc, i.id desc")
    List<Invoice> findByCustomerOrderByInvoiceDateDescIdDesc(@Param("customer") Customer customer);

    /** Outstanding list: saare baaki-wale invoices ek query me (pehle har customer ki alag query). */
    @Query("select i from Invoice i where i.deleted = false and i.balanceAmount > 0 and i.paymentStatus <> com.billing.entity.enums.InvoiceStatus.PAID")
    List<Invoice> findAllWithOutstandingBalance();

    /**
     * Customer list/form metrics: sirf diye gaye customers ka SQL aggregate.
     * Pehle har request par poori invoices table (items/products samet) memory me aati thi.
     * Row: [customerId, sum(totalAmount), sum(discountAmount), max(invoiceDate), count].
     */
    @Query("""
            select i.customer.id, sum(i.totalAmount), sum(i.discountAmount), max(i.invoiceDate), count(i.id)
            from Invoice i
            where i.deleted = false and i.customer.id in :customerIds
            group by i.customer.id
            """)
    List<Object[]> summarizeActiveByCustomerIds(@Param("customerIds") java.util.Collection<Long> customerIds);

    /** Outstanding reminders: har customer ki sabse purani baaki invoice ki date (entities load kiye bina). Row: [customerId, min(invoiceDate)]. */
    @Query("""
            select i.customer.id, min(i.invoiceDate)
            from Invoice i
            where i.deleted = false and i.balanceAmount > 0 and i.paymentStatus <> com.billing.entity.enums.InvoiceStatus.PAID
            group by i.customer.id
            """)
    List<Object[]> findOldestOutstandingDatePerCustomer();

    /** Outstanding reminders: sirf current page ke customers ke baaki invoices (sabse purani pehle). */
    @Query("""
            select i from Invoice i
            where i.deleted = false and i.balanceAmount > 0 and i.paymentStatus <> com.billing.entity.enums.InvoiceStatus.PAID
              and i.customer.id in :customerIds
            order by i.invoiceDate asc, i.id asc
            """)
    List<Invoice> findOutstandingByCustomerIds(@Param("customerIds") java.util.Collection<Long> customerIds);

    /**
     * Purchase history: pehle sirf ids ka page DB me (LIMIT/OFFSET). Items collection fetch ke saath
     * Pageable dene par Hibernate customer ke SAARE invoices laake memory me page karta tha.
     */
    @Query(value = "select i.id from Invoice i where i.customer = :customer and i.deleted = false",
            countQuery = "select count(i) from Invoice i where i.customer = :customer and i.deleted = false")
    Page<Long> findIdPageByCustomer(@Param("customer") Customer customer, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "customer.stateMaster", "referByUser", "items", "items.product", "items.taxMaster"})
    @Query("select distinct i from Invoice i where i.id in :invoiceIds")
    List<Invoice> findWithDetailsByIdIn(@Param("invoiceIds") java.util.Collection<Long> invoiceIds);
    @EntityGraph(attributePaths = {"customer", "customer.stateMaster", "referByUser", "items", "items.product", "items.taxMaster"})
    @Query("select i from Invoice i where (:company is null or 1=1) and i.customer = :customer and i.deleted = false")
    Page<Invoice> findByCompanyAndCustomer(@Param("company") Company company, @Param("customer") Customer customer, Pageable pageable);
    // Reports: date range DB me hi filter (pehle poori table memory me aati thi).
    // Items ke saath: jahan line-level data chahiye (GST, sold products).
    @EntityGraph(attributePaths = {"customer", "customer.stateMaster", "items", "items.product", "items.taxMaster"})
    @Query("""
            select i from Invoice i
            where i.deleted = false
              and (:startDate is null or i.invoiceDate >= :startDate)
              and (:endDate is null or i.invoiceDate <= :endDate)
            order by i.invoiceDate desc, i.id desc
            """)
    List<Invoice> findActiveInRangeWithItems(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Light: sirf header + customer (totals/balances/history ke liye, items nahi).
    @EntityGraph(attributePaths = {"customer"})
    @Query("""
            select i from Invoice i
            where i.deleted = false
              and (:startDate is null or i.invoiceDate >= :startDate)
              and (:endDate is null or i.invoiceDate <= :endDate)
            order by i.invoiceDate desc, i.id desc
            """)
    List<Invoice> findActiveInRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Dashboard/Analytics KPIs: [sum(totalAmount), count(invoices), count(distinct customers)] DB me hi.
    // Pehle poori invoices history entity bana kar Java me jodi jaati thi.
    @Query("""
            select coalesce(sum(i.totalAmount), 0), count(i), count(distinct i.customer.id)
            from Invoice i
            where i.deleted = false
              and (:startDate is null or i.invoiceDate >= :startDate)
              and (:endDate is null or i.invoiceDate <= :endDate)
            """)
    List<Object[]> summarizeActiveInRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Charts/trend buckets: din-wise [invoiceDate, sum(totalAmount)].
    @Query("""
            select i.invoiceDate, coalesce(sum(i.totalAmount), 0)
            from Invoice i
            where i.deleted = false
              and (:startDate is null or i.invoiceDate >= :startDate)
              and (:endDate is null or i.invoiceDate <= :endDate)
            group by i.invoiceDate
            """)
    List<Object[]> sumActiveTotalByDate(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // New/existing customer: har customer ki pehli purchase date (endDate tak) [customerId, min(invoiceDate)].
    @Query("""
            select i.customer.id, min(i.invoiceDate)
            from Invoice i
            where i.deleted = false
              and (:endDate is null or i.invoiceDate <= :endDate)
            group by i.customer.id
            """)
    List<Object[]> findFirstPurchaseDatesUpTo(@Param("endDate") LocalDate endDate);

    // Dashboard GST KPIs: [sum(taxAmount), sum(cgstTotal), sum(sgstTotal), sum(igstTotal)] (items load kiye bina).
    @Query("""
            select coalesce(sum(i.taxAmount), 0), coalesce(sum(i.cgstTotal), 0), coalesce(sum(i.sgstTotal), 0), coalesce(sum(i.igstTotal), 0)
            from Invoice i
            where i.deleted = false
              and (:startDate is null or i.invoiceDate >= :startDate)
              and (:endDate is null or i.invoiceDate <= :endDate)
            """)
    List<Object[]> sumActiveTaxTotalsInRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Dashboard outstanding card (aaj ka balance): sirf baaki-wale invoices endDate tak, customer ke saath.
    @EntityGraph(attributePaths = {"customer"})
    @Query("""
            select i from Invoice i
            where i.deleted = false
              and i.balanceAmount > 0
              and (:endDate is null or i.invoiceDate <= :endDate)
            order by i.invoiceDate desc, i.id desc
            """)
    List<Invoice> findActiveWithBalanceUpTo(@Param("endDate") LocalDate endDate);

    // Sales referral report: sirf refer-by wale invoices, DB me hi date filter (items nahi chahiye).
    @EntityGraph(attributePaths = {"customer", "referByUser"})
    @Query("""
            select i from Invoice i
            where i.deleted = false
              and i.referByUser is not null
              and (:startDate is null or i.invoiceDate >= :startDate)
              and (:endDate is null or i.invoiceDate <= :endDate)
            order by i.invoiceDate desc, i.id desc
            """)
    List<Invoice> findActiveReferredInRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Payment apply/reverse: paidAmount read-modify-write ke dauraan row lock (lost update fix).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> lockById(@Param("id") Long id);

    // FIFO allocation ke liye customer ke open invoices (purane pehle), locked.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i from Invoice i
            where i.customer = :customer and i.deleted = false and i.balanceAmount > 0
            order by i.invoiceDate asc, i.id asc
            """)
    List<Invoice> lockOpenInvoicesOldestFirst(@Param("customer") Customer customer);

    // Number-length pehle, taaki 99999 ke baad 100000 bhi "max" mile.
    @Query("select i.invoiceNo from Invoice i where i.invoiceNo like concat(:prefix, '%') order by length(i.invoiceNo) desc, i.invoiceNo desc")
    List<String> findLatestNumbersWithPrefix(@Param("prefix") String prefix, Pageable pageable);
    @Query("select count(i) from Invoice i where (:company is null or 1=1) and i.deleted = false")
    long countByCompanyAndDeletedFalse(@Param("company") Company company);

    @Query("select coalesce(sum(i.totalAmount), 0) from Invoice i where i.deleted = false")
    BigDecimal sumTotalAmount();

    @Query("""
            select coalesce(sum(i.totalAmount), 0), count(i)
            from Invoice i
            where i.deleted = false
            """)
    List<Object[]> revenueByCompany();

    /**
     * Invoice list + KPI summary ka common filter (dono me same where, taaki cards aur table
     * hamesha match karein). Pehle items/product/category par LEFT JOIN + DISTINCT tha: category
     * filter na ho tab bhi har item row join hoti, MySQL poora result temp table me dedupe +
     * filesort karta (invoice_date index kaam hi nahi aata). Ab category sirf EXISTS subquery me.
     */
    String INVOICE_SEARCH_FILTER = """
            where (:customerId is null or i.customer.id = :customerId)
              and (:deleted is null or i.deleted = :deleted)
              and (:search is null
                or lower(i.invoiceNo) like lower(concat('%', :search, '%'))
                or lower(i.customer.name) like lower(concat('%', :search, '%'))
                or i.customer.mobile like concat('%', :search, '%'))
              and (:paymentStatus is null or i.paymentStatus = :paymentStatus)
              and (:startDate is null or i.invoiceDate >= :startDate)
              and (:endDate is null or i.invoiceDate <= :endDate)
              and (:outstandingFilter is null
                or (:outstandingFilter = 'OUTSTANDING' and i.balanceAmount > 0)
                or (:outstandingFilter = 'FULLY_PAID' and i.balanceAmount = 0)
                or (:outstandingFilter = 'PARTIAL_OUTSTANDING' and i.balanceAmount > 0 and i.paidAmount > 0))
              and (:minAmount is null or i.totalAmount >= :minAmount)
              and (:maxAmount is null or i.totalAmount <= :maxAmount)
              and (:categoryId is null or exists (
                select 1 from InvoiceItem categoryItem
                where categoryItem.invoice = i and categoryItem.product.productCategory.id = :categoryId
              ))
              and (:createdByRole is null or exists (
                select 1 from User u
                where (str(u.id) = i.createdBy or lower(u.email) = lower(i.createdBy))
                  and u.role = :createdByRole
              ))
            """;

    /** Invoice list page: sirf ids (sorted + paginated). Details alag se findAllWithDetailsByIdIn se ek query me. */
    @Query(
            value = "select i.id from Invoice i " + INVOICE_SEARCH_FILTER,
            countQuery = "select count(i) from Invoice i " + INVOICE_SEARCH_FILTER
    )
    Page<Long> searchInvoiceIds(@Param("customerId") Long customerId,
                                @Param("deleted") Boolean deleted,
                                @Param("search") String search,
                                @Param("paymentStatus") InvoiceStatus paymentStatus,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate,
                                @Param("outstandingFilter") String outstandingFilter,
                                @Param("minAmount") BigDecimal minAmount,
                                @Param("maxAmount") BigDecimal maxAmount,
                                @Param("categoryId") Long categoryId,
                                @Param("createdByRole") RoleName createdByRole,
                                Pageable pageable);

    /**
     * Invoice list KPI cards + sparkline: (date, status) wise count aur outstanding SQL me hi.
     * Pehle frontend 50,000 tak poore invoice (items samet) laa kar browser me ginta tha.
     * Row: [invoiceDate, paymentStatus, invoiceCount, balanceAmountSum]
     */
    @Query("select i.invoiceDate, i.paymentStatus, count(i), coalesce(sum(i.balanceAmount), 0) from Invoice i "
            + INVOICE_SEARCH_FILTER
            + " group by i.invoiceDate, i.paymentStatus")
    List<Object[]> summarizeInvoices(@Param("customerId") Long customerId,
                                     @Param("deleted") Boolean deleted,
                                     @Param("search") String search,
                                     @Param("paymentStatus") InvoiceStatus paymentStatus,
                                     @Param("startDate") LocalDate startDate,
                                     @Param("endDate") LocalDate endDate,
                                     @Param("outstandingFilter") String outstandingFilter,
                                     @Param("minAmount") BigDecimal minAmount,
                                     @Param("maxAmount") BigDecimal maxAmount,
                                     @Param("categoryId") Long categoryId,
                                     @Param("createdByRole") RoleName createdByRole);

    /** Ek page ke invoices poore graph ke saath ek query me (pehle har invoice par items/product/tax ki alag query = N+1). */
    @EntityGraph(attributePaths = {"customer", "customer.stateMaster", "referByUser", "items", "items.product", "items.taxMaster"})
    @Query("select i from Invoice i where i.id in :invoiceIds")
    List<Invoice> findAllWithDetailsByIdIn(@Param("invoiceIds") Collection<Long> invoiceIds);

    /** Invoice detail / print / PDF: header + customer + items + product + tax ek query me. */
    @EntityGraph(attributePaths = {"customer", "customer.stateMaster", "referByUser", "items", "items.product", "items.taxMaster"})
    @Query("select i from Invoice i where i.id = :invoiceId")
    Optional<Invoice> findWithDetailsById(@Param("invoiceId") Long invoiceId);
}
