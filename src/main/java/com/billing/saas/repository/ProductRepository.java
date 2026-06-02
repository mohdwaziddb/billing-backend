package com.billing.saas.repository;

import com.billing.saas.entity.Company;
import com.billing.saas.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByCompanyOrderByCreatedAtDesc(Company company);
    List<Product> findByCompanyAndActiveTrueOrderByCreatedAtDesc(Company company);
    @Query("""
            SELECT p
            FROM Product p
            WHERE p.company = :company
              AND (:active IS NULL OR p.active = :active)
              AND (
                    :search IS NULL
                    OR TRIM(:search) = ''
                    OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            ORDER BY p.createdAt DESC
            """)
    List<Product> findAllByCompanyWithFilters(@Param("company") Company company,
                                              @Param("active") Boolean active,
                                              @Param("search") String search);
    Optional<Product> findByIdAndCompany(Long id, Company company);
    boolean existsByCompanyAndSkuIgnoreCase(Company company, String sku);
    boolean existsByCompanyAndSkuIgnoreCaseAndIdNot(Company company, String sku, Long id);
    long countByCompany(Company company);
}
