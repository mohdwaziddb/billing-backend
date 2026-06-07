package com.billing.dto.company;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CompanyOwnersRequest {
    @NotEmpty
    private List<Long> ownerUserIds;
}
