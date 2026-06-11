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
@Table(name = "notification_logs", indexes = {
        @Index(name = "idx_notification_logs_company", columnList = "company_id"),
        @Index(name = "idx_notification_logs_channel", columnList = "channel"),
        @Index(name = "idx_notification_logs_status", columnList = "status"),
        @Index(name = "idx_notification_logs_sent_at", columnList = "sent_at")
})
public class NotificationLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 30)
    private String channel;

    @Column(name = "template_id")
    private Long templateId;

    @Column(nullable = false)
    private String recipient;

    private String subject;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "provider_response", columnDefinition = "TEXT")
    private String providerResponse;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "sent_by")
    private String sentBy;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}
