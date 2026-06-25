package com.billing.ai.context;

import com.billing.dto.permission.MenuPermissionResponse;
import com.billing.dto.permission.PermissionMatrixResponse;
import com.billing.entity.Company;
import com.billing.entity.User;
import com.billing.exception.ChatbotDisabledException;
import com.billing.service.AccessControlService;
import com.billing.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiSecurityContext {

    private final AccessControlService accessControlService;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public AiUserContext current(Authentication authentication, boolean requireChatbotEnabled) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Session Expired");
        }

        User user = accessControlService.getCurrentUser(authentication.getName());
        Company company = accessControlService.requireCompany(user);
        if (!user.isActive()) {
            throw new AccessDeniedException("Session Expired");
        }
        if (!company.isActive()) {
            throw new AccessDeniedException("Company is inactive");
        }
        if (requireChatbotEnabled && !company.isChatbotEnabled()) {
            throw new ChatbotDisabledException("Company Disabled Chatbot");
        }

        PermissionMatrixResponse matrix = permissionService.effectivePermissions(authentication.getName());
        return AiUserContext.builder()
                .companyId(company.getId())
                .companyName(company.getName())
                .companyActive(company.isActive())
                .chatbotEnabled(company.isChatbotEnabled())
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole() == null ? "USER" : user.getRole().name())
                .userActive(user.isActive())
                .permissions(flattenPermissions(matrix.getMenus()))
                .build();
    }

    private Set<String> flattenPermissions(java.util.List<MenuPermissionResponse> menus) {
        Set<String> permissions = new LinkedHashSet<>();
        if (menus == null) {
            return permissions;
        }
        for (MenuPermissionResponse menu : menus) {
            collect(menu, permissions);
        }
        return permissions;
    }

    private void collect(MenuPermissionResponse menu, Set<String> permissions) {
        if (menu == null) {
            return;
        }
        if (menu.isCanView()) {
            permissions.add((menu.getMenuCode() + ":VIEW").toUpperCase());
        }
        if (menu.getActions() != null) {
            menu.getActions().stream()
                    .filter(action -> action.isAllowed())
                    .forEach(action -> permissions.add((menu.getMenuCode() + ":" + action.getActionCode()).toUpperCase()));
        }
        if (menu.getChildren() != null) {
            menu.getChildren().forEach(child -> collect(child, permissions));
        }
    }
}
