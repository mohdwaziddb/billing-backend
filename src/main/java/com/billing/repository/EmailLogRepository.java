package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.EmailLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
    @Query("select e from EmailLog e where (:company is null or 1=1) order by e.sentAt desc, e.createdAt desc")
    Page<EmailLog> findByCompanyOrderBySentAtDescCreatedAtDesc(@Param("company") Company company, Pageable pageable);
}
