package com.billing.saas.dto.customer;

import com.billing.saas.dto.invoice.InvoiceResponse;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CustomerPurchaseHistoryResponse {
    private Long customerId;
    private String customerName;
    private String mobile;
    private String address;
    private CustomerSummaryMetrics summary;
    private List<InvoiceResponse> invoices;
}
