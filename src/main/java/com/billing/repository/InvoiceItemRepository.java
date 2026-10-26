package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.Invoice;
import com.billing.entity.InvoiceItem;
import com.billing.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {
    List<InvoiceItem> findByInvoice(Invoice invoice);

    @Query("""
            select ii from InvoiceItem ii
            join fetch ii.invoice i
            where (:company is null or 1=1)
              and ii.product = :product
              and i.deleted = false
            order by i.invoiceDate asc, i.id asc, ii.id asc
            """)
    List<InvoiceItem> findHistoricalByCompanyAndProduct(@Param("company") Company company, @Param("product") Product product);

    // Top selling products: product-wise [productId, sum(qty), sum(lineTotal)] DB me.
    // Pehle range ke saare invoices items samet memory me aate the.
    @Query("""
            select ii.product.id, coalesce(sum(ii.qty), 0), coalesce(sum(ii.lineTotal), 0)
            from InvoiceItem ii
            join ii.invoice i
            where i.deleted = false
              and (:startDate is null or i.invoiceDate >= :startDate)
              and (:endDate is null or i.invoiceDate <= :endDate)
            group by ii.product.id
            """)
    List<Object[]> sumActiveSalesByProduct(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Sales by category: category-wise [categoryId, sum(lineTotal)].
    @Query("""
            select p.productCategory.id, coalesce(sum(ii.lineTotal), 0)
            from InvoiceItem ii
            join ii.invoice i
            join ii.product p
            where i.deleted = false
              and (:startDate is null or i.invoiceDate >= :startDate)
              and (:endDate is null or i.invoiceDate <= :endDate)
            group by p.productCategory.id
            """)
    List<Object[]> sumActiveSalesByCategory(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Dashboard: range me kitne alag products bike.
    @Query("""
            select count(distinct ii.product.id)
            from InvoiceItem ii
            join ii.invoice i
            where i.deleted = false
              and (:startDate is null or i.invoiceDate >= :startDate)
              and (:endDate is null or i.invoiceDate <= :endDate)
            """)
    long countActiveDistinctProductsInRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // VAT / sales-tax invoice ke IGST slot me rehta hai (GstReportService.isNonGst jaisa): uska jod.
    @Query("""
            select coalesce(sum(ii.igstAmount), 0)
            from InvoiceItem ii
            join ii.invoice i
            join ii.taxMaster tm
            where i.deleted = false
              and tm.taxType is not null
              and tm.taxType <> com.billing.entity.enums.TaxType.GST
              and (:startDate is null or i.invoiceDate >= :startDate)
              and (:endDate is null or i.invoiceDate <= :endDate)
            """)
    BigDecimal sumActiveNonGstTaxInRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
