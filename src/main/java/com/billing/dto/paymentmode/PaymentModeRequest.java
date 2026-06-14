package com.billing.dto.paymentmode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentModeRequest {

    @NotBlank
    @Size(max = 255)
    private String modeName;

    @Size(max = 1000)
    private String description;

    @NotNull
    private Boolean active;
}
