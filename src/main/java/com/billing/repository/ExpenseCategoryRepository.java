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

    List<ExpenseCategory> findByCompanyOrderByCategoryNameAsc(Company company);

    @Query("""
            select c from ExpenseCategory c
            where c.company = :company
              and (:active is null or c.active = :active)
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
            where c.company = :company
              and (:active is null or c.active = :active)
              and (:search is null
                or lower(c.categoryName) like lower(concat('%', :search, '%'))
                or lower(coalesce(c.description, '')) like lower(concat('%', :search, '%')))
            order by c.categoryName asc
            """)
    List<ExpenseCategory> findAllByCompanyWithFilters(@Param("company") Company company,
                                                      @Param("active") Boolean active,
                                                      @Param("search") String search);

    Optional<ExpenseCategory> findByIdAndCompany(Long id, Company company);

    Optional<ExpenseCategory> findByCompanyAndCategoryNameIgnoreCase(Company company, String categoryName);

    Optional<ExpenseCategory> findByCompanyAndCategoryNameIgnoreCaseAndActiveTrue(Company company, String categoryName);

    boolean existsByCompanyAndCategoryNameIgnoreCase(Company company, String categoryName);

    boolean existsByCompanyAndCategoryNameIgnoreCaseAndIdNot(Company company, String categoryName, Long id);
}
