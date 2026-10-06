package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.EmailProviderSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmailProviderSettingRepository extends JpaRepository<EmailProviderSetting, Long> {
    @Query("select e from EmailProviderSetting e where (:company is null or 1=1) and e.active = true order by e.id desc")
    Optional<EmailProviderSetting> findFirstByCompanyAndActiveTrueOrderByIdDesc(@Param("company") Company company);
    @Query("select e from EmailProviderSetting e where (:company is null or 1=1) order by e.active desc, e.providerName asc")
    List<EmailProviderSetting> findByCompanyOrderByActiveDescProviderNameAsc(@Param("company") Company company);
    @Query("select e from EmailProviderSetting e where (:company is null or 1=1) and e.active = true")
    List<EmailProviderSetting> findByCompanyAndActiveTrue(@Param("company") Company company);
}
