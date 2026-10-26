package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.Product;
import com.billing.entity.ProductBatch;
import com.billing.entity.Purchase;
import com.billing.entity.enums.ProductBatchStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductBatchRepository extends JpaRepository<ProductBatch, Long> {

    // Saare products ka stock ek GROUP BY query me [productId, remainingQty] (DataPort invoice,
    // Analytics low-stock) — pehle har product par alag sumRemainingQty query = N+1.
    @Query("select b.product.id, coalesce(sum(b.remainingQty), 0) from ProductBatch b group by b.product.id")
    List<Object[]> sumRemainingQtyGroupByProduct();

    @Query("""
            select b from ProductBatch b
            where (:company is null or 1=1) and b.product = :product
            order by b.batchDate asc, b.id asc
            """)
    List<ProductBatch> findByCompanyAndProductOrderByBatchDateAscIdAsc(@Param("company") Company company, @Param("product") Product product);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from ProductBatch b
            where (:company is null or 1=1) and b.product = :product
            order by b.batchDate asc, b.id asc
            """)
    List<ProductBatch> lockByCompanyAndProductOrderByBatchDateAscIdAsc(@Param("company") Company company, @Param("product") Product product);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from ProductBatch b
            where (:company is null or 1=1) and b.product = :product
            order by b.batchDate desc, b.id desc
            """)
    List<ProductBatch> lockByCompanyAndProductOrderByBatchDateDescIdDesc(@Param("company") Company company, @Param("product") Product product);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ProductBatch b where b.id = :id")
    Optional<ProductBatch> lockById(@Param("id") Long id);

    @Query("""
            select b from ProductBatch b
            where (:company is null or 1=1) and b.product = :product
            order by b.batchDate desc, b.id desc
            """)
    List<ProductBatch> findByCompanyAndProductOrderByBatchDateDescIdDesc(@Param("company") Company company, @Param("product") Product product);

    @Query("""
            select b from ProductBatch b
            where (:company is null or 1=1) and b.product = :product
            order by b.batchDate desc, b.id desc
            """)
    Optional<ProductBatch> findTopByCompanyAndProductOrderByBatchDateDescIdDesc(@Param("company") Company company, @Param("product") Product product);

    @Query("""
            select coalesce(sum(b.remainingQty), 0)
            from ProductBatch b
            where (:company is null or 1=1) and b.product = :product
            """)
    Integer sumRemainingQty(@Param("company") Company company, @Param("product") Product product);


    @Query("""
            select b from ProductBatch b
            where (:company is null or 1=1) and b.product in :products
              and b.batchStatus = :status
            order by b.product.id asc, b.batchDate asc, b.id asc
            """)
    List<ProductBatch> findByCompanyAndProductInAndBatchStatusOrderByProductIdAscBatchDateAscIdAsc(@Param("company") Company company,
                                                                                                     @Param("products") Collection<Product> products,
                                                                                                     @Param("status") ProductBatchStatus status);

    @Query("""
            select b from ProductBatch b
            where (:company is null or 1=1) and b.product in :products
            order by b.product.id asc, b.batchDate asc, b.id asc
            """)
    List<ProductBatch> findByCompanyAndProductInOrderByProductIdAscBatchDateAscIdAsc(@Param("company") Company company,
                                                                                      @Param("products") Collection<Product> products);

    @Query("""
            select b from ProductBatch b
            where (:company is null or 1=1) and b.purchase = :purchase
            order by b.batchDate asc, b.id asc
            """)
    List<ProductBatch> findByCompanyAndPurchaseOrderByBatchDateAscIdAsc(@Param("company") Company company, @Param("purchase") Purchase purchase);

    // Product list / dropdown (size=1000) ke stock summary ke liye sirf zaroori columns.
    // Pehle poori ProductBatch entities load hoti thi (hydration + persistence context) —
    // hazaron batches par list API slow. Order wahi hai jo summarize() ki FIFO/LIFO logic maangti hai.
    @Query("""
            select b.product.id as productId, b.id as batchId, b.batchDate as batchDate,
                   b.remainingQty as remainingQty, b.purchaseRate as purchaseRate, b.sellingRate as sellingRate
            from ProductBatch b
            where b.product.id in :productIds
            order by b.product.id asc, b.batchDate asc, b.id asc
            """)
    List<ProductBatchStockRow> findStockRowsByProductIdIn(@Param("productIds") Collection<Long> productIds);

    // Product detail: batch history me purchaseNo dikhta hai — purchase saath me fetch,
    // warna har batch par alag purchase query (N+1) chalti thi.
    @Query("""
            select b from ProductBatch b
            left join fetch b.purchase
            where b.product.id in :productIds
            order by b.product.id asc, b.batchDate asc, b.id asc
            """)
    List<ProductBatch> findWithPurchaseByProductIdIn(@Param("productIds") Collection<Long> productIds);

    interface ProductBatchStockRow {
        Long getProductId();
        Long getBatchId();
        java.time.LocalDate getBatchDate();
        Integer getRemainingQty();
        java.math.BigDecimal getPurchaseRate();
        java.math.BigDecimal getSellingRate();
    }
}
