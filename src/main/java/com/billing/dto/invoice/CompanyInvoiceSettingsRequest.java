package com.billing.dto.invoice;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyInvoiceSettingsRequest {

    @NotBlank
    private String defaultTemplateId;

    private Boolean showWatermark;

    private String watermarkText;

    private Boolean showSignature;

    private String signatureLabel;

    private String signatureHeading;

    private Boolean showQr;

    private Boolean showBankDetails;

    private Boolean showTerms;

    private Boolean showNotes;

    private String noteText;

    private String termsText;

    private String footerCredit;
}
