package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.EmailProviderSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailProviderSettingRepository extends JpaRepository<EmailProviderSetting, Long> {
    Optional<EmailProviderSetting> findFirstByCompanyAndActiveTrueOrderByIdDesc(Company company);
    Optional<EmailProviderSetting> findByIdAndCompany(Long id, Company company);
    List<EmailProviderSetting> findByCompanyOrderByActiveDescProviderNameAsc(Company company);
    List<EmailProviderSetting> findByCompanyAndActiveTrue(Company company);
}
