package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.Invoice;
import com.billing.entity.enums.InvoiceStatus;
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
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    List<Invoice> findByCompanyOrderByInvoiceDateDescIdDesc(Company company);
    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    List<Invoice> findAllByOrderByInvoiceDateDescIdDesc();
    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    Page<Invoice> findByCompany(Company company, Pageable pageable);
    Optional<Invoice> findByIdAndCompany(Long id, Company company);
    Optional<Invoice> findTopByCompanyOrderByIdDesc(Company company);
    List<Invoice> findByCompanyAndCustomerOrderByInvoiceDateDescIdDesc(Company company, Customer customer);
    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    Page<Invoice> findByCompanyAndCustomer(Company company, Customer customer, Pageable pageable);
    long countByCompanyAndInvoiceDate(Company company, LocalDate invoiceDate);
    long countByCompany(Company company);

    @Query("select coalesce(sum(i.totalAmount), 0) from Invoice i")
    BigDecimal sumTotalAmount();

    @Query("""
            select i.company.id, i.company.name, coalesce(sum(i.totalAmount), 0), count(i)
            from Invoice i
            group by i.company.id, i.company.name
            order by coalesce(sum(i.totalAmount), 0) desc
            """)
    List<Object[]> revenueByCompany();

    @Query(
            value = """
                    select distinct i from Invoice i
                    left join i.items item
                    left join item.product product
                    left join product.productCategory category
                    where (:company is null or i.company = :company)
                      and (:customer is null or i.customer = :customer)
                      and (:customerId is null or i.customer.id = :customerId)
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
                      and (:categoryId is null or category.id = :categoryId)
                      and (:createdByRole is null or exists (
                        select 1 from User u
                        where u.company = i.company
                          and (str(u.id) = i.createdBy or lower(u.email) = lower(i.createdBy))
                          and u.role = :createdByRole
                      ))
                    """,
            countQuery = """
                    select count(distinct i) from Invoice i
                    left join i.items item
                    left join item.product product
                    left join product.productCategory category
                    where (:company is null or i.company = :company)
                      and (:customer is null or i.customer = :customer)
                      and (:customerId is null or i.customer.id = :customerId)
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
                      and (:categoryId is null or category.id = :categoryId)
                      and (:createdByRole is null or exists (
                        select 1 from User u
                        where u.company = i.company
                          and (str(u.id) = i.createdBy or lower(u.email) = lower(i.createdBy))
                          and u.role = :createdByRole
                      ))
                    """
    )
    Page<Invoice> searchInvoices(@Param("company") Company company,
                                 @Param("customer") Customer customer,
                                 @Param("customerId") Long customerId,
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
}
