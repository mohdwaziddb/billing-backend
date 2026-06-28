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
        name = "customers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_customer_company_mobile", columnNames = {"company_id", "mobile"}),
                @UniqueConstraint(name = "uk_customer_company_email", columnNames = {"company_id", "email"})
        }
)
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

    private String city;

    private String state;

    private String country;

    private String pincode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state_id")
    private StateMaster stateMaster;

    private String gstNo;

    private String gstin;

    @Builder.Default
    @Column(nullable = false)
    private boolean gstRegistered = false;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal currentBalance;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
