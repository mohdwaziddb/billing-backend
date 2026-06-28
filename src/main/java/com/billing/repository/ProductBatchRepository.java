package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.Product;
import com.billing.entity.ProductBatch;
import com.billing.entity.Purchase;
import com.billing.entity.enums.ProductBatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductBatchRepository extends JpaRepository<ProductBatch, Long> {

    @Query("""
            select b from ProductBatch b
            where b.company = :company
              and b.product = :product
            order by b.batchDate asc, b.id asc
            """)
    List<ProductBatch> findByCompanyAndProductOrderByBatchDateAscIdAsc(@Param("company") Company company, @Param("product") Product product);

    @Query("""
            select b from ProductBatch b
            where b.company = :company
              and b.product = :product
            order by b.batchDate desc, b.id desc
            """)
    List<ProductBatch> findByCompanyAndProductOrderByBatchDateDescIdDesc(@Param("company") Company company, @Param("product") Product product);

    Optional<ProductBatch> findTopByCompanyAndProductOrderByBatchDateDescIdDesc(Company company, Product product);

    long countByCompany(Company company);

    @Query("""
            select coalesce(sum(b.remainingQty), 0)
            from ProductBatch b
            where b.company = :company
              and b.product = :product
            """)
    Integer sumRemainingQty(@Param("company") Company company, @Param("product") Product product);

    @Query("""
            select b from ProductBatch b
            where b.company = :company
              and b.product in :products
              and b.batchStatus = :status
            order by b.product.id asc, b.batchDate asc, b.id asc
            """)
    List<ProductBatch> findByCompanyAndProductInAndBatchStatusOrderByProductIdAscBatchDateAscIdAsc(@Param("company") Company company,
                                                                                                     @Param("products") Collection<Product> products,
                                                                                                     @Param("status") ProductBatchStatus status);

    @Query("""
            select b from ProductBatch b
            where b.company = :company
              and b.product in :products
            order by b.product.id asc, b.batchDate asc, b.id asc
            """)
    List<ProductBatch> findByCompanyAndProductInOrderByProductIdAscBatchDateAscIdAsc(@Param("company") Company company,
                                                                                      @Param("products") Collection<Product> products);

    List<ProductBatch> findByCompanyAndPurchaseOrderByBatchDateAscIdAsc(Company company, Purchase purchase);
}
