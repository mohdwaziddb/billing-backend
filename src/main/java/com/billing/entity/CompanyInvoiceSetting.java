package com.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
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
@Table(name = "company_invoice_settings")
public class CompanyInvoiceSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @Column(name = "default_template_id", nullable = false)
    private String defaultTemplateId;

    @Builder.Default
    @Column(name = "show_watermark", nullable = false)
    private boolean showWatermark = false;

    @Column(name = "watermark_text")
    private String watermarkText;

    @Builder.Default
    @Column(name = "show_signature", nullable = false)
    private boolean showSignature = true;

    @Column(name = "signature_label")
    private String signatureLabel;

    @Column(name = "signature_heading")
    private String signatureHeading;

    @Builder.Default
    @Column(name = "show_qr", nullable = false)
    private boolean showQr = true;

    @Builder.Default
    @Column(name = "show_bank_details", nullable = false)
    private boolean showBankDetails = true;

    @Builder.Default
    @Column(name = "show_terms", nullable = false)
    private boolean showTerms = true;

    @Builder.Default
    @Column(name = "show_notes", nullable = false)
    private boolean showNotes = true;

    @Column(name = "note_text", columnDefinition = "TEXT")
    private String noteText;

    @Column(name = "terms_text", columnDefinition = "TEXT")
    private String termsText;

    @Column(name = "footer_credit")
    private String footerCredit;
}
