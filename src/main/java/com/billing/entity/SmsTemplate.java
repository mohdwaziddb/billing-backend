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
@Table(name = "sms_templates", indexes = {
        @Index(name = "idx_sms_templates_company", columnList = "company_id"),
        @Index(name = "idx_sms_templates_active", columnList = "is_active")
})
public class SmsTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "template_name", nullable = false, length = 150)
    private String templateName;

    @Column(name = "template_body", nullable = false, columnDefinition = "TEXT")
    private String templateBody;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
