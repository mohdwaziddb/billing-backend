package com.billing.dto.invoice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class InvoiceRequest {

    @NotNull
    private Long customerId;

    @NotNull
    private LocalDate invoiceDate;

    private Long referByUserId;

    @DecimalMin(value = "0.00")
    private BigDecimal discountAmount;

    @DecimalMin(value = "0.00")
    private BigDecimal paidAmount;

    private String paymentMode;

    @Valid
    @NotEmpty
    private List<InvoiceItemRequest> items;
}
