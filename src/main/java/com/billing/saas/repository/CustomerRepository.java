package com.billing.saas.repository;

import com.billing.saas.entity.Company;
import com.billing.saas.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByCompanyOrderByCreatedAtDesc(Company company);
    List<Customer> findByCompanyAndActiveTrueOrderByCreatedAtDesc(Company company);
    @Query("""
            SELECT c
            FROM Customer c
            WHERE c.company = :company
              AND (:active IS NULL OR c.active = :active)
              AND (:search IS NULL OR TRIM(:search) = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY c.createdAt DESC
            """)
    List<Customer> findAllByCompanyWithFilters(@Param("company") Company company,
                                               @Param("active") Boolean active,
                                               @Param("search") String search);
    Optional<Customer> findByCompanyAndMobileIgnoreCase(Company company, String mobile);
    Optional<Customer> findByIdAndCompany(Long id, Company company);
    List<Customer> findByCompanyAndCurrentBalanceGreaterThanOrderByCurrentBalanceDesc(Company company, BigDecimal amount);
    List<Customer> findByCompanyAndActiveTrueAndCurrentBalanceGreaterThanOrderByCurrentBalanceDesc(Company company, BigDecimal amount);
    boolean existsByCompanyAndMobileIgnoreCaseAndIdNot(Company company, String mobile, Long id);
    boolean existsByCompanyAndMobileIgnoreCase(Company company, String mobile);
    boolean existsByCompanyAndEmailIgnoreCaseAndIdNot(Company company, String email, Long id);
    boolean existsByCompanyAndEmailIgnoreCase(Company company, String email);
}
