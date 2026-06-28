package com.billing.security;

import com.billing.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class RequirePermissionAspect {

    private final PermissionService permissionService;

    @Before("@annotation(requiresPermission)")
    public void checkPermission(RequiresPermission requiresPermission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!permissionService.has(authentication, requiresPermission.menu(), requiresPermission.action())) {
            throw new AccessDeniedException("Permission denied");
        }
    }
}
