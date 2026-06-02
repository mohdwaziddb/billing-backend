package com.billing.saas.repository;

import com.billing.saas.entity.Company;
import com.billing.saas.entity.Customer;
import com.billing.saas.entity.Invoice;
import com.billing.saas.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByCompanyOrderByPaymentDateDescIdDesc(Company company);
    Optional<Payment> findByIdAndCompany(Long id, Company company);
    List<Payment> findByCompanyAndCustomerOrderByPaymentDateDescIdDesc(Company company, Customer customer);
    boolean existsByInvoice(Invoice invoice);
}
