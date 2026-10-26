package com.billing.dto.invoice;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Invoice list page ke KPI cards + sparkline. List wale saare filters lagte hain;
 * SQL aggregate se banta hai (pehle browser 50k tak invoice laa kar ginta tha).
 */
@Getter
@Builder
public class InvoiceListSummaryResponse {
    private long totalInvoices;
    private long paidInvoices;
    private long pendingInvoices;
    private long partialInvoices;
    private BigDecimal outstandingAmount;
    /** (date, status) wise points: frontend inhi se 8-bucket sparkline banata hai. */
    private List<TrendPoint> trend;

    @Getter
    @Builder
    public static class TrendPoint {
        private LocalDate invoiceDate;
        private String paymentStatus;
        private long invoiceCount;
        private BigDecimal balanceAmount;
    }
}
