package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.CompanyInvoiceSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyInvoiceSettingRepository extends JpaRepository<CompanyInvoiceSetting, Long> {
    Optional<CompanyInvoiceSetting> findByCompany(Company company);
}
