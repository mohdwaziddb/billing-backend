package com.billing.service;

import com.billing.dto.auth.PlatformAdminAuthResponse;
import com.billing.dto.auth.PlatformAdminLoginRequest;
import com.billing.entity.PlatformSetting;
import com.billing.exception.UnauthorizedException;
import com.billing.repository.PlatformSettingRepository;
import com.billing.security.JwtService;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlatformAdminAuthService {

    private final PlatformSettingRepository platformSettingRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public PlatformAdminAuthResponse login(Map<String, Object> param) {
        PlatformAdminLoginRequest request = mapToLoginRequest(param);
        return login(request);
    }

    @Transactional(readOnly = true)
    public PlatformAdminAuthResponse login(PlatformAdminLoginRequest request) {
        PlatformSetting setting = platformSettingRepository.findTopByOrderByIdAsc()
                .orElseThrow(() -> new UnauthorizedException("Platform admin is not configured."));

        if (!matchesUsername(setting.getUsername(), request.getUsername())
                || setting.getPassword() == null
                || !passwordEncoder.matches(request.getPassword(), setting.getPassword())) {
            throw new UnauthorizedException("Invalid platform admin credentials.");
        }

        String username = setting.getUsername().trim();
        return PlatformAdminAuthResponse.builder()
                .accessToken(jwtService.generatePlatformAdminAccessToken(username))
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration())
                .username(username)
                .build();
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
