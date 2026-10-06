package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.ExpenseCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    @Query("select c from ExpenseCategory c where (:company is null or 1=1) order by c.categoryName asc")
    List<ExpenseCategory> findByCompanyOrderByCategoryNameAsc(@Param("company") Company company);

    @Query("""
            select c from ExpenseCategory c
            where (:company is null or 1=1) and (:active is null or c.active = :active)
              and (:search is null
                or lower(c.categoryName) like lower(concat('%', :search, '%'))
                or lower(coalesce(c.description, '')) like lower(concat('%', :search, '%')))
            order by c.categoryName asc
            """)
    Page<ExpenseCategory> findPageByCompanyWithFilters(@Param("company") Company company,
                                                       @Param("active") Boolean active,
                                                       @Param("search") String search,
                                                       Pageable pageable);

    @Query("""
            select c from ExpenseCategory c
            where (:company is null or 1=1) and (:active is null or c.active = :active)
              and (:search is null
                or lower(c.categoryName) like lower(concat('%', :search, '%'))
                or lower(coalesce(c.description, '')) like lower(concat('%', :search, '%')))
            order by c.categoryName asc
            """)
    List<ExpenseCategory> findAllByCompanyWithFilters(@Param("company") Company company,
                                                       @Param("active") Boolean active,
                                                       @Param("search") String search);
    @Query("select c from ExpenseCategory c where (:company is null or 1=1) and lower(c.categoryName) = lower(:categoryName)")
    Optional<ExpenseCategory> findByCompanyAndCategoryNameIgnoreCase(@Param("company") Company company, @Param("categoryName") String categoryName);

    @Query("select c from ExpenseCategory c where (:company is null or 1=1) and lower(c.categoryName) = lower(:categoryName) and c.active = true")
    Optional<ExpenseCategory> findByCompanyAndCategoryNameIgnoreCaseAndActiveTrue(@Param("company") Company company, @Param("categoryName") String categoryName);

    @Query("select case when count(c) > 0 then true else false end from ExpenseCategory c where (:company is null or 1=1) and lower(c.categoryName) = lower(:categoryName)")
    boolean existsByCompanyAndCategoryNameIgnoreCase(@Param("company") Company company, @Param("categoryName") String categoryName);

    @Query("select case when count(c) > 0 then true else false end from ExpenseCategory c where (:company is null or 1=1) and lower(c.categoryName) = lower(:categoryName) and c.id <> :id")
    boolean existsByCompanyAndCategoryNameIgnoreCaseAndIdNot(@Param("company") Company company, @Param("categoryName") String categoryName, @Param("id") Long id);
}
