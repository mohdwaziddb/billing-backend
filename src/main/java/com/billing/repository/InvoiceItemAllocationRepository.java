package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.Invoice;
import com.billing.entity.InvoiceItem;
import com.billing.entity.InvoiceItemAllocation;
import com.billing.entity.Product;
import com.billing.entity.ProductBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface InvoiceItemAllocationRepository extends JpaRepository<InvoiceItemAllocation, Long> {

    @Query("select a from InvoiceItemAllocation a where (:company is null or 1=1) and a.invoice = :invoice and a.active = true order by a.id asc")
    List<InvoiceItemAllocation> findByCompanyAndInvoiceAndActiveTrueOrderByIdAsc(@Param("company") Company company, @Param("invoice") Invoice invoice);

    @Query("select a from InvoiceItemAllocation a where (:company is null or 1=1) and a.invoiceItem = :invoiceItem and a.active = true order by a.id asc")
    List<InvoiceItemAllocation> findByCompanyAndInvoiceItemAndActiveTrueOrderByIdAsc(@Param("company") Company company, @Param("invoiceItem") InvoiceItem invoiceItem);

    @Query("""
            select coalesce(sum(a.costAmount), 0)
            from InvoiceItemAllocation a
            where (:company is null or 1=1) and a.invoice.id = :invoiceId
              and a.active = true
            """)
    BigDecimal sumActiveCostByInvoice(@Param("company") Company company, @Param("invoiceId") Long invoiceId);

    @Query("""
            select coalesce(sum(a.costAmount), 0)
            from InvoiceItemAllocation a
            where (:company is null or 1=1) and (:startDate is null or a.invoice.invoiceDate >= :startDate)
              and (:endDate is null or a.invoice.invoiceDate <= :endDate)
              and a.active = true
            """)
    BigDecimal sumActiveCostByInvoiceDateRange(@Param("company") Company company,
                                               @Param("startDate") java.time.LocalDate startDate,
                                               @Param("endDate") java.time.LocalDate endDate);

    @Query("""
            select coalesce(sum(a.costAmount), 0)
            from InvoiceItemAllocation a
            where (:company is null or 1=1) and a.invoice.customer.id = :customerId
              and (:startDate is null or a.invoice.invoiceDate >= :startDate)
              and (:endDate is null or a.invoice.invoiceDate <= :endDate)
              and a.active = true
            """)
    BigDecimal sumActiveCostByCustomer(@Param("company") Company company,
                                       @Param("customerId") Long customerId,
                                       @Param("startDate") java.time.LocalDate startDate,
                                       @Param("endDate") java.time.LocalDate endDate);

    // P&L COGS: revenue jaise hi customer/invoice filter (pehle sirf date filter tha).
    @Query("""
            select coalesce(sum(a.costAmount), 0)
            from InvoiceItemAllocation a
            where (:startDate is null or a.invoice.invoiceDate >= :startDate)
              and (:endDate is null or a.invoice.invoiceDate <= :endDate)
              and (:customerId is null or a.invoice.customer.id = :customerId)
              and (:invoiceId is null or a.invoice.id = :invoiceId)
              and a.active = true
            """)
    BigDecimal sumActiveCostFiltered(@Param("customerId") Long customerId,
                                     @Param("invoiceId") Long invoiceId,
                                     @Param("startDate") java.time.LocalDate startDate,
                                     @Param("endDate") java.time.LocalDate endDate);

    // Invoice edit: purane items delete karne se pehle released allocations ka item link hatao.
    @Modifying
    @Query("update InvoiceItemAllocation a set a.invoiceItem = null where a.invoiceItem in :items and a.active = false")
    int detachReleasedFromItems(@Param("items") List<InvoiceItem> items);

    @Query("""
            select a from InvoiceItemAllocation a
            where (:company is null or 1=1) and a.product = :product
            order by a.createdAt asc, a.id asc
            """)
    List<InvoiceItemAllocation> findByCompanyAndProductOrderByCreatedAtAscIdAsc(@Param("company") Company company, @Param("product") Product product);

    @Query("select count(a) from InvoiceItemAllocation a where (:company is null or 1=1) and a.productBatch in :productBatches and a.active = true")
    long countByCompanyAndProductBatchInAndActiveTrue(@Param("company") Company company, @Param("productBatches") List<ProductBatch> productBatches);
}
