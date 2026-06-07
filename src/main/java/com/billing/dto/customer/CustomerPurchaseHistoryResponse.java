package com.billing.dto.customer;

import com.billing.dto.invoice.InvoiceResponse;
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
    private int page;
    private int size;
    private long totalRecords;
    private int totalPages;
}
