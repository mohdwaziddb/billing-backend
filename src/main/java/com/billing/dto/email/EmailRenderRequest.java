package com.billing.dto.email;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class EmailRenderRequest {
    private Map<String, Object> variables;
}
