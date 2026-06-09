package com.billing.repository;

import com.billing.entity.AuditLog;
import com.billing.entity.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByCompanyAndModuleNameAndEntityIdOrderByCreatedAtDescIdDesc(Company company, String moduleName, Long entityId, Pageable pageable);
}
