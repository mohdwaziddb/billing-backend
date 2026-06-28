package com.billing.dto.invoice;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InvoiceRenderResponse {
    private Long invoiceId;
    private String invoiceNo;
    private String templateId;
    private String templateName;
    private String html;
}
