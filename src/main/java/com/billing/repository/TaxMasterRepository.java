package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.TaxMaster;
import com.billing.entity.enums.TaxType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TaxMasterRepository extends JpaRepository<TaxMaster, Long> {

    @Query("""
            SELECT t
            FROM TaxMaster t
            WHERE t.company = :company
              AND t.deleted = false
              AND (:active IS NULL OR t.active = :active)
              AND (:taxType IS NULL OR t.taxType = :taxType)
              AND (
                    :search IS NULL
                    OR TRIM(:search) = ''
                    OR LOWER(t.taxName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(t.taxCode) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            ORDER BY t.taxName ASC
            """)
    Page<TaxMaster> findPageByCompanyWithFilters(@Param("company") Company company,
                                                 @Param("active") Boolean active,
                                                 @Param("taxType") TaxType taxType,
                                                 @Param("search") String search,
                                                 Pageable pageable);

    @Query("""
            SELECT t
            FROM TaxMaster t
            WHERE t.company = :company
              AND t.deleted = false
              AND (:active IS NULL OR t.active = :active)
              AND (:taxType IS NULL OR t.taxType = :taxType)
              AND (
                    :search IS NULL
                    OR TRIM(:search) = ''
                    OR LOWER(t.taxName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(t.taxCode) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            ORDER BY t.taxName ASC
            """)
    List<TaxMaster> findAllByCompanyWithFilters(@Param("company") Company company,
                                                @Param("active") Boolean active,
                                                @Param("taxType") TaxType taxType,
                                                @Param("search") String search);

    Optional<TaxMaster> findByIdAndCompanyAndDeletedFalse(Long id, Company company);
    Optional<TaxMaster> findByCompanyAndTaxNameIgnoreCaseAndDeletedFalse(Company company, String taxName);
    Optional<TaxMaster> findByCompanyAndTaxCodeIgnoreCaseAndDeletedFalse(Company company, String taxCode);
    boolean existsByCompanyAndTaxNameIgnoreCaseAndDeletedFalse(Company company, String taxName);
    boolean existsByCompanyAndTaxCodeIgnoreCaseAndDeletedFalse(Company company, String taxCode);
    boolean existsByCompanyAndTaxNameIgnoreCaseAndDeletedFalseAndIdNot(Company company, String taxName, Long id);
    boolean existsByCompanyAndTaxCodeIgnoreCaseAndDeletedFalseAndIdNot(Company company, String taxCode, Long id);
    Optional<TaxMaster> findByCompanyAndTaxTypeAndRateAndDeletedFalse(Company company, TaxType taxType, BigDecimal rate);
    Optional<TaxMaster> findByCompanyAndDefaultTaxTrueAndDeletedFalse(Company company);
    List<TaxMaster> findByCompanyAndDeletedFalseOrderByTaxNameAsc(Company company);
}
