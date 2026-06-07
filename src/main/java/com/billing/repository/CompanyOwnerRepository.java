package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.CompanyOwner;
import com.billing.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyOwnerRepository extends JpaRepository<CompanyOwner, Long> {
    boolean existsByCompanyAndUserAndUserActiveTrue(Company company, User user);
    List<CompanyOwner> findByCompanyOrderByCreatedAtAsc(Company company);
    Optional<CompanyOwner> findByCompanyAndUser(Company company, User user);
    void deleteByCompanyAndUser(Company company, User user);
}
