package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    List<Invoice> findByCompanyOrderByInvoiceDateDescIdDesc(Company company);
    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    Page<Invoice> findByCompany(Company company, Pageable pageable);
    Optional<Invoice> findByIdAndCompany(Long id, Company company);
    Optional<Invoice> findTopByCompanyOrderByIdDesc(Company company);
    List<Invoice> findByCompanyAndCustomerOrderByInvoiceDateDescIdDesc(Company company, Customer customer);
    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    Page<Invoice> findByCompanyAndCustomer(Company company, Customer customer, Pageable pageable);
    long countByCompanyAndInvoiceDate(Company company, LocalDate invoiceDate);
    long countByCompany(Company company);
}
