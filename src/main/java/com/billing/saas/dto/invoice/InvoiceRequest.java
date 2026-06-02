package com.billing.saas.dto.invoice;

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

    @DecimalMin(value = "0.00")
    private BigDecimal discountAmount;

    @Valid
    @NotEmpty
    private List<InvoiceItemRequest> items;
}
