package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.EmailLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
    Page<EmailLog> findByCompanyOrderBySentAtDescCreatedAtDesc(Company company, Pageable pageable);
}
