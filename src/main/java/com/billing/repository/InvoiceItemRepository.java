package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.Invoice;
import com.billing.entity.InvoiceItem;
import com.billing.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {
    List<InvoiceItem> findByInvoice(Invoice invoice);

    @Query("""
            select ii from InvoiceItem ii
            join fetch ii.invoice i
            where ii.company = :company
              and ii.product = :product
              and i.deleted = false
            order by i.invoiceDate asc, i.id asc, ii.id asc
            """)
    List<InvoiceItem> findHistoricalByCompanyAndProduct(@Param("company") Company company, @Param("product") Product product);
}
