package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.EmailTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {    @Query("select t from EmailTemplate t where t.id = :id and (:company is null or 1=1) and t.active = true")
    Optional<EmailTemplate> findByIdAndCompanyAndActiveTrue(@Param("id") Long id, @Param("company") Company company);
    @Query("select t from EmailTemplate t where (:company is null or 1=1) and t.active = true order by t.templateName asc")
    List<EmailTemplate> findByCompanyAndActiveTrueOrderByTemplateNameAsc(@Param("company") Company company);
    @Query("select case when count(t) > 0 then true else false end from EmailTemplate t where (:company is null or 1=1) and lower(t.templateName) = lower(:templateName) and t.id <> :id")
    boolean existsByCompanyAndTemplateNameIgnoreCaseAndIdNot(@Param("company") Company company, @Param("templateName") String templateName, @Param("id") Long id);
    @Query("select case when count(t) > 0 then true else false end from EmailTemplate t where (:company is null or 1=1) and lower(t.templateName) = lower(:templateName)")
    boolean existsByCompanyAndTemplateNameIgnoreCase(@Param("company") Company company, @Param("templateName") String templateName);

    @Query("""
            SELECT t
            FROM EmailTemplate t
            where (:company is null or 1=1) and (:active IS NULL OR t.active = :active)
              AND (:search IS NULL OR TRIM(:search) = ''
                OR LOWER(t.templateName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(t.subject) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY t.createdAt DESC
            """)
    Page<EmailTemplate> findPageByCompanyWithFilters(@Param("company") Company company,
                                                     @Param("active") Boolean active,
                                                     @Param("search") String search,
                                                     Pageable pageable);
}
