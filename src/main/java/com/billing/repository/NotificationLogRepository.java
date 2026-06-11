package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    Page<NotificationLog> findByCompanyOrderBySentAtDescCreatedAtDesc(Company company, Pageable pageable);
}
