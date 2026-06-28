package com.billing.dto.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class PurchaseRequest {

    @NotNull
    private LocalDate purchaseDate;

    private String supplierName;

    private String remarks;

    @Valid
    @NotEmpty
    private List<PurchaseItemRequest> items;
}
