package com.billing.dto.permission;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ActionPermissionResponse {
    private Long id;
    private String actionName;
    private String actionCode;
    private boolean allowed;
    private Boolean overrideAllowed;
}
