package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.Product;
import com.billing.entity.PurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {
    List<PurchaseItem> findByCompanyAndProductOrderByCreatedAtAsc(Company company, Product product);
}
