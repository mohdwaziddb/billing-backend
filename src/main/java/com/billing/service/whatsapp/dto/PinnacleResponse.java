package com.billing.service.whatsapp.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PinnacleResponse {
    private String messageId;
    private String status;
    private String rawResponse;
    private String failureReason;
}
