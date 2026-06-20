package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.WhatsAppProviderSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WhatsAppProviderSettingRepository extends JpaRepository<WhatsAppProviderSetting, Long> {
    Optional<WhatsAppProviderSetting> findFirstByCompanyAndActiveTrueOrderByIdDesc(Company company);
    Optional<WhatsAppProviderSetting> findByIdAndCompany(Long id, Company company);
    List<WhatsAppProviderSetting> findByCompanyOrderByActiveDescProviderNameAsc(Company company);
    List<WhatsAppProviderSetting> findByCompanyAndActiveTrue(Company company);
}
