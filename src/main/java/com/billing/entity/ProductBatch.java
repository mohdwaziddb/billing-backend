package com.billing.entity;

import com.billing.entity.enums.ProductBatchSourceType;
import com.billing.entity.enums.ProductBatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product_batches", uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "batch_no"}))
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class ProductBatch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id")
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_item_id")
    private PurchaseItem purchaseItem;

    @Column(nullable = false)
    private String batchNo;

    @Column(nullable = false)
    private LocalDate batchDate;

    @Column(nullable = false)
    private Integer purchaseQty;

    @Column(nullable = false)
    private Integer remainingQty;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal purchaseRate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductBatchStatus batchStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductBatchSourceType sourceType;
}
