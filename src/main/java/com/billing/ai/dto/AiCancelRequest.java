package com.billing.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiCancelRequest {
    @NotBlank
    private String draftId;
}
