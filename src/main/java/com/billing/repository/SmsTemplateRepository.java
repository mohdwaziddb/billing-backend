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
    Optional<SmsTemplate> findByIdAndCompany(Long id, Company company);
    Optional<SmsTemplate> findByIdAndCompanyAndActiveTrue(Long id, Company company);
    List<SmsTemplate> findByCompanyAndActiveTrueOrderByTemplateNameAsc(Company company);
    boolean existsByCompanyAndTemplateNameIgnoreCase(Company company, String templateName);
    boolean existsByCompanyAndTemplateNameIgnoreCaseAndIdNot(Company company, String templateName, Long id);

    @Query("""
            SELECT t FROM SmsTemplate t
            WHERE t.company = :company
              AND (:active IS NULL OR t.active = :active)
              AND (:search IS NULL OR LOWER(t.templateName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(t.templateBody) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY t.createdAt DESC
            """)
    Page<SmsTemplate> findPageByCompanyWithFilters(@Param("company") Company company,
                                                   @Param("active") Boolean active,
                                                   @Param("search") String search,
                                                   Pageable pageable);
}
