package com.billing.saas.repository;

import com.billing.saas.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import com.billing.saas.entity.Company;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByMobileNumber(String mobileNumber);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByMobileNumber(String mobileNumber);
    List<User> findByCompanyOrderByCreatedAtDesc(Company company);
    Optional<User> findByIdAndCompany(Long id, Company company);
}
