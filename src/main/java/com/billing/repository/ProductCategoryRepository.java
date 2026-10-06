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
            where (:company is null or 1=1) and (:active IS NULL OR c.active = :active)
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
            where (:company is null or 1=1) and (:active IS NULL OR c.active = :active)
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

    @Query("select c from ProductCategory c where (:company is null or 1=1) and lower(c.categoryName)=lower(:categoryName)")
    Optional<ProductCategory> findByCompanyAndCategoryNameIgnoreCase(@Param("company") Company company, @Param("categoryName") String categoryName);

    @Query("select c from ProductCategory c where (:company is null or 1=1) and lower(c.categoryName)=lower(:categoryName) and c.active=true")
    Optional<ProductCategory> findByCompanyAndCategoryNameIgnoreCaseAndActiveTrue(@Param("company") Company company, @Param("categoryName") String categoryName);

    @Query("select c from ProductCategory c where (:company is null or 1=1) and c.active=true order by c.categoryName asc")
    List<ProductCategory> findByCompanyAndActiveTrueOrderByCategoryNameAsc(@Param("company") Company company);

    @Query("select case when count(c)>0 then true else false end from ProductCategory c where (:company is null or 1=1) and lower(c.categoryName)=lower(:categoryName)")
    boolean existsByCompanyAndCategoryNameIgnoreCase(@Param("company") Company company, @Param("categoryName") String categoryName);

    @Query("select case when count(c)>0 then true else false end from ProductCategory c where (:company is null or 1=1) and lower(c.categoryName)=lower(:categoryName) and c.id <> :id")
    boolean existsByCompanyAndCategoryNameIgnoreCaseAndIdNot(@Param("company") Company company, @Param("categoryName") String categoryName, @Param("id") Long id);
}
