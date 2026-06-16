package com.billing.dto;

import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Getter
@Setter
public class BulkDeleteRequest {
    @NotEmpty
    private List<Long> ids;
}
