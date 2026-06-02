package com.billing.saas.dto.payment;

import com.billing.saas.entity.enums.PaymentMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class PaymentRequest {

    @NotNull
    private Long customerId;

    private Long invoiceId;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    @NotNull
    private LocalDate paymentDate;

    @NotNull
    private PaymentMode mode;

    private String remarks;
}
