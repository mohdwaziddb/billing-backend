package com.billing.service;

import com.billing.entity.Company;
import com.billing.entity.RefreshToken;
import com.billing.entity.User;
import com.billing.entity.enums.RoleName;
import com.billing.dto.auth.AuthResponse;
import com.billing.dto.auth.ForgotPasswordRequest;
import com.billing.dto.auth.LoginRequest;
import com.billing.dto.auth.RefreshTokenRequest;
import com.billing.dto.auth.RegisterCompanyRequest;
import com.billing.dto.user.UserProfileResponse;
import com.billing.exception.BadRequestException;
import com.billing.exception.CompanyInactiveException;
import com.billing.exception.UnauthorizedException;
import com.billing.repository.CompanyRepository;
import com.billing.repository.RefreshTokenRepository;
import com.billing.repository.UserRepository;
import com.billing.security.CustomUserDetails;
import com.billing.security.JwtService;
import com.billing.config.PermissionDataInitializer;
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

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final PermissionDataInitializer permissionDataInitializer;

    @Value("${app.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Transactional
    public AuthResponse registerCompany(RegisterCompanyRequest request) {
        if (companyRepository.existsByEmailIgnoreCase(request.getCompanyEmail())) {
            throw new BadRequestException("Company email already exists");
        }
        if (companyRepository.existsByTaxIdIgnoreCase(request.getTaxId())) {
            throw new BadRequestException("Tax ID already exists");
        }
        String companyCode = normalizeCompanyCode(request.getCompanyCode(), request.getCompanyName());
        if (companyRepository.existsByCodeIgnoreCase(companyCode)) {
            throw new BadRequestException("Company code already exists");
        }
        String adminMobileNumber = normalizeMobile(request.getAdminMobileNumber());
        String adminEmail = normalizeEmail(request.getAdminEmail());
        String adminUsername = normalizeUsername(request.getAdminUsername());

        Company company = Company.builder()
                .name(request.getCompanyName())
                .code(companyCode)
                .databaseName(blankToNull(request.getDatabaseName()))
                .email(request.getCompanyEmail())
                .phone(request.getCompanyPhone())
                .address(request.getCompanyAddress())
                .taxId(request.getTaxId())
                .active(true)
                .build();
        companyRepository.save(company);
        permissionDataInitializer.seedPermissionsForCompany(company);

        validateUniqueUser(company, adminUsername, adminMobileNumber, adminEmail, null);

        User user = User.builder()
                .fullName(request.getAdminFullName())
                .mobileNumber(adminMobileNumber)
                .email(adminEmail)
                .username(adminUsername)
                .password(passwordEncoder.encode(request.getAdminPassword()))
                .role(RoleName.OWNER)
                .active(true)
                .company(company)
                .build();
        userRepository.save(user);

        return buildAuthResponse(user);
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
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken).ifPresent(refreshTokenRepository::delete);
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

    private String normalizeCompanyCode(String requestedCode, String companyName) {
        String source = requestedCode == null || requestedCode.isBlank() ? companyName : requestedCode;
        String normalized = source.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (normalized.isBlank()) {
            throw new BadRequestException("Company code is required");
        }
        return normalized.length() > 20 ? normalized.substring(0, 20) : normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    private void validateUniqueUser(Company company, String username, String mobileNumber, String email, Long currentUserId) {
        String normalizedUsername = normalizeUsername(username);
        List<String> validationMessages = new ArrayList<>();
        userRepository.findByCompanyAndUsernameIgnoreCase(company, normalizedUsername)
                .filter(existing -> currentUserId == null || !existing.getId().equals(currentUserId))
                .ifPresent(existing -> validationMessages.add("Username already exists in this company."));
        userRepository.findByCompanyAndMobileNumber(company, mobileNumber)
                .filter(existing -> currentUserId == null || !existing.getId().equals(currentUserId))
                .ifPresent(existing -> validationMessages.add("Mobile number already exists in this company."));
        userRepository.findByCompanyAndEmailIgnoreCase(company, email)
                .filter(existing -> currentUserId == null || !existing.getId().equals(currentUserId))
                .ifPresent(existing -> validationMessages.add("Email already exists in this company."));
        if (!validationMessages.isEmpty()) {
            throw new BadRequestException(String.join(" ", validationMessages));
        }
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
