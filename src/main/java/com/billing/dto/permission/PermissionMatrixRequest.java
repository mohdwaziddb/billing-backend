package com.billing.dto.permission;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PermissionMatrixRequest {
    private String roleCode;
    private Long userId;
    private List<MenuPermissionRequest> menus;
}
