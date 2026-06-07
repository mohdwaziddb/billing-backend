package com.billing.dto.permission;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActionPermissionRequest {
    private Long actionId;
    private boolean allowed;
    private Boolean overrideAllowed;
}
