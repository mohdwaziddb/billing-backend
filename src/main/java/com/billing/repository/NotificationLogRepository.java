package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    @Query("select e from NotificationLog e where (:company is null or 1=1) order by e.sentAt desc, e.createdAt desc")
    Page<NotificationLog> findByCompanyOrderBySentAtDescCreatedAtDesc(@Param("company") Company company, Pageable pageable);
}
