package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.PaymentModeMaster;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentModeMasterRepository extends JpaRepository<PaymentModeMaster, Long> {

    @Query("""
            SELECT m
            FROM PaymentModeMaster m
            WHERE m.company = :company
              AND (:active IS NULL OR m.active = :active)
              AND (
                    :search IS NULL
                    OR TRIM(:search) = ''
                    OR LOWER(m.modeName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(m.modeCode) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(m.description) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            ORDER BY m.modeName ASC
            """)
    List<PaymentModeMaster> findAllByCompanyWithFilters(@Param("company") Company company,
                                                        @Param("active") Boolean active,
                                                        @Param("search") String search);

    @Query("""
            SELECT m
            FROM PaymentModeMaster m
            WHERE m.company = :company
              AND (:active IS NULL OR m.active = :active)
              AND (
                    :search IS NULL
                    OR TRIM(:search) = ''
                    OR LOWER(m.modeName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(m.modeCode) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(m.description) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            ORDER BY m.modeName ASC
            """)
    Page<PaymentModeMaster> findPageByCompanyWithFilters(@Param("company") Company company,
                                                         @Param("active") Boolean active,
                                                         @Param("search") String search,
                                                         Pageable pageable);

    Optional<PaymentModeMaster> findByIdAndCompany(Long id, Company company);

    Optional<PaymentModeMaster> findByCompanyAndModeCodeIgnoreCase(Company company, String modeCode);

    Optional<PaymentModeMaster> findByCompanyAndModeCodeIgnoreCaseAndActiveTrue(Company company, String modeCode);

    boolean existsByCompanyAndModeCodeIgnoreCase(Company company, String modeCode);

    boolean existsByCompanyAndModeNameIgnoreCase(Company company, String modeName);

    boolean existsByCompanyAndModeCodeIgnoreCaseAndIdNot(Company company, String modeCode, Long id);

    boolean existsByCompanyAndModeNameIgnoreCaseAndIdNot(Company company, String modeName, Long id);
}
