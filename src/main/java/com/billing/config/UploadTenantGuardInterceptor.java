package com.billing.config;

import com.billing.multitenancy.TenantContextHolder;
import com.billing.security.PlatformAdminPrincipal;
import com.billing.service.CompanyRegistryService;
import com.billing.service.storage.S3StorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Locale;

/**
 * Local-storage uploads (/uploads/{tenantFolder}/...) pehle har logged-in user
 * ko har company ki file de dete the (file names predictable: signature-x.png).
 * Ab doosre tenant ke folder par 404. Apna folder, platform admin, aur purane
 * shared/legacy folders (company-logos/...) jaise pehle chalte rahenge.
 */
@Component
@RequiredArgsConstructor
public class UploadTenantGuardInterceptor implements HandlerInterceptor {

    private final CompanyRegistryService companyRegistryService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String folder = firstFolder(request.getRequestURI());
        if (folder == null) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PlatformAdminPrincipal) {
            return true;
        }
        String tenant = TenantContextHolder.getTenant();
        if (tenant != null && !tenant.isBlank()) {
            String ownFolder = S3StorageService.tenantFolder(tenant.replace("_read", "").replace("_write", ""));
            if (folder.equals(ownFolder)) {
                return true;
            }
        }
        for (String databaseName : companyRegistryService.allDatabaseNames()) {
            if (folder.equals(S3StorageService.tenantFolder(databaseName))) {
                // 404 (403 nahi): doosri company ki file exist karti hai ye bhi na bataye.
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return false;
            }
        }
        return true;
    }

    private String firstFolder(String uri) {
        if (uri == null) {
            return null;
        }
        int start = uri.indexOf("/uploads/");
        if (start < 0) {
            return null;
        }
        String rest = uri.substring(start + "/uploads/".length());
        int slash = rest.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        return rest.substring(0, slash).toLowerCase(Locale.ROOT);
    }
}
