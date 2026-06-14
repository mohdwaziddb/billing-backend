package com.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment_modes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_mode_company_code", columnNames = {"company_id", "mode_code"}),
        @UniqueConstraint(name = "uk_payment_mode_company_name", columnNames = {"company_id", "mode_name"})
})
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class PaymentModeMaster extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mode_name", nullable = false)
    private String modeName;

    @Column(name = "name", nullable = false)
    private String legacyName;

    @Column(name = "mode_code", nullable = false, length = 80)
    private String modeCode;

    @Column(length = 1000)
    private String description;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @PrePersist
    @PreUpdate
    private void syncNameColumnsBeforeSave() {
        if (modeName == null || modeName.isBlank()) {
            modeName = legacyName;
        }
        legacyName = modeName;
    }

    @PostLoad
    private void syncNameColumnsAfterLoad() {
        if ((modeName == null || modeName.isBlank()) && legacyName != null) {
            modeName = legacyName;
        }
        if ((legacyName == null || legacyName.isBlank()) && modeName != null) {
            legacyName = modeName;
        }
    }
}
