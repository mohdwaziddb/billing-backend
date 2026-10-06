package com.billing.repository;

import com.billing.entity.AppMenu;
import com.billing.entity.AppMenuAction;
import com.billing.entity.Company;
import com.billing.entity.RoleMaster;
import com.billing.entity.RoleMenuActionPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleMenuActionPermissionRepository extends JpaRepository<RoleMenuActionPermission, Long> {
    @Query("select e from RoleMenuActionPermission e where (:company is null or 1=1) and e.role = :role")
    List<RoleMenuActionPermission> findByCompanyAndRole(@Param("company") Company company, @Param("role") RoleMaster role);
    @Query("select e from RoleMenuActionPermission e where (:company is null or 1=1) and e.role = :role and e.appMenu = :appMenu and e.appMenuAction = :action")
    Optional<RoleMenuActionPermission> findByCompanyAndRoleAndAppMenuAndAppMenuAction(@Param("company") Company company, @Param("role") RoleMaster role, @Param("appMenu") AppMenu appMenu, @Param("action") AppMenuAction action);
}
