package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.ReminderLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReminderLogRepository extends JpaRepository<ReminderLog, Long> {
    @Query("select e from ReminderLog e where (:company is null or 1=1) and e.customer = :customer order by e.createdAt desc")
    List<ReminderLog> findByCompanyAndCustomerOrderByCreatedAtDesc(@Param("company") Company company, @Param("customer") Customer customer);

    @Query("select e from ReminderLog e where (:company is null or 1=1) and e.customer = :customer order by e.createdAt desc")
    Page<ReminderLog> findByCompanyAndCustomerOrderByCreatedAtDesc(@Param("company") Company company, @Param("customer") Customer customer, Pageable pageable);

    @Query("select e from ReminderLog e where (:company is null or 1=1) and e.customer = :customer order by e.createdAt desc")
    Optional<ReminderLog> findFirstByCompanyAndCustomerOrderByCreatedAtDesc(@Param("company") Company company, @Param("customer") Customer customer);
}
