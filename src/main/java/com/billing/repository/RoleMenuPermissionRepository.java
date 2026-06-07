package com.billing.repository;

import com.billing.entity.AppMenu;
import com.billing.entity.Company;
import com.billing.entity.RoleMaster;
import com.billing.entity.RoleMenuPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleMenuPermissionRepository extends JpaRepository<RoleMenuPermission, Long> {
    List<RoleMenuPermission> findByCompanyAndRole(Company company, RoleMaster role);
    Optional<RoleMenuPermission> findByCompanyAndRoleAndAppMenu(Company company, RoleMaster role, AppMenu appMenu);
}
