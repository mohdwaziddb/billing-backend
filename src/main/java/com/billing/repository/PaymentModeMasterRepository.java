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
            where (:company is null or 1=1) and (:active IS NULL OR m.active = :active)
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
            where (:company is null or 1=1) and (:active IS NULL OR m.active = :active)
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

    @Query("select m from PaymentModeMaster m where (:company is null or 1=1) and lower(m.modeCode)=lower(:modeCode)")
    Optional<PaymentModeMaster> findByCompanyAndModeCodeIgnoreCase(@Param("company") Company company, @Param("modeCode") String modeCode);

    @Query("select m from PaymentModeMaster m where (:company is null or 1=1) and lower(m.modeCode)=lower(:modeCode) and m.active=true")
    Optional<PaymentModeMaster> findByCompanyAndModeCodeIgnoreCaseAndActiveTrue(@Param("company") Company company, @Param("modeCode") String modeCode);

    @Query("select case when count(m)>0 then true else false end from PaymentModeMaster m where (:company is null or 1=1) and lower(m.modeCode)=lower(:modeCode)")
    boolean existsByCompanyAndModeCodeIgnoreCase(@Param("company") Company company, @Param("modeCode") String modeCode);

    @Query("select case when count(m)>0 then true else false end from PaymentModeMaster m where (:company is null or 1=1) and lower(m.modeName)=lower(:modeName)")
    boolean existsByCompanyAndModeNameIgnoreCase(@Param("company") Company company, @Param("modeName") String modeName);

    @Query("select case when count(m)>0 then true else false end from PaymentModeMaster m where (:company is null or 1=1) and lower(m.modeCode)=lower(:modeCode) and m.id <> :id")
    boolean existsByCompanyAndModeCodeIgnoreCaseAndIdNot(@Param("company") Company company, @Param("modeCode") String modeCode, @Param("id") Long id);

    @Query("select case when count(m)>0 then true else false end from PaymentModeMaster m where (:company is null or 1=1) and lower(m.modeName)=lower(:modeName) and m.id <> :id")
    boolean existsByCompanyAndModeNameIgnoreCaseAndIdNot(@Param("company") Company company, @Param("modeName") String modeName, @Param("id") Long id);
}
