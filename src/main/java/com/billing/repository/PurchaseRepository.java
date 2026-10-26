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

    // DataPort opening stock duplicate guard: active purchases ki lines ek query me
    // [purchaseDate, supplierName, productId, qty, purchaseRate, purchaseNo] (pehle har purchase ke items lazy = N+1).
    @Query("""
            select p.purchaseDate, p.supplierName, item.product.id, item.qty, item.purchaseRate, p.purchaseNo
            from PurchaseItem item join item.purchase p
            where p.active = true
            order by p.purchaseDate desc, p.id desc, item.id asc
            """)
    List<Object[]> findActivePurchaseLineKeys();

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("select p from Purchase p where (:company is null or 1=1) and p.purchaseNo = :purchaseNo")
    Optional<Purchase> findByCompanyAndPurchaseNo(@Param("company") Company company, @Param("purchaseNo") String purchaseNo);

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("""
            select p from Purchase p
            where p.id = :id
              and (:company is null or 1=1)
            """)
    Optional<Purchase> findByIdWithItemsAndCompany(@Param("id") Long id, @Param("company") Company company);

    @Query("""
            select p from Purchase p
            where (:company is null or 1=1) and (:active is null or coalesce(p.active, true) = :active)
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

    // Purchases page: ek page ki saari purchases ke items + product ek hi query me.
    // Pehle har row par items (lazy) + har item ka product alag query = N+1.
    @Query("""
            select p from Purchase p
            left join fetch p.items item
            left join fetch item.product
            where p.id in :purchaseIds
            order by p.id asc, item.id asc
            """)
    List<Purchase> findAllWithItemsByIdIn(@Param("purchaseIds") java.util.Collection<Long> purchaseIds);

    // Number-length pehle, taaki 99999 ke baad 100000 bhi "max" mile.
    @Query("select p.purchaseNo from Purchase p where p.purchaseNo like concat(:prefix, '%') order by length(p.purchaseNo) desc, p.purchaseNo desc")
    List<String> findLatestNumbersWithPrefix(@Param("prefix") String prefix, Pageable pageable);

    @Query("select p from Purchase p where (:company is null or 1=1) order by p.purchaseDate desc, p.id desc")
    List<Purchase> findByCompanyOrderByPurchaseDateDescIdDesc(@Param("company") Company company);
}
