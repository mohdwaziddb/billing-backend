package com.billing.entity;

import com.billing.entity.enums.PaymentGatewayType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Company ka payment gateway config (har gateway ki ek row). Gateway-specific
 * keys provider_config JSON me rehti hain; secret fields encrypted.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment_gateway_settings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_gateway_type", columnNames = {"gateway_type"})
})
public class PaymentGatewaySetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Transient
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway_type", nullable = false, length = 40)
    private PaymentGatewayType gatewayType;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "provider_config", columnDefinition = "LONGTEXT")
    private String providerConfig;

    @Builder.Default
    @Column(name = "test_mode", nullable = false)
    private boolean testMode = true;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = false;

    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private boolean defaultGateway = false;
}
