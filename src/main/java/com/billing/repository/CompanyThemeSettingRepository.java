package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.CompanyThemeSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyThemeSettingRepository extends JpaRepository<CompanyThemeSetting, Long> {
    Optional<CompanyThemeSetting> findByCompany(Company company);
}
