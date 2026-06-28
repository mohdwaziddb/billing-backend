package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.InventoryLedgerEntry;
import com.billing.entity.Product;
import com.billing.entity.Purchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface InventoryLedgerRepository extends JpaRepository<InventoryLedgerEntry, Long> {

    @Query("""
            select l from InventoryLedgerEntry l
            where l.company = :company
              and (:productId is null or l.product.id = :productId)
              and (:startDate is null or l.entryDate >= :startDate)
              and (:endDate is null or l.entryDate <= :endDate)
              and (:search is null
                or lower(l.product.name) like lower(concat('%', :search, '%'))
                or lower(coalesce(l.referenceNo, '')) like lower(concat('%', :search, '%'))
                or lower(coalesce(l.remarks, '')) like lower(concat('%', :search, '%'))
                or str(l.entryDate) like concat('%', :search, '%')
                or cast(function('date_format', l.entryDate, '%d-%m-%Y') as string) like concat('%', :search, '%'))
            order by l.entryDate desc, l.id desc
            """)
    Page<InventoryLedgerEntry> search(@Param("company") Company company,
                                      @Param("productId") Long productId,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate,
                                      @Param("search") String search,
                                      Pageable pageable);

    @Query("""
            select l from InventoryLedgerEntry l
            where l.company = :company
              and l.product = :product
            order by l.entryDate asc, l.id asc
            """)
    java.util.List<InventoryLedgerEntry> findByCompanyAndProductOrderByEntryDateAscIdAsc(@Param("company") Company company, @Param("product") Product product);

    java.util.List<InventoryLedgerEntry> findByCompanyAndPurchaseOrderByEntryDateAscIdAsc(Company company, Purchase purchase);
}
