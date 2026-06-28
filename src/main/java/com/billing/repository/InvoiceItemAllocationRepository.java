package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.Invoice;
import com.billing.entity.InvoiceItem;
import com.billing.entity.InvoiceItemAllocation;
import com.billing.entity.Product;
import com.billing.entity.ProductBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface InvoiceItemAllocationRepository extends JpaRepository<InvoiceItemAllocation, Long> {

    List<InvoiceItemAllocation> findByCompanyAndInvoiceAndActiveTrueOrderByIdAsc(Company company, Invoice invoice);

    List<InvoiceItemAllocation> findByCompanyAndInvoiceItemAndActiveTrueOrderByIdAsc(Company company, InvoiceItem invoiceItem);

    @Query("""
            select coalesce(sum(a.costAmount), 0)
            from InvoiceItemAllocation a
            where a.company = :company
              and a.invoice.id = :invoiceId
              and a.active = true
            """)
    BigDecimal sumActiveCostByInvoice(@Param("company") Company company, @Param("invoiceId") Long invoiceId);

    @Query("""
            select coalesce(sum(a.costAmount), 0)
            from InvoiceItemAllocation a
            where a.company = :company
              and (:startDate is null or a.invoice.invoiceDate >= :startDate)
              and (:endDate is null or a.invoice.invoiceDate <= :endDate)
              and a.active = true
            """)
    BigDecimal sumActiveCostByInvoiceDateRange(@Param("company") Company company,
                                               @Param("startDate") java.time.LocalDate startDate,
                                               @Param("endDate") java.time.LocalDate endDate);

    @Query("""
            select coalesce(sum(a.costAmount), 0)
            from InvoiceItemAllocation a
            where a.company = :company
              and a.invoice.customer.id = :customerId
              and (:startDate is null or a.invoice.invoiceDate >= :startDate)
              and (:endDate is null or a.invoice.invoiceDate <= :endDate)
              and a.active = true
            """)
    BigDecimal sumActiveCostByCustomer(@Param("company") Company company,
                                       @Param("customerId") Long customerId,
                                       @Param("startDate") java.time.LocalDate startDate,
                                       @Param("endDate") java.time.LocalDate endDate);

    @Query("""
            select a from InvoiceItemAllocation a
            where a.company = :company
              and a.product = :product
            order by a.createdAt asc, a.id asc
            """)
    List<InvoiceItemAllocation> findByCompanyAndProductOrderByCreatedAtAscIdAsc(@Param("company") Company company, @Param("product") Product product);

    long countByCompanyAndProductBatchInAndActiveTrue(Company company, List<ProductBatch> productBatches);
}
