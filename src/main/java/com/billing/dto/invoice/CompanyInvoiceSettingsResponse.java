package com.billing.dto.invoice;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompanyInvoiceSettingsResponse {
    private String defaultTemplateId;
    private boolean showWatermark;
    private String watermarkText;
    private boolean showSignature;
    private String signatureLabel;
    private String signatureHeading;
    private boolean showQr;
    private boolean showBankDetails;
    private boolean showTerms;
    private boolean showNotes;
    private String noteText;
    private String termsText;
    private String footerCredit;
}
