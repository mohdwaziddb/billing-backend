package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.SmsProviderSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SmsProviderSettingRepository extends JpaRepository<SmsProviderSetting, Long> {
    Optional<SmsProviderSetting> findFirstByCompanyAndActiveTrueOrderByIdDesc(Company company);
    Optional<SmsProviderSetting> findByIdAndCompany(Long id, Company company);
    List<SmsProviderSetting> findByCompanyOrderByActiveDescProviderNameAsc(Company company);
    List<SmsProviderSetting> findByCompanyAndActiveTrue(Company company);
}
