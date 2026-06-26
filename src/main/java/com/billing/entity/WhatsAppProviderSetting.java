package com.billing.entity;

import com.billing.entity.enums.WhatsAppProviderType;
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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "whatsapp_provider_settings", indexes = @Index(name = "idx_whatsapp_provider_company", columnList = "company_id"))
public class WhatsAppProviderSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    private WhatsAppProviderType providerType;

    @Column(name = "auth_key")
    private String authKey;

    @Column(name = "whatsapp_number", nullable = false)
    private String whatsappNumber;

    @Column(name = "sender_name")
    private String senderName;

    @Column(name = "api_url", nullable = false, columnDefinition = "TEXT")
    private String apiUrl;

    @Column(name = "provider_config", columnDefinition = "LONGTEXT")
    private String providerConfig;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
