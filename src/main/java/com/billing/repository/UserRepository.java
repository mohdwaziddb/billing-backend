package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByMobileNumber(String mobileNumber);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByMobileNumber(String mobileNumber);
    List<User> findByCompanyOrderByCreatedAtDesc(Company company);
    Page<User> findByCompanyOrderByCreatedAtDesc(Company company, Pageable pageable);
    Optional<User> findByIdAndCompany(Long id, Company company);
}
