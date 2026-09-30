package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.Product;
import com.billing.entity.PurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {
    @Query("select e from PurchaseItem e where (:company is null or 1=1) and e.product = :product order by e.createdAt asc")
    List<PurchaseItem> findByCompanyAndProductOrderByCreatedAtAsc(@Param("company") Company company, @Param("product") Product product);
}
