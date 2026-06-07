package com.billing.dto.permission;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MenuPermissionRequest {
    private Long menuId;
    private boolean canView;
    private List<ActionPermissionRequest> actions;
}
