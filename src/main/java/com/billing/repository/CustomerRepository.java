package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findAllByOrderByCreatedAtDesc();
    List<Customer> findByActiveTrueOrderByCreatedAtDesc();
    long count();
    @Query("""
            SELECT c
            FROM Customer c
            WHERE (:company IS NULL OR 1=1)
              AND (:active IS NULL OR c.active = :active)
              AND (:search IS NULL OR TRIM(:search) = ''
                OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(c.mobile) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(c.email, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY c.createdAt DESC
            """)
    List<Customer> findAllByCompanyWithFilters(@Param("company") Company company,
                                               @Param("active") Boolean active,
                                               @Param("search") String search);
    @Query("""
            SELECT c
            FROM Customer c
            WHERE (:company IS NULL OR 1=1)
              AND (:active IS NULL OR c.active = :active)
              AND (:search IS NULL OR TRIM(:search) = ''
                OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(c.mobile) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(c.email, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY c.createdAt DESC
            """)
    Page<Customer> findPageByCompanyWithFilters(@Param("company") Company company,
                                                @Param("active") Boolean active,
                                                @Param("search") String search,
                                                Pageable pageable);
    Optional<Customer> findByMobileIgnoreCase(String mobile);
    Optional<Customer> findByMobileIgnoreCaseAndActiveTrue(String mobile);
    @Query("select c from Customer c where c.id = :id and (:company is null or 1=1)")
    Optional<Customer> findByIdAndCompany(@Param("id") Long id, @Param("company") Company company);
    List<Customer> findByCurrentBalanceGreaterThanOrderByCurrentBalanceDesc(BigDecimal amount);
    @Query("""
            SELECT c
            FROM Customer c
            WHERE (:company IS NULL OR 1=1)
              AND c.currentBalance > 0
              AND (:minBalance IS NULL OR c.currentBalance >= :minBalance)
              AND (:search IS NULL OR TRIM(:search) = ''
                OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(c.mobile) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY c.currentBalance DESC
            """)
    Page<Customer> findOutstandingPageByCompanyWithFilters(@Param("company") Company company,
                                                           @Param("search") String search,
                                                           @Param("minBalance") BigDecimal minBalance,
                                                           Pageable pageable);
    List<Customer> findByActiveTrueAndCurrentBalanceGreaterThanOrderByCurrentBalanceDesc(BigDecimal amount);
    boolean existsByMobileIgnoreCaseAndIdNot(String mobile, Long id);
    boolean existsByMobileIgnoreCase(String mobile);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    boolean existsByEmailIgnoreCase(String email);

    @Modifying
    @Query("update Customer c set c.currentBalance = c.currentBalance + :delta where c.id = :id and c.currentBalance + :delta >= 0")
    int adjustBalance(@Param("id") Long id, @Param("delta") BigDecimal delta);
}
