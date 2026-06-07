package com.billing.dto.user;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserPreferenceResponse {
    private boolean darkModeEnabled;
}
