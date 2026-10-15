package com.billing.service;

import com.billing.entity.Company;
import com.billing.entity.RefreshToken;
import com.billing.entity.User;
import com.billing.entity.UserMfaDevice;
import com.billing.entity.enums.RoleName;
import com.billing.multitenancy.TenantContextHolder;
import com.billing.dto.auth.AuthResponse;
import com.billing.dto.auth.LoginRequest;
import com.billing.dto.auth.RefreshTokenRequest;
import com.billing.dto.user.UserProfileResponse;
import com.billing.exception.BadRequestException;
import com.billing.exception.CompanyInactiveException;
import com.billing.exception.UnauthorizedException;
import com.billing.repository.CompanyRepository;
import com.billing.repository.RefreshTokenRepository;
import com.billing.repository.UserMfaDeviceRepository;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserMfaDeviceRepository userMfaDeviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final SecretEncryptionService secretEncryptionService;
    private final TotpService totpService;

    @Value("${app.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Transactional
    public AuthResponse login(Map<String, Object> param) {
        return login(mapToLoginRequest(param));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String loginIdentifier = request.getLoginIdentifier();
        java.util.Optional<User> userOpt = findAuthenticatedUser(loginIdentifier, request.getPassword());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            validateCompanyActiveForLogin(user);
            java.util.Optional<Company> tenantCompanyOpt = resolveTenantCompany();
            if (tenantCompanyOpt.isPresent() && tenantCompanyOpt.get().isApplyMfa()) {
                List<UserMfaDevice> enabledDevices = enabledMfaDevices(user.getId());
                if (!enabledDevices.isEmpty()) {
                    return buildMfaChallenge(user, tenantCompanyOpt.get());
                }
                AuthResponse normalResponse = buildAuthResponse(user);
                return AuthResponse.builder()
                        .accessToken(normalResponse.getAccessToken())
                        .refreshToken(normalResponse.getRefreshToken())
                        .tokenType(normalResponse.getTokenType())
                        .expiresIn(normalResponse.getExpiresIn())
                        .user(normalResponse.getUser())
                        .mfaRequired(false)
                        .mfaEnrollAvailable(true)
                        .build();
            }
            return buildAuthResponse(user);
        }
        // Users first, super-admin fallback: credentials live on the tenant's
        // own companies row, not in users. No refresh token: bootstrap session
        // lasts the access-token lifetime, then re-login.
        return loginSuperAdmin(loginIdentifier, request.getPassword());
    }

    private AuthResponse loginSuperAdmin(String loginIdentifier, String password) {
        Company company = resolveTenantCompany()
                .orElseThrow(() -> new UnauthorizedException("Invalid Mobile Number/Email ID or Password."));
        if (!company.isActive()) {
            throw new CompanyInactiveException("Company is inactive. Please contact administrator.");
        }
        String storedUsername = company.getSuperAdminUsername();
        String storedHash = company.getSuperAdminPassword();
        String identifier = loginIdentifier == null ? "" : loginIdentifier.trim();
        if (storedUsername == null || storedUsername.isBlank()
                || storedHash == null || storedHash.isBlank()
                || !storedUsername.equalsIgnoreCase(identifier)
                || !passwordEncoder.matches(password, storedHash)) {
            throw new UnauthorizedException("Invalid Mobile Number/Email ID or Password.");
        }
        String tenantDb = company.getDatabaseName() != null && !company.getDatabaseName().isBlank()
                ? company.getDatabaseName()
                : resolveTenantDatabaseName();
        String accessToken = jwtService.generateSuperAdminAccessToken(storedUsername, company.getCode(), tenantDb);
        User synthetic = User.builder()
                .fullName("Super Admin")
                .username(storedUsername)
                .mobileNumber("")
                .email(storedUsername)
                .password("")
                .role(RoleName.OWNER)
                .active(true)
                .company(company)
                .superAdmin(true)
                .build();
        UserProfileResponse profileResponse = userMapper.toProfile(synthetic, company);
        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration())
                .user(profileResponse)
                .build();
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

        User user = token.getUser();
        validateCompanyActiveForApi(user);
        // Rotate ONLY the presented token: sibling tabs/devices keep their own
        // sessions. deleteByUser here used to kill every sibling session, so a
        // second concurrent refresh (multi-tab, deploy-time 401 bursts) found
        // its token deleted and logged the user out everywhere (shared storage).
        refreshTokenRepository.delete(token);
        refreshTokenRepository.deleteByUserAndExpiryDateBefore(user, LocalDateTime.now());
        return issueTokens(user);
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
        // Fresh login keeps single-session behavior: old sessions invalidated.
        refreshTokenRepository.deleteByUser(user);
        return issueTokens(user);
    }

    private AuthResponse issueTokens(User user) {
        // DATABASE-per-tenant: User.company is @Transient, so resolve tenant company
        // explicitly from the current tenant connection (TSM-like JWT claims).
        Company tenantCompany = resolveTenantCompany().orElse(null);
        String tenantDb = tenantCompany != null && tenantCompany.getDatabaseName() != null
                && !tenantCompany.getDatabaseName().isBlank()
                ? tenantCompany.getDatabaseName()
                : resolveTenantDatabaseName();
        String tenantCode = tenantCompany != null && tenantCompany.getCode() != null
                ? tenantCompany.getCode()
                : tenantDb.toUpperCase(Locale.ROOT);
        CustomUserDetails userDetails = new CustomUserDetails(user, tenantCode, tenantDb);
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshTokenValue = UUID.randomUUID().toString() + UUID.randomUUID();

        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenValue)
                .user(user)
                .expiryDate(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);

        UserProfileResponse profileResponse = userMapper.toProfile(user, tenantCompany);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration())
                .user(profileResponse)
                .build();
    }

    @Transactional
    public AuthResponse verifyMfa(Map<String, Object> param) {
        String mfaToken = trimmedOrNull(param != null ? param.get("mfaToken") : null);
        if (mfaToken == null || mfaToken.isBlank()) {
            mfaToken = trimmedOrNull(param != null ? param.get("mfa_token") : null);
        }
        String code = trimmedOrNull(param != null ? param.get("code") : null);
        if (code == null || code.isBlank()) {
            code = trimmedOrNull(param != null ? param.get("otp") : null);
        }
        if (mfaToken == null || mfaToken.isBlank()) {
            throw new BadRequestException("MFA session expired. Dobara login karo");
        }
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Verification code required");
        }
        Long tokenUserId;
        try {
            if (!jwtService.isMfaPendingToken(mfaToken)) {
                throw new UnauthorizedException("MFA session expired. Dobara login karo");
            }
            tokenUserId = jwtService.extractUserId(mfaToken);
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("MFA session expired. Dobara login karo");
        }
        if (tokenUserId == null) {
            throw new UnauthorizedException("MFA session expired. Dobara login karo");
        }
        try {
            String tokenDb = jwtService.extractDatabaseName(mfaToken);
            String currentDb = resolveTenantDatabaseName();
            if (tokenDb != null && !tokenDb.isBlank()
                    && !tokenDb.trim().equalsIgnoreCase(currentDb.trim())) {
                throw new UnauthorizedException("MFA session expired. Dobara login karo");
            }
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception ignored) {
            // Tenant resolve fail to neeche company check me handle hoga
        }
        User user = userRepository.findById(tokenUserId)
                .orElseThrow(() -> new UnauthorizedException("MFA session expired. Dobara login karo"));
        validateCompanyActiveForApi(user);
        if (!user.isActive()) {
            throw new UnauthorizedException("User is inactive. Please contact administrator.");
        }
        java.util.Optional<Company> tenantCompanyOpt = resolveTenantCompany();
        if (tenantCompanyOpt.isEmpty() || !tenantCompanyOpt.get().isApplyMfa()) {
            return buildAuthResponse(user);
        }
        List<UserMfaDevice> enabledDevices = enabledMfaDevices(user.getId());
        if (enabledDevices.isEmpty()) {
            throw new BadRequestException("MFA enabled nahi hai. Dobara login karo");
        }
        LocalDateTime now = LocalDateTime.now();
        for (UserMfaDevice device : enabledDevices) {
            if (device.getLockedUntil() != null && device.getLockedUntil().isAfter(now)) {
                throw new UnauthorizedException("Bahut galat attempts. 15 minute baad try karo");
            }
        }
        UserMfaDevice matchedDevice = null;
        for (UserMfaDevice device : enabledDevices) {
            String secret = secretEncryptionService.decrypt(device.getSecretEncrypted());
            if (secret == null || secret.isBlank()) {
                continue;
            }
            if (totpService.verifyCode(secret, code.trim())) {
                matchedDevice = device;
                break;
            }
        }
        if (matchedDevice != null) {
            matchedDevice.setFailedAttempts(0);
            matchedDevice.setLockedUntil(null);
            matchedDevice.setLastUsedAt(now);
            userMfaDeviceRepository.save(matchedDevice);
            return buildAuthResponse(user);
        }
        int maxAttempts = 0;
        for (UserMfaDevice device : enabledDevices) {
            int attempts = device.getFailedAttempts() + 1;
            device.setFailedAttempts(attempts);
            if (attempts >= MfaService.MAX_FAILED_ATTEMPTS) {
                device.setLockedUntil(now.plusMinutes(MfaService.LOCK_MINUTES));
            }
            if (attempts > maxAttempts) {
                maxAttempts = attempts;
            }
            userMfaDeviceRepository.save(device);
        }
        int remaining = MfaService.MAX_FAILED_ATTEMPTS - maxAttempts;
        if (remaining <= 0) {
            throw new UnauthorizedException("Bahut galat attempts. 15 minute baad try karo");
        }
        throw new UnauthorizedException("Galat code. " + remaining + " attempts bache");
    }

    private AuthResponse buildMfaChallenge(User user, Company tenantCompany) {
        String tenantDb = tenantCompany.getDatabaseName() != null && !tenantCompany.getDatabaseName().isBlank()
                ? tenantCompany.getDatabaseName()
                : resolveTenantDatabaseName();
        String tenantCode = tenantCompany.getCode() != null && !tenantCompany.getCode().isBlank()
                ? tenantCompany.getCode()
                : tenantDb.toUpperCase(Locale.ROOT);
        String mfaToken = jwtService.generateMfaPendingToken(user.getId(), tenantCode, tenantDb);
        UserProfileResponse profileResponse = userMapper.toProfile(user, tenantCompany);
        return AuthResponse.builder()
                .tokenType("Bearer")
                .expiresIn(300L)
                .user(profileResponse)
                .mfaRequired(true)
                .mfaToken(mfaToken)
                .build();
    }

    private List<UserMfaDevice> enabledMfaDevices(Long userId) {
        Long normalizedId = DataTypeUtility.getForeignKeyValue(userId);
        if (normalizedId == null) {
            return new ArrayList<>();
        }
        try {
            return userMfaDeviceRepository.findByUserIdAndEnabledTrue(normalizedId);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private String resolveTenantDatabaseName() {
        String tenant = TenantContextHolder.getTenant();
        if (tenant == null || tenant.isBlank()) {
            throw new BadRequestException("Unable to resolve company database for this request. Please contact administrator.");
        }
        String base = tenant.replace("_read", "").replace("_write", "").trim();
        if (base.isEmpty()) {
            throw new BadRequestException("Unable to resolve company database for this request. Please contact administrator.");
        }
        return base;
    }

    private java.util.Optional<Company> resolveTenantCompany() {
        String db = resolveTenantDatabaseName();
        try {
            return companyRepository.findByCodeIgnoreCase(db.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return java.util.Optional.empty();
        }
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
