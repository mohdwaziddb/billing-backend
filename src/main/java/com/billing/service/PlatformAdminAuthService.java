package com.billing.service;

import com.billing.dto.auth.PlatformAdminAuthResponse;
import com.billing.dto.auth.PlatformAdminLoginRequest;
import com.billing.exception.UnauthorizedException;
import com.billing.security.JwtService;
import com.billing.security.LoginAttemptService;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlatformAdminAuthService {

    // Platform credentials live in the common catalog (billing_common.platform_settings),
    // NOT in tenant DBs: platform-admin works on the main domain without a tenant pool.
    @Qualifier("billingCommonJdbcTemplate")
    private final JdbcTemplate billingCommonJdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;

    @Transactional(readOnly = true)
    public PlatformAdminAuthResponse login(Map<String, Object> param) {
        PlatformAdminLoginRequest request = mapToLoginRequest(param);
        return login(request);
    }

    @Transactional(readOnly = true)
    public PlatformAdminAuthResponse login(PlatformAdminLoginRequest request) {
        // Ek hi platform admin account hai: lockout key fixed rakho, taaki
        // username badal kar lockout bypass na ho.
        String attemptKey = LoginAttemptService.key("platform-admin", "admin");
        loginAttemptService.assertNotLocked(attemptKey);
        Map<String, Object> setting = loadPlatformSetting();

        if (!matchesUsername((String) setting.get("username"), request.getUsername())
                || setting.get("password") == null
                || !passwordEncoder.matches(request.getPassword(), (String) setting.get("password"))) {
            loginAttemptService.recordFailure(attemptKey);
            throw new UnauthorizedException("Invalid platform admin credentials.");
        }
        loginAttemptService.recordSuccess(attemptKey);

        String username = ((String) setting.get("username")).trim();
        return PlatformAdminAuthResponse.builder()
                .accessToken(jwtService.generatePlatformAdminAccessToken(username))
                .tokenType("Bearer")
                .expiresIn(jwtService.getAdminAccessTokenExpiration())
                .username(username)
                .build();
    }

    private Map<String, Object> loadPlatformSetting() {
        try {
            return billingCommonJdbcTemplate.queryForMap(
                    "SELECT platform_name, platform_logo, platform_tagline, username, password FROM billing_common.platform_settings ORDER BY id ASC LIMIT 1");
        } catch (Exception e) {
            throw new UnauthorizedException("Platform admin is not configured.");
        }
    }

    private boolean matchesUsername(String savedUsername, String requestUsername) {
        if (savedUsername == null || requestUsername == null) {
            return false;
        }
        return savedUsername.trim().equalsIgnoreCase(requestUsername.trim());
    }

    private PlatformAdminLoginRequest mapToLoginRequest(Map<String, Object> param) {
        PlatformAdminLoginRequest request = new PlatformAdminLoginRequest();
        String username = DataTypeUtility.stringValue(param.get("username"));
        if (username.length() == 0) {
            username = null;
        }
        request.setUsername(username);
        String password = DataTypeUtility.stringValue(param.get("password"));
        if (password.length() == 0) {
            password = null;
        }
        request.setPassword(password);
        return request;
    }
}
