package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByCompanyOrderByCreatedAtDesc(Company company);
    List<Product> findByCompanyAndActiveTrueOrderByCreatedAtDesc(Company company);

    @EntityGraph(attributePaths = "productCategory")
    @Query("""
            SELECT p
            FROM Product p
            LEFT JOIN p.productCategory pc
            WHERE p.company = :company
              AND (:active IS NULL OR p.active = :active)
              AND (
                    :search IS NULL
                    OR TRIM(:search) = ''
                    OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(pc.categoryName) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            ORDER BY p.createdAt DESC
            """)
    List<Product> findAllByCompanyWithFilters(@Param("company") Company company,
                                              @Param("active") Boolean active,
                                              @Param("search") String search);

    @EntityGraph(attributePaths = "productCategory")
    @Query("""
            SELECT p
            FROM Product p
            LEFT JOIN p.productCategory pc
            WHERE p.company = :company
              AND (:active IS NULL OR p.active = :active)
              AND (
                    :search IS NULL
                    OR TRIM(:search) = ''
                    OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(pc.categoryName) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            ORDER BY p.createdAt DESC
            """)
    Page<Product> findPageByCompanyWithFilters(@Param("company") Company company,
                                               @Param("active") Boolean active,
                                               @Param("search") String search,
                                               Pageable pageable);

    @EntityGraph(attributePaths = "productCategory")
    Optional<Product> findByIdAndCompany(Long id, Company company);

    boolean existsByCompanyAndSkuIgnoreCase(Company company, String sku);
    boolean existsByCompanyAndSkuIgnoreCaseAndIdNot(Company company, String sku, Long id);
    long countByCompany(Company company);
}
