package com.billing.saas.entity;

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
@Table(name = "customers")
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class Customer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String mobile;

    private String email;

    private String address;

    private String gstNo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal openingBalance;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal currentBalance;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal creditLimit;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
