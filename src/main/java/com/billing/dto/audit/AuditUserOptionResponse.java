package com.billing.dto.audit;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuditUserOptionResponse {
    private Long id;
    private String name;
}
