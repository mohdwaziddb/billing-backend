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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "email_provider_settings", indexes = @Index(name = "idx_email_provider_company", columnList = "company_id"))
public class EmailProviderSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Column(name = "sender_email", nullable = false)
    private String senderEmail;

    @Column(name = "smtp_host")
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_username")
    private String smtpUsername;

    @Column(name = "smtp_password")
    private String smtpPassword;

    @Column(name = "smtp_tls_enabled")
    private boolean smtpTlsEnabled;

    @Column(name = "aws_access_key")
    private String awsAccessKey;

    @Column(name = "aws_secret_key")
    private String awsSecretKey;

    @Column(name = "aws_region")
    private String awsRegion;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
