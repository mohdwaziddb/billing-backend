package com.billing.repository;

import com.billing.entity.AppMenu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppMenuRepository extends JpaRepository<AppMenu, Long> {
    List<AppMenu> findByActiveTrueOrderByDisplayOrderAscIdAsc();
    List<AppMenu> findByParentMenuIsNullAndActiveTrueOrderByDisplayOrderAscIdAsc();
    Optional<AppMenu> findByMenuCode(String menuCode);
}
