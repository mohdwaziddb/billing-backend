package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.ReminderLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReminderLogRepository extends JpaRepository<ReminderLog, Long> {
    List<ReminderLog> findByCompanyAndCustomerOrderByCreatedAtDesc(Company company, Customer customer);
    Page<ReminderLog> findByCompanyAndCustomerOrderByCreatedAtDesc(Company company, Customer customer, Pageable pageable);
    Optional<ReminderLog> findFirstByCompanyAndCustomerOrderByCreatedAtDesc(Company company, Customer customer);
}
