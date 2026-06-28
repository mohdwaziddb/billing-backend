package com.billing.dto.gst;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class GstReportResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private long totalInvoices;
    private BigDecimal taxableAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal totalTaxAmount;
    private BigDecimal grandTotal;
    private List<GstInvoiceWiseRowResponse> invoiceWise;
    private List<GstCustomerWiseRowResponse> customerWise;
    private List<GstMonthWiseRowResponse> monthWise;
    private List<GstTaxWiseRowResponse> taxWise;
    private List<GstHsnSummaryRowResponse> hsnSummary;
}
