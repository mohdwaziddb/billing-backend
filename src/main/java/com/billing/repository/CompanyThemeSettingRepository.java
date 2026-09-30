package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.CompanyThemeSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompanyThemeSettingRepository extends JpaRepository<CompanyThemeSetting, Long> {
    @Query("select s from CompanyThemeSetting s where (:company is null or 1=1)")
    Optional<CompanyThemeSetting> findByCompany(@Param("company") Company company);

    Optional<CompanyThemeSetting> findFirstByOrderByIdDesc();
}
