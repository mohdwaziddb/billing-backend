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

public interface ReminderLogRepository extends JpaRepository<ReminderLog, Long> {
    @Query("select e from ReminderLog e where (:company is null or 1=1) and e.customer = :customer order by e.createdAt desc")
    List<ReminderLog> findByCompanyAndCustomerOrderByCreatedAtDesc(@Param("company") Company company, @Param("customer") Customer customer);

    @Query("select e from ReminderLog e where (:company is null or 1=1) and e.customer = :customer order by e.createdAt desc")
    Page<ReminderLog> findByCompanyAndCustomerOrderByCreatedAtDesc(@Param("company") Company company, @Param("customer") Customer customer, Pageable pageable);

    /** Har customer ka sabse naya reminder, ek query me (Outstanding list). */
    @Query("select e from ReminderLog e where e.createdAt = (select max(x.createdAt) from ReminderLog x where x.customer = e.customer)")
    List<ReminderLog> findLatestPerCustomer();

    /** Outstanding list: sirf current page ke customers ka latest reminder (poori reminder_logs nahi). */
    @Query("select e from ReminderLog e where e.customer.id in :customerIds and e.createdAt = (select max(x.createdAt) from ReminderLog x where x.customer = e.customer)")
    List<ReminderLog> findLatestForCustomerIds(@Param("customerIds") java.util.Collection<Long> customerIds);
}
