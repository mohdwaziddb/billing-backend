package com.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "invoice_items")
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class InvoiceItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_master_id")
    private TaxMaster taxMaster;

    @Column(nullable = false)
    private Integer qty;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal taxPercent;

    private String taxName;

    @Column(precision = 5, scale = 2)
    private BigDecimal taxRate;

    private String hsnCode;

    @Column(precision = 12, scale = 2)
    private BigDecimal taxableAmount;

    @Column(precision = 5, scale = 2)
    private BigDecimal cgstRate;

    @Column(precision = 12, scale = 2)
    private BigDecimal cgstAmount;

    @Column(precision = 5, scale = 2)
    private BigDecimal sgstRate;

    @Column(precision = 12, scale = 2)
    private BigDecimal sgstAmount;

    @Column(precision = 5, scale = 2)
    private BigDecimal igstRate;

    @Column(precision = 12, scale = 2)
    private BigDecimal igstAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal netAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal grandAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;
}
