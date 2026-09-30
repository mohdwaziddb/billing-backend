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
            WHERE (:company IS NULL OR 1=1)
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
            WHERE (:company IS NULL OR 1=1)
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
    @Query("select sc from ProductSubCategory sc where (:company is null or 1=1) and sc.productCategory = :productCategory and lower(sc.subCategoryName)=lower(:subCategoryName)")
    Optional<ProductSubCategory> findByCompanyAndProductCategoryAndSubCategoryNameIgnoreCase(@Param("company") Company company,
                                                                                              @Param("productCategory") ProductCategory productCategory,
                                                                                              @Param("subCategoryName") String subCategoryName);

    @Query("select sc from ProductSubCategory sc where (:company is null or 1=1) and sc.productCategory = :productCategory and lower(sc.subCategoryName)=lower(:subCategoryName) and sc.active=true")
    Optional<ProductSubCategory> findByCompanyAndProductCategoryAndSubCategoryNameIgnoreCaseAndActiveTrue(@Param("company") Company company,
                                                                                                            @Param("productCategory") ProductCategory productCategory,
                                                                                                            @Param("subCategoryName") String subCategoryName);

    @EntityGraph(attributePaths = "productCategory")
    @Query("select sc from ProductSubCategory sc where (:company is null or 1=1) and sc.productCategory = :productCategory and sc.active=true order by sc.subCategoryName asc")
    List<ProductSubCategory> findByCompanyAndProductCategoryAndActiveTrueOrderBySubCategoryNameAsc(@Param("company") Company company, @Param("productCategory") ProductCategory productCategory);

    @Query("select case when count(sc)>0 then true else false end from ProductSubCategory sc where (:company is null or 1=1) and sc.productCategory = :productCategory and lower(sc.subCategoryName)=lower(:subCategoryName)")
    boolean existsByCompanyAndProductCategoryAndSubCategoryNameIgnoreCase(@Param("company") Company company,
                                                                          @Param("productCategory") ProductCategory productCategory,
                                                                          @Param("subCategoryName") String subCategoryName);

    @Query("select case when count(sc)>0 then true else false end from ProductSubCategory sc where (:company is null or 1=1) and sc.productCategory = :productCategory and lower(sc.subCategoryName)=lower(:subCategoryName) and sc.id <> :id")
    boolean existsByCompanyAndProductCategoryAndSubCategoryNameIgnoreCaseAndIdNot(@Param("company") Company company,
                                                                                  @Param("productCategory") ProductCategory productCategory,
                                                                                  @Param("subCategoryName") String subCategoryName,
                                                                                  @Param("id") Long id);
}
