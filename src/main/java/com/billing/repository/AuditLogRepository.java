package com.billing.repository;

import com.billing.entity.AuditLog;
import com.billing.entity.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    Page<AuditLog> findByCompanyAndModuleNameAndEntityIdOrderByCreatedAtDescIdDesc(Company company, String moduleName, Long entityId, Pageable pageable);

    @Query("select distinct log.userId, log.userName from AuditLog log where log.company = :company and log.userId is not null order by log.userName asc")
    List<Object[]> findDistinctUsersByCompany(@Param("company") Company company);
}
