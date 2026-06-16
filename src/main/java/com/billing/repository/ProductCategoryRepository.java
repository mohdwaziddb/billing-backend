package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.ProductCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    @Query("""
            SELECT c
            FROM ProductCategory c
            WHERE c.company = :company
              AND (:active IS NULL OR c.active = :active)
              AND (
                    :search IS NULL
                    OR TRIM(:search) = ''
                    OR LOWER(c.categoryName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            ORDER BY c.categoryName ASC
            """)
    List<ProductCategory> findAllByCompanyWithFilters(@Param("company") Company company,
                                                      @Param("active") Boolean active,
                                                      @Param("search") String search);
    @Query("""
            SELECT c
            FROM ProductCategory c
            WHERE c.company = :company
              AND (:active IS NULL OR c.active = :active)
              AND (
                    :search IS NULL
                    OR TRIM(:search) = ''
                    OR LOWER(c.categoryName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            ORDER BY c.categoryName ASC
            """)
    Page<ProductCategory> findPageByCompanyWithFilters(@Param("company") Company company,
                                                       @Param("active") Boolean active,
                                                       @Param("search") String search,
                                                       Pageable pageable);

    Optional<ProductCategory> findByIdAndCompany(Long id, Company company);

    Optional<ProductCategory> findByCompanyAndCategoryNameIgnoreCase(Company company, String categoryName);

    Optional<ProductCategory> findByCompanyAndCategoryNameIgnoreCaseAndActiveTrue(Company company, String categoryName);

    List<ProductCategory> findByCompanyAndActiveTrueOrderByCategoryNameAsc(Company company);

    boolean existsByCompanyAndCategoryNameIgnoreCase(Company company, String categoryName);

    boolean existsByCompanyAndCategoryNameIgnoreCaseAndIdNot(Company company, String categoryName, Long id);
}
