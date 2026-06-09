package com.billing.dto.email;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EmailPreviewResponse {
    private String subject;
    private String emailBody;
}
