package com.billing.dto.state;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StateResponse {
    private Long id;
    private String stateCode;
    private String stateName;
    private String countryName;
    private boolean active;
}
