package com.billing.dto.invoice;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class InvoiceResponse {
    private Long id;
    private String invoiceNo;
    private Long customerId;
    private String customerName;
    private String customerMobile;
    private String customerEmail;
    private String customerAddress;
    private String customerState;
    private Long customerStateId;
    private String customerGstin;
    private Long referByUserId;
    private String referByUserName;
    private String referByUsername;
    private BigDecimal subtotal;
    private BigDecimal taxableAmount;
    private BigDecimal cgstTotal;
    private BigDecimal sgstTotal;
    private BigDecimal igstTotal;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal roundOff;
    private BigDecimal grandTotal;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;
    private String paymentStatus;
    private LocalDate invoiceDate;
    private boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private List<InvoiceItemResponse> items;
}
