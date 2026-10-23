package com.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_mfa_devices", indexes = {
        @Index(name = "idx_mfa_user", columnList = "user_id"),
        @Index(name = "idx_mfa_user_enabled", columnList = "user_id,enabled")
})
public class UserMfaDevice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_name", nullable = false, length = 50)
    private String deviceName;

    @Column(name = "secret_encrypted", nullable = false, columnDefinition = "TEXT")
    private String secretEncrypted;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = false;

    @Builder.Default
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "enrolled_at")
    private LocalDateTime enrolledAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    // Last accepted TOTP time-step: same/purana step dobara accept nahi (code replay).
    @Column(name = "last_used_step")
    private Long lastUsedStep;
}
