package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.ProductCategory;
import com.billing.entity.ProductSubCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductSubCategoryRepository extends JpaRepository<ProductSubCategory, Long> {

    @EntityGraph(attributePaths = "productCategory")
    @Query("""
            SELECT sc
            FROM ProductSubCategory sc
            JOIN sc.productCategory pc
            WHERE sc.company = :company
              AND (:categoryId IS NULL OR pc.id = :categoryId)
              AND (:active IS NULL OR sc.active = :active)
              AND (
                    :search IS NULL
                    OR TRIM(:search) = ''
                    OR LOWER(sc.subCategoryName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(sc.description) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(pc.categoryName) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            ORDER BY pc.categoryName ASC, sc.subCategoryName ASC
            """)
    List<ProductSubCategory> findAllByCompanyWithFilters(@Param("company") Company company,
                                                         @Param("categoryId") Long categoryId,
                                                         @Param("active") Boolean active,
                                                         @Param("search") String search);

    @EntityGraph(attributePaths = "productCategory")
    @Query("""
            SELECT sc
            FROM ProductSubCategory sc
            JOIN sc.productCategory pc
            WHERE sc.company = :company
              AND (:categoryId IS NULL OR pc.id = :categoryId)
              AND (:active IS NULL OR sc.active = :active)
              AND (
                    :search IS NULL
                    OR TRIM(:search) = ''
                    OR LOWER(sc.subCategoryName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(sc.description) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(pc.categoryName) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            ORDER BY pc.categoryName ASC, sc.subCategoryName ASC
            """)
    Page<ProductSubCategory> findPageByCompanyWithFilters(@Param("company") Company company,
                                                          @Param("categoryId") Long categoryId,
                                                          @Param("active") Boolean active,
                                                          @Param("search") String search,
                                                          Pageable pageable);

    @EntityGraph(attributePaths = "productCategory")
    Optional<ProductSubCategory> findByIdAndCompany(Long id, Company company);

    Optional<ProductSubCategory> findByCompanyAndProductCategoryAndSubCategoryNameIgnoreCase(Company company,
                                                                                              ProductCategory productCategory,
                                                                                              String subCategoryName);

    Optional<ProductSubCategory> findByCompanyAndProductCategoryAndSubCategoryNameIgnoreCaseAndActiveTrue(Company company,
                                                                                                            ProductCategory productCategory,
                                                                                                            String subCategoryName);

    @EntityGraph(attributePaths = "productCategory")
    List<ProductSubCategory> findByCompanyAndProductCategoryAndActiveTrueOrderBySubCategoryNameAsc(Company company, ProductCategory productCategory);

    boolean existsByCompanyAndProductCategoryAndSubCategoryNameIgnoreCase(Company company,
                                                                          ProductCategory productCategory,
                                                                          String subCategoryName);

    boolean existsByCompanyAndProductCategoryAndSubCategoryNameIgnoreCaseAndIdNot(Company company,
                                                                                  ProductCategory productCategory,
                                                                                  String subCategoryName,
                                                                                  Long id);
}
