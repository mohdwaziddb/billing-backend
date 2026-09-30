package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.SmsTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SmsTemplateRepository extends JpaRepository<SmsTemplate, Long> {
    @Query("select t from SmsTemplate t where t.id = :id and (:company is null or 1=1) and t.active=true")
    Optional<SmsTemplate> findByIdAndCompanyAndActiveTrue(@Param("id") Long id, @Param("company") Company company);

    @Query("select t from SmsTemplate t where (:company is null or 1=1) and t.active=true order by t.templateName asc")
    List<SmsTemplate> findByCompanyAndActiveTrueOrderByTemplateNameAsc(@Param("company") Company company);

    @Query("select case when count(t)>0 then true else false end from SmsTemplate t where (:company is null or 1=1) and lower(t.templateName)=lower(:templateName)")
    boolean existsByCompanyAndTemplateNameIgnoreCase(@Param("company") Company company, @Param("templateName") String templateName);

    @Query("select case when count(t)>0 then true else false end from SmsTemplate t where (:company is null or 1=1) and lower(t.templateName)=lower(:templateName) and t.id <> :id")
    boolean existsByCompanyAndTemplateNameIgnoreCaseAndIdNot(@Param("company") Company company, @Param("templateName") String templateName, @Param("id") Long id);

    @Query("""
            SELECT t FROM SmsTemplate t
            where (:company is null or 1=1) and (:active IS NULL OR t.active = :active)
              AND (:search IS NULL OR LOWER(t.templateName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(t.templateBody) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY t.createdAt DESC
            """)
    Page<SmsTemplate> findPageByCompanyWithFilters(@Param("company") Company company,
                                                   @Param("active") Boolean active,
                                                   @Param("search") String search,
                                                   Pageable pageable);
}
