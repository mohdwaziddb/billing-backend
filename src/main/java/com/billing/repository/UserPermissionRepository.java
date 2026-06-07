package com.billing.repository;

import com.billing.entity.AppMenu;
import com.billing.entity.AppMenuAction;
import com.billing.entity.Company;
import com.billing.entity.User;
import com.billing.entity.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {
    List<UserPermission> findByCompanyAndUser(Company company, User user);
    Optional<UserPermission> findByCompanyAndUserAndAppMenuAndAppMenuAction(Company company, User user, AppMenu appMenu, AppMenuAction action);
}
