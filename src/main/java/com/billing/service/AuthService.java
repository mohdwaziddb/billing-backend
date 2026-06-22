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
