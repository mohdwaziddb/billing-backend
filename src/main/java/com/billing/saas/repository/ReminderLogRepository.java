package com.billing.saas.repository;

import com.billing.saas.entity.Company;
import com.billing.saas.entity.Customer;
import com.billing.saas.entity.ReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReminderLogRepository extends JpaRepository<ReminderLog, Long> {
    List<ReminderLog> findByCompanyAndCustomerOrderByCreatedAtDesc(Company company, Customer customer);
}
