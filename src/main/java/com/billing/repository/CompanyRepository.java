package com.billing.repository;

import com.billing.dto.platformadmin.PlatformAdminCompanyOverviewView;
import com.billing.dto.platformadmin.PlatformAdminCompanySummaryView;
import com.billing.entity.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    List<Company> findAll();
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByTaxIdIgnoreCase(String taxId);
    boolean existsByCodeIgnoreCase(String code);

    @Query(value = """
            select
                c.id as id,
                c.name as name,
                group_concat(case when u.role = 'OWNER' then u.full_name end order by u.full_name separator ', ') as ownerName,
                c.email as email,
                c.phone as mobile,
                c.is_active as active,
                c.created_at as createdAt,
                coalesce(sum(case when u.role = 'OWNER' then 1 else 0 end), 0) as ownerCount,
                coalesce(sum(case when u.role = 'ADMIN' then 1 else 0 end), 0) as adminCount,
                coalesce(sum(case when u.role = 'USER' then 1 else 0 end), 0) as userCount
            from companies c
            left join users u on u.company_id = c.id
            where (:active is null or c.is_active = :active)
              and (:search is null
                or lower(c.name) like lower(concat('%', :search, '%'))
                or lower(c.email) like lower(concat('%', :search, '%'))
                or lower(coalesce(c.phone, '')) like lower(concat('%', :search, '%'))
                or lower(coalesce(c.tax_id, '')) like lower(concat('%', :search, '%'))
                or lower(c.code) like lower(concat('%', :search, '%')))
            group by c.id, c.name, c.email, c.phone, c.is_active, c.created_at
            order by c.created_at desc, c.id desc
            """,
            countQuery = """
            select count(*)
            from companies c
            where (:active is null or c.is_active = :active)
              and (:search is null
                or lower(c.name) like lower(concat('%', :search, '%'))
                or lower(c.email) like lower(concat('%', :search, '%'))
                or lower(coalesce(c.phone, '')) like lower(concat('%', :search, '%'))
                or lower(coalesce(c.tax_id, '')) like lower(concat('%', :search, '%'))
                or lower(c.code) like lower(concat('%', :search, '%')))
            """,
            nativeQuery = true)
    Page<PlatformAdminCompanySummaryView> searchPlatformAdminCompanies(@Param("search") String search,
                                                                       @Param("active") Boolean active,
                                                                       Pageable pageable);

    @Query(value = """
            select
                count(c.id) as companyCount,
                coalesce(sum(case when u.role = 'OWNER' then 1 else 0 end), 0) as ownerCount,
                coalesce(sum(case when u.role = 'ADMIN' then 1 else 0 end), 0) as adminCount,
                coalesce(sum(case when u.role = 'USER' then 1 else 0 end), 0) as userCount
            from companies c
            left join users u on u.company_id = c.id
            where (:active is null or c.is_active = :active)
              and (:search is null
                or lower(c.name) like lower(concat('%', :search, '%'))
                or lower(c.email) like lower(concat('%', :search, '%'))
                or lower(coalesce(c.phone, '')) like lower(concat('%', :search, '%'))
                or lower(coalesce(c.tax_id, '')) like lower(concat('%', :search, '%'))
                or lower(c.code) like lower(concat('%', :search, '%')))
            """,
            nativeQuery = true)
    PlatformAdminCompanyOverviewView getPlatformAdminCompanyOverview(@Param("search") String search,
                                                                     @Param("active") Boolean active);
}
