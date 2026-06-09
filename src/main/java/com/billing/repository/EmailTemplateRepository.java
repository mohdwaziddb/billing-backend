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

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {
    Optional<EmailTemplate> findByIdAndCompany(Long id, Company company);
    Optional<EmailTemplate> findByIdAndCompanyAndActiveTrue(Long id, Company company);
    List<EmailTemplate> findByCompanyAndActiveTrueOrderByTemplateNameAsc(Company company);
    boolean existsByCompanyAndTemplateNameIgnoreCaseAndIdNot(Company company, String templateName, Long id);
    boolean existsByCompanyAndTemplateNameIgnoreCase(Company company, String templateName);

    @Query("""
            SELECT t
            FROM EmailTemplate t
            WHERE t.company = :company
              AND (:active IS NULL OR t.active = :active)
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
