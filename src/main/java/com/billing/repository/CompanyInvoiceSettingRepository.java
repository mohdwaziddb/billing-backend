package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.CompanyInvoiceSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompanyInvoiceSettingRepository extends JpaRepository<CompanyInvoiceSetting, Long> {
    @Query("select s from CompanyInvoiceSetting s where (:company is null or 1=1)")
    Optional<CompanyInvoiceSetting> findByCompany(@Param("company") Company company);

    Optional<CompanyInvoiceSetting> findFirstByOrderByIdDesc();
}
