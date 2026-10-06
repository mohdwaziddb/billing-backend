package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.WhatsAppProviderSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WhatsAppProviderSettingRepository extends JpaRepository<WhatsAppProviderSetting, Long> {
    @Query("select e from WhatsAppProviderSetting e where (:company is null or 1=1) and e.active=true order by e.id desc")
    Optional<WhatsAppProviderSetting> findFirstByCompanyAndActiveTrueOrderByIdDesc(@Param("company") Company company);

    @Query("select e from WhatsAppProviderSetting e where (:company is null or 1=1) order by e.active desc, e.providerName asc")
    List<WhatsAppProviderSetting> findByCompanyOrderByActiveDescProviderNameAsc(@Param("company") Company company);

    @Query("select e from WhatsAppProviderSetting e where (:company is null or 1=1) and e.active=true")
    List<WhatsAppProviderSetting> findByCompanyAndActiveTrue(@Param("company") Company company);
}
