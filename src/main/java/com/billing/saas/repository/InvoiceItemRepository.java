package com.billing.saas.repository;

import com.billing.saas.entity.Invoice;
import com.billing.saas.entity.InvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {
    List<InvoiceItem> findByInvoice(Invoice invoice);
}
