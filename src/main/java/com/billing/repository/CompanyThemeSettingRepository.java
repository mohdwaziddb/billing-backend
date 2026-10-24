package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.CompanyThemeSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyThemeSettingRepository extends JpaRepository<CompanyThemeSetting, Long> {
    /**
     * DATABASE-per-tenant: har tenant DB me ek hi theme row honi chahiye. Table par
     * unique guard nahi hai, isliye single-result query ki jagah latest row lo -
     * galti se duplicate row ban jaaye to bhi GET/PUT 500 (IncorrectResultSize) na de.
     */
    default Optional<CompanyThemeSetting> findByCompany(Company company) {
        return findFirstByOrderByIdDesc();
    }

    Optional<CompanyThemeSetting> findFirstByOrderByIdDesc();
}
