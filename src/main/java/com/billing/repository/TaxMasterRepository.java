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
            where (:company is null or 1=1) and t.deleted = false
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
            where (:company is null or 1=1) and t.deleted = false
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

    @Query("select t from TaxMaster t where t.id = :id and (:company is null or 1=1) and t.deleted=false")
    Optional<TaxMaster> findByIdAndCompanyAndDeletedFalse(@Param("id") Long id, @Param("company") Company company);

    @Query("select t from TaxMaster t where (:company is null or 1=1) and lower(t.taxName)=lower(:taxName) and t.deleted=false")
    Optional<TaxMaster> findByCompanyAndTaxNameIgnoreCaseAndDeletedFalse(@Param("company") Company company, @Param("taxName") String taxName);

    @Query("select t from TaxMaster t where (:company is null or 1=1) and lower(t.taxCode)=lower(:taxCode) and t.deleted=false")
    Optional<TaxMaster> findByCompanyAndTaxCodeIgnoreCaseAndDeletedFalse(@Param("company") Company company, @Param("taxCode") String taxCode);

    @Query("select case when count(t)>0 then true else false end from TaxMaster t where (:company is null or 1=1) and lower(t.taxName)=lower(:taxName) and t.deleted=false")
    boolean existsByCompanyAndTaxNameIgnoreCaseAndDeletedFalse(@Param("company") Company company, @Param("taxName") String taxName);

    @Query("select case when count(t)>0 then true else false end from TaxMaster t where (:company is null or 1=1) and lower(t.taxCode)=lower(:taxCode) and t.deleted=false")
    boolean existsByCompanyAndTaxCodeIgnoreCaseAndDeletedFalse(@Param("company") Company company, @Param("taxCode") String taxCode);

    @Query("select case when count(t)>0 then true else false end from TaxMaster t where (:company is null or 1=1) and lower(t.taxName)=lower(:taxName) and t.deleted=false and t.id <> :id")
    boolean existsByCompanyAndTaxNameIgnoreCaseAndDeletedFalseAndIdNot(@Param("company") Company company, @Param("taxName") String taxName, @Param("id") Long id);

    @Query("select case when count(t)>0 then true else false end from TaxMaster t where (:company is null or 1=1) and lower(t.taxCode)=lower(:taxCode) and t.deleted=false and t.id <> :id")
    boolean existsByCompanyAndTaxCodeIgnoreCaseAndDeletedFalseAndIdNot(@Param("company") Company company, @Param("taxCode") String taxCode, @Param("id") Long id);

    @Query("select t from TaxMaster t where (:company is null or 1=1) and t.taxType = :taxType and t.rate = :rate and t.deleted=false")
    Optional<TaxMaster> findByCompanyAndTaxTypeAndRateAndDeletedFalse(@Param("company") Company company, @Param("taxType") TaxType taxType, @Param("rate") BigDecimal rate);

    @Query("select t from TaxMaster t where (:company is null or 1=1) and t.defaultTax=true and t.deleted=false")
    Optional<TaxMaster> findByCompanyAndDefaultTaxTrueAndDeletedFalse(@Param("company") Company company);

    @Query("select t from TaxMaster t where (:company is null or 1=1) and t.deleted=false order by t.taxName asc")
    List<TaxMaster> findByCompanyAndDeletedFalseOrderByTaxNameAsc(@Param("company") Company company);
}
