package com.billing.service;

import com.billing.entity.RefreshToken;
import com.billing.entity.User;
import com.billing.dto.auth.AuthResponse;
import com.billing.dto.auth.ForgotPasswordRequest;
import com.billing.dto.auth.LoginRequest;
import com.billing.dto.auth.RefreshTokenRequest;
import com.billing.dto.user.UserProfileResponse;
import com.billing.exception.BadRequestException;
import com.billing.exception.CompanyInactiveException;
import com.billing.exception.UnauthorizedException;
import com.billing.repository.RefreshTokenRepository;
import com.billing.repository.UserRepository;
import com.billing.security.CustomUserDetails;
import com.billing.security.JwtService;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    @Value("${app.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Transactional
    public AuthResponse login(Map<String, Object> param) {
        return login(mapToLoginRequest(param));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String loginIdentifier = request.getLoginIdentifier();
        User user = findAuthenticatedUser(loginIdentifier, request.getPassword())
                .orElseThrow(() -> new UnauthorizedException("Invalid Mobile Number/Email ID or Password."));
        validateCompanyActiveForLogin(user);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(Map<String, Object> param) {
        return refresh(mapToRefreshRequest(param));
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken token = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(token);
            throw new UnauthorizedException("Refresh token has expired");
        }

        validateCompanyActiveForApi(token.getUser());
        return buildAuthResponse(token.getUser());
    }

    @Transactional
    public void logout(Map<String, Object> param) {
        String refreshToken = extractRefreshToken(param);
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        logout(refreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken).ifPresent(refreshTokenRepository::delete);
    }

    @Transactional
    public void forgotPassword(Map<String, Object> param) {
        forgotPassword(mapToForgotPasswordRequest(param));
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = findByLoginIdentifier(request.getLoginIdentifier())
                .orElseThrow(() -> new BadRequestException("No user found with this Mobile Number/Email ID."));
        if (!user.isActive()) {
            throw new BadRequestException("This user account is inactive.");
        }
        validateCompanyActiveForLogin(user);

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.deleteByUser(user);
    }

    private LoginRequest mapToLoginRequest(Map<String, Object> param) {
        LoginRequest request = new LoginRequest();
        String identifier = extractLoginIdentifier(param);
        if (identifier == null || identifier.isBlank()) {
            throw new BadRequestException("Email / Mobile / Username is required");
        }
        request.setUsername(identifier);
        String password = trimmedOrNull(param != null ? param.get("password") : null);
        if (password == null || password.isBlank()) {
            throw new BadRequestException("Password is required");
        }
        request.setPassword(password);
        return request;
    }

    private RefreshTokenRequest mapToRefreshRequest(Map<String, Object> param) {
        String refreshToken = extractRefreshToken(param);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadRequestException("Refresh token is required");
        }
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshToken);
        return request;
    }

    private ForgotPasswordRequest mapToForgotPasswordRequest(Map<String, Object> param) {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        String identifier = extractLoginIdentifier(param);
        if (identifier == null || identifier.isBlank()) {
            throw new BadRequestException("Email / Mobile / Username is required");
        }
        request.setUsername(identifier);
        Object rawNewPassword = param != null
                ? (param.get("newPassword") != null ? param.get("newPassword") : param.get("password"))
                : null;
        String newPassword = trimmedOrNull(rawNewPassword);
        if (newPassword == null || newPassword.isBlank()) {
            throw new BadRequestException("New password is required");
        }
        if (newPassword.length() < 6) {
            throw new BadRequestException("New password must be at least 6 characters");
        }
        request.setNewPassword(newPassword);
        return request;
    }

    private String extractLoginIdentifier(Map<String, Object> param) {
        if (param == null) {
            return null;
        }
        String identifier = trimmedOrNull(param.get("username"));
        if (identifier == null || identifier.isBlank()) {
            identifier = trimmedOrNull(param.get("email"));
        }
        if (identifier == null || identifier.isBlank()) {
            identifier = trimmedOrNull(param.get("loginIdentifier"));
        }
        if (identifier == null || identifier.isBlank()) {
            identifier = trimmedOrNull(param.get("mobileNumber"));
        }
        if (identifier == null || identifier.isBlank()) {
            identifier = trimmedOrNull(param.get("mobile"));
        }
        return identifier;
    }

    private String extractRefreshToken(Map<String, Object> param) {
        if (param == null) {
            return null;
        }
        String token = trimmedOrNull(param.get("refreshToken"));
        if (token == null || token.isBlank()) {
            token = trimmedOrNull(param.get("refresh_token"));
        }
        if (token == null || token.isBlank()) {
            token = trimmedOrNull(param.get("token"));
        }
        return token;
    }

    private String trimmedOrNull(Object value) {
        String text = DataTypeUtility.stringValue(value);
        return text.isEmpty() ? null : text.trim();
    }

    private AuthResponse buildAuthResponse(User user) {
        validateCompanyActiveForApi(user);
        refreshTokenRepository.deleteByUser(user);

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshTokenValue = UUID.randomUUID().toString() + UUID.randomUUID();

        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenValue)
                .user(user)
                .expiryDate(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);

        UserProfileResponse profileResponse = userMapper.toProfile(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration())
                .user(profileResponse)
                .build();
    }

    private java.util.Optional<User> findByLoginIdentifier(String loginIdentifier) {
        String normalized = normalizeIdentifier(loginIdentifier);
        List<User> candidates = new ArrayList<>();
        candidates.addAll(userRepository.findAllByUsernameIgnoreCase(normalized));
        candidates.addAll(userRepository.findAllByEmailIgnoreCase(normalized));
        candidates.addAll(userRepository.findAllByMobileNumber(normalized));
        List<User> uniqueCandidates = uniqueById(candidates);
        if (uniqueCandidates.size() > 1) {
            throw new BadRequestException("Multiple companies use this identifier. Please contact administrator.");
        }
        return uniqueCandidates.stream().findFirst();
    }

    private java.util.Optional<User> findAuthenticatedUser(String loginIdentifier, String password) {
        String normalized = normalizeIdentifier(loginIdentifier);
        List<User> candidates = new ArrayList<>();
        candidates.addAll(userRepository.findAllByUsernameIgnoreCase(normalized));
        candidates.addAll(userRepository.findAllByEmailIgnoreCase(normalized));
        candidates.addAll(userRepository.findAllByMobileNumber(normalized));
        List<User> matches = uniqueById(candidates).stream()
                .filter(user -> passwordEncoder.matches(password, user.getPassword()))
                .toList();
        if (matches.size() > 1) {
            throw new UnauthorizedException("Multiple companies use these credentials. Please contact administrator.");
        }
        return matches.stream().findFirst();
    }

    private void validateCompanyActiveForLogin(User user) {
        if (user.getCompany() != null && !user.getCompany().isActive()) {
            throw new CompanyInactiveException("Company is inactive. Please contact administrator.");
        }
    }

    private void validateCompanyActiveForApi(User user) {
        if (user.getCompany() != null && !user.getCompany().isActive()) {
            throw new CompanyInactiveException("Company is inactive");
        }
    }

    private List<User> uniqueById(List<User> candidates) {
        Map<Long, User> byId = new LinkedHashMap<>();
        for (User candidate : candidates) {
            byId.putIfAbsent(candidate.getId(), candidate);
        }
        return new ArrayList<>(byId.values());
    }

    private String normalizeEmail(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeMobile(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeUsername(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeIdentifier(String value) {
        return value == null ? null : value.trim();
    }
}
