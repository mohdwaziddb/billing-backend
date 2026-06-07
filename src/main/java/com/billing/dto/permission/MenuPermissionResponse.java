package com.billing.dto.permission;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MenuPermissionResponse {
    private Long id;
    private String menuName;
    private String menuCode;
    private String menuIcon;
    private String menuRoute;
    private Integer displayOrder;
    private Long parentMenuId;
    private String parentMenuCode;
    private boolean canView;
    private List<ActionPermissionResponse> actions;
    private List<MenuPermissionResponse> children;
}
