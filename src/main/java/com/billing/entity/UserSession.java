package com.billing.entity;

import com.billing.entity.enums.UserSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Ek login = ek row (ek device / browser). Refresh token har ghante badalta hai,
 * par session wahi rehta hai, isliye "ye wahi phone hai" pata rehta hai.
 * Access token me {@code sid} claim = {@link #sessionKey}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_sessions", indexes = {
        @Index(name = "ux_user_sessions_key", columnList = "session_key", unique = true),
        @Index(name = "ix_user_sessions_user_status", columnList = "user_id,status")
})
public class UserSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "session_key", nullable = false, length = 64)
    private String sessionKey;

    @Column(length = 120)
    private String deviceName;

    @Column(length = 20)
    private String deviceType;

    @Column(length = 60)
    private String browser;

    @Column(length = 60)
    private String os;

    @Column(length = 64)
    private String ipAddress;

    @Column(length = 512)
    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime loginAt;

    @Column(nullable = false)
    private LocalDateTime lastActiveAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserSessionStatus status;

    private LocalDateTime endedAt;

    @Column(length = 160)
    private String endedReason;
}
