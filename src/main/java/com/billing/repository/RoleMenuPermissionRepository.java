package com.billing.repository;

import com.billing.entity.AppMenu;
import com.billing.entity.Company;
import com.billing.entity.RoleMaster;
import com.billing.entity.RoleMenuPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleMenuPermissionRepository extends JpaRepository<RoleMenuPermission, Long> {
    @Query("select e from RoleMenuPermission e where (:company is null or 1=1) and e.role = :role")
    List<RoleMenuPermission> findByCompanyAndRole(@Param("company") Company company, @Param("role") RoleMaster role);
    @Query("select e from RoleMenuPermission e where (:company is null or 1=1) and e.role = :role and e.appMenu = :appMenu")
    Optional<RoleMenuPermission> findByCompanyAndRoleAndAppMenu(@Param("company") Company company, @Param("role") RoleMaster role, @Param("appMenu") AppMenu appMenu);
}
