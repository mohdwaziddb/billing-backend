package com.billing.repository;

import com.billing.entity.AppMenu;
import com.billing.entity.AppMenuAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppMenuActionRepository extends JpaRepository<AppMenuAction, Long> {
    List<AppMenuAction> findByActiveTrueOrderByIdAsc();
    List<AppMenuAction> findByAppMenuAndActiveTrueOrderByIdAsc(AppMenu appMenu);
    Optional<AppMenuAction> findByAppMenuAndActionCode(AppMenu appMenu, String actionCode);
}
