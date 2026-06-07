package com.billing.dto.permission;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PermissionMatrixResponse {
    private String roleCode;
    private Long userId;
    private List<MenuPermissionResponse> menus;
}
