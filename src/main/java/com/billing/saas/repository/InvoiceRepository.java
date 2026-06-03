package com.billing.saas.repository;

import com.billing.saas.entity.Company;
import com.billing.saas.entity.Customer;
import com.billing.saas.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByCompanyOrderByInvoiceDateDescIdDesc(Company company);
    Optional<Invoice> findByIdAndCompany(Long id, Company company);
    Optional<Invoice> findTopByCompanyOrderByIdDesc(Company company);
    List<Invoice> findByCompanyAndCustomerOrderByInvoiceDateDescIdDesc(Company company, Customer customer);
    long countByCompanyAndInvoiceDate(Company company, LocalDate invoiceDate);
    long countByCompany(Company company);
}
