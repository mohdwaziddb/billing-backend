package com.billing.saas.service;

import com.billing.saas.dto.auth.AuthResponse;
import com.billing.saas.dto.auth.LoginRequest;
import com.billing.saas.dto.auth.RefreshTokenRequest;
import com.billing.saas.dto.auth.RegisterCompanyRequest;
import com.billing.saas.dto.user.UserProfileResponse;
import com.billing.saas.entity.Company;
import com.billing.saas.entity.RefreshToken;
import com.billing.saas.entity.User;
import com.billing.saas.entity.enums.RoleName;
import com.billing.saas.exception.BadRequestException;
import com.billing.saas.exception.UnauthorizedException;
import com.billing.saas.repository.CompanyRepository;
import com.billing.saas.repository.RefreshTokenRepository;
import com.billing.saas.repository.UserRepository;
import com.billing.saas.security.CustomUserDetails;
import com.billing.saas.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserMapper userMapper;

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
        if (userRepository.existsByEmailIgnoreCase(request.getAdminEmail())) {
            throw new BadRequestException("Admin email already exists");
        }

        Company company = Company.builder()
                .name(request.getCompanyName())
                .email(request.getCompanyEmail())
                .phone(request.getCompanyPhone())
                .address(request.getCompanyAddress())
                .taxId(request.getTaxId())
                .build();
        companyRepository.save(company);

        User user = User.builder()
                .fullName(request.getAdminFullName())
                .email(request.getAdminEmail())
                .password(passwordEncoder.encode(request.getAdminPassword()))
                .role(RoleName.COMPANY_ADMIN)
                .active(true)
                .company(company)
                .build();
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
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

        return buildAuthResponse(token.getUser());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken).ifPresent(refreshTokenRepository::delete);
    }

    private AuthResponse buildAuthResponse(User user) {
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
}
