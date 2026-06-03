package com.billing.saas.repository;

import com.billing.saas.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByTaxIdIgnoreCase(String taxId);
    boolean existsByCodeIgnoreCase(String code);
    Optional<Company> findById(Long id);
}
