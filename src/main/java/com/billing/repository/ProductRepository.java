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
import java.util.Set;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByOrderByCreatedAtDesc();
    @Query("select p from Product p where (:company is null or 1=1) order by p.createdAt desc")
    List<Product> findByCompanyOrderByCreatedAtDesc(@Param("company") Company company);
    @Query("select p from Product p where (:company is null or 1=1) and p.active = true order by p.createdAt desc")
    List<Product> findByCompanyAndActiveTrueOrderByCreatedAtDesc(@Param("company") Company company);

    @EntityGraph(attributePaths = {"productCategory", "productSubCategory", "taxMaster"})
    @Query("""
            SELECT p
            FROM Product p
            LEFT JOIN p.productCategory pc
            LEFT JOIN p.productSubCategory psc
            WHERE (:company IS NULL OR 1=1)
              AND (:active IS NULL OR p.active = :active)
              AND (:categoryId IS NULL OR pc.id = :categoryId)
              AND (:subCategoryId IS NULL OR psc.id = :subCategoryId)
              AND (
                    :search IS NULL
                    OR TRIM(:search) = ''
                    OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(pc.categoryName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(psc.subCategoryName) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            ORDER BY p.createdAt DESC
            """)
    List<Product> findAllByCompanyWithFilters(@Param("company") Company company,
                                              @Param("active") Boolean active,
                                              @Param("categoryId") Long categoryId,
                                              @Param("subCategoryId") Long subCategoryId,
                                              @Param("search") String search);

    @EntityGraph(attributePaths = {"productCategory", "productSubCategory", "taxMaster"})
    @Query("""
            SELECT p
            FROM Product p
            LEFT JOIN p.productCategory pc
            LEFT JOIN p.productSubCategory psc
            WHERE (:company IS NULL OR 1=1)
              AND (:active IS NULL OR p.active = :active)
              AND (:categoryId IS NULL OR pc.id = :categoryId)
              AND (:subCategoryId IS NULL OR psc.id = :subCategoryId)
              AND (
                    :search IS NULL
                    OR TRIM(:search) = ''
                    OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(pc.categoryName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(psc.subCategoryName) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            ORDER BY p.createdAt DESC
            """)
    Page<Product> findPageByCompanyWithFilters(@Param("company") Company company,
                                               @Param("active") Boolean active,
                                               @Param("categoryId") Long categoryId,
                                               @Param("subCategoryId") Long subCategoryId,
                                               @Param("search") String search,
                                               Pageable pageable);

    @EntityGraph(attributePaths = {"productCategory", "productSubCategory", "taxMaster"})
    @Query("select p from Product p where p.id = :id and (:company is null or 1=1)")
    Optional<Product> findByIdAndCompany(@Param("id") Long id, @Param("company") Company company);

    @Query("select case when count(p) > 0 then true else false end from Product p where (:company is null or 1=1) and lower(p.sku) = lower(:sku)")
    boolean existsByCompanyAndSkuIgnoreCase(@Param("company") Company company, @Param("sku") String sku);
    @Query("select case when count(p) > 0 then true else false end from Product p where (:company is null or 1=1) and lower(p.sku) = lower(:sku) and p.id <> :id")
    boolean existsByCompanyAndSkuIgnoreCaseAndIdNot(@Param("company") Company company, @Param("sku") String sku, @Param("id") Long id);
    @Query("select count(p) from Product p where (:company is null or 1=1)")
    long countByCompany(@Param("company") Company company);

    @Query("""
            SELECT LOWER(TRIM(p.sku))
            FROM Product p
            where (:company is null or 1=1)
            """)
    Set<String> findNormalizedSkusByCompany(@Param("company") Company company);
}
