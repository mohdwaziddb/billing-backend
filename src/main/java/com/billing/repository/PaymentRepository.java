package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.Invoice;
import com.billing.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @EntityGraph(attributePaths = {"customer", "invoice"})
    List<Payment> findByCompanyOrderByPaymentDateDescIdDesc(Company company);
    @EntityGraph(attributePaths = {"customer", "invoice"})
    Page<Payment> findByCompany(Company company, Pageable pageable);
    Optional<Payment> findByIdAndCompany(Long id, Company company);
    List<Payment> findByCompanyAndCustomerOrderByPaymentDateDescIdDesc(Company company, Customer customer);
    List<Payment> findByCompanyAndCustomerAndAmountGreaterThanOrderByPaymentDateDescIdDesc(Company company, Customer customer, BigDecimal amount);
    boolean existsByInvoice(Invoice invoice);
}
