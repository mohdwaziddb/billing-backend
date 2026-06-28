package com.billing.entity;

import com.billing.entity.enums.TaxType;
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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "tax_master",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tax_master_company_name", columnNames = {"company_id", "tax_name"}),
                @UniqueConstraint(name = "uk_tax_master_company_code", columnNames = {"company_id", "tax_code"})
        }
)
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class TaxMaster extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String taxName;

    @Column(nullable = false)
    private String taxCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaxType taxType;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal rate;

    private String description;

    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private boolean defaultTax = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;
}
