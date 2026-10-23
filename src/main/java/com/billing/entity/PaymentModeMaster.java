package com.billing.entity;

import com.billing.entity.enums.PaymentModeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment_modes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_mode_code", columnNames = {"mode_code"}),
        @UniqueConstraint(name = "uk_payment_mode_name", columnNames = {"mode_name"})
})
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

    // null (purani rows) = OFFLINE. ONLINE mode sirf gateway flow se record hota hai.
    @Enumerated(EnumType.STRING)
    @Column(name = "mode_type", length = 20)
    private PaymentModeType modeType;

    // true ho to form me optional "Reference No" (UTR / approval no.) field dikhta hai.
    @Builder.Default
    @Column(name = "requires_reference", nullable = false)
    private boolean requiresReference = false;

    @Transient
    private Company company;

    public PaymentModeType effectiveModeType() {
        return modeType == null ? PaymentModeType.OFFLINE : modeType;
    }

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
