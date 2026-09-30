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
    @Query("select a from AuditLog a where (:company is null or 1=1) and a.moduleName = :moduleName and a.entityId = :entityId order by a.createdAt desc, a.id desc")
    Page<AuditLog> findByCompanyAndModuleNameAndEntityIdOrderByCreatedAtDescIdDesc(@Param("company") Company company, @Param("moduleName") String moduleName, @Param("entityId") Long entityId, Pageable pageable);
    @Query("select count(a) from AuditLog a where (:company is null or 1=1)")
    long countByCompany(@Param("company") Company company);

    @Query("select distinct log.userId, log.userName from AuditLog log where (:company is null or 1=1) and log.userId is not null order by log.userName asc")
    List<Object[]> findDistinctUsersByCompany(@Param("company") Company company);

    @Query("select distinct log.userId, log.userName from AuditLog log where log.userId is not null order by log.userName asc")
    List<Object[]> findDistinctUsers();
}
