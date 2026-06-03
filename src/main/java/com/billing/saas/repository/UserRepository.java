package com.billing.saas.repository;

import com.billing.saas.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import com.billing.saas.entity.Company;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findByCompanyOrderByCreatedAtDesc(Company company);
    Optional<User> findByIdAndCompany(Long id, Company company);
    boolean existsByCompanyAndRole(Company company, com.billing.saas.entity.enums.RoleName role);
}
