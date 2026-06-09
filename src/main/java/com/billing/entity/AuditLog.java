package com.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_company", columnList = "company_id"),
        @Index(name = "idx_audit_entity", columnList = "entity_id"),
        @Index(name = "idx_audit_module", columnList = "module_name"),
        @Index(name = "idx_audit_created_at", columnList = "created_at"),
        @Index(name = "idx_audit_user", columnList = "user_id")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String moduleName;

    @Column(nullable = false)
    private String entityName;

    @Column(nullable = false)
    private Long entityId;

    @Column(nullable = false)
    private String actionType;

    @Column(columnDefinition = "json")
    private String oldData;

    @Column(columnDefinition = "json")
    private String newData;

    @Column(columnDefinition = "json")
    private String changedFields;

    private Long userId;

    private String userName;

    private String ipAddress;

    @Column(length = 1024)
    private String userAgent;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
