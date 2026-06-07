package com.billing.repository;

import com.billing.entity.AppMenu;
import com.billing.entity.AppMenuAction;
import com.billing.entity.Company;
import com.billing.entity.RoleMaster;
import com.billing.entity.RoleMenuActionPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleMenuActionPermissionRepository extends JpaRepository<RoleMenuActionPermission, Long> {
    List<RoleMenuActionPermission> findByCompanyAndRole(Company company, RoleMaster role);
    Optional<RoleMenuActionPermission> findByCompanyAndRoleAndAppMenuAndAppMenuAction(Company company, RoleMaster role, AppMenu appMenu, AppMenuAction action);
}
