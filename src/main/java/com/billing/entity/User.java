package com.billing.entity;

import com.billing.entity.enums.RoleName;
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
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_mobile", columnNames = {"mobile_number"}),
        @UniqueConstraint(name = "uk_users_email", columnNames = {"email_id"}),
        @UniqueConstraint(name = "uk_users_username", columnNames = {"username"})
})
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(name = "mobile_number", nullable = false)
    private String mobileNumber;

    @Column(name = "email_id", nullable = false)
    private String email;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleName role;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Transient
    private Company company;

    /**
     * Display-only marker for tenant super-admin sessions. Synthetic users
     * built for SUPER_ADMIN logins set this true; real users always false.
     * Never persisted (@Transient) — only flows into the profile response
     * so the UI can show a "SUPER ADMIN" chip instead of the OWNER role.
     */
    @Transient
    @Builder.Default
    private boolean superAdmin = false;
}
