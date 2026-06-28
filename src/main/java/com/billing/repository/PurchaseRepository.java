package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.Purchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDate;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Purchase> findByIdAndCompany(Long id, Company company);

    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Purchase> findByCompanyAndPurchaseNo(Company company, String purchaseNo);

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("""
            select p from Purchase p
            where p.id = :id
              and p.company = :company
            """)
    Optional<Purchase> findByIdWithItemsAndCompany(@Param("id") Long id, @Param("company") Company company);

    @Query("""
            select p from Purchase p
            where p.company = :company
              and (:active is null or coalesce(p.active, true) = :active)
              and (:startDate is null or p.purchaseDate >= :startDate)
              and (:endDate is null or p.purchaseDate <= :endDate)
              and (:search is null
                or lower(p.purchaseNo) like lower(concat('%', :search, '%'))
                or lower(coalesce(p.supplierName, '')) like lower(concat('%', :search, '%'))
                or str(p.purchaseDate) like concat('%', :search, '%')
                or cast(function('date_format', p.purchaseDate, '%d-%m-%Y') as string) like concat('%', :search, '%'))
            order by p.purchaseDate desc, p.id desc
            """)
    Page<Purchase> search(@Param("company") Company company,
                          @Param("active") Boolean active,
                          @Param("search") String search,
                          @Param("startDate") LocalDate startDate,
                          @Param("endDate") LocalDate endDate,
                          Pageable pageable);

    @Query("select count(p) from Purchase p where p.company = :company and p.purchaseDate = current_date")
    long countTodayByCompany(@Param("company") Company company);

    List<Purchase> findByCompanyOrderByPurchaseDateDescIdDesc(Company company);
}
