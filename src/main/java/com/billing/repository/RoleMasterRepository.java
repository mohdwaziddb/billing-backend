package com.billing.repository;

import com.billing.entity.RoleMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleMasterRepository extends JpaRepository<RoleMaster, Long> {
    Optional<RoleMaster> findByRoleCode(String roleCode);
}
