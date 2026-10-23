package com.billing.ai.tool;

import com.billing.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiToolGuard {

    private static final String DENIED_MESSAGE =
            "You do not have permission to perform this action. Please contact your company owner or admin.";

    private final PermissionService permissionService;

    public boolean allowed(String menu, String action) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return permissionService.has(authentication, menu, action);
    }

    public String denied() {
        return DENIED_MESSAGE;
    }
}
