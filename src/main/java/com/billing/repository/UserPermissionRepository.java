package com.billing.repository;

import com.billing.entity.AppMenu;
import com.billing.entity.AppMenuAction;
import com.billing.entity.Company;
import com.billing.entity.User;
import com.billing.entity.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {
    @Query("select e from UserPermission e where (:company is null or 1=1) and e.user = :user")
    List<UserPermission> findByCompanyAndUser(@Param("company") Company company, @Param("user") User user);
    @Query("select e from UserPermission e where (:company is null or 1=1) and e.user = :user and e.appMenu = :appMenu and e.appMenuAction = :action")
    Optional<UserPermission> findByCompanyAndUserAndAppMenuAndAppMenuAction(@Param("company") Company company, @Param("user") User user, @Param("appMenu") AppMenu appMenu, @Param("action") AppMenuAction action);
}
