package com.billing.repository;

import com.billing.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    List<Company> findAll();
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByTaxIdIgnoreCase(String taxId);
    boolean existsByCodeIgnoreCase(String code);
}
