package com.billing.service;

import com.billing.dto.auth.PasswordResetChallengeResponse;
import com.billing.entity.Company;
import com.billing.entity.PasswordResetOtp;
import com.billing.entity.RefreshToken;
import com.billing.entity.User;
import com.billing.entity.enums.RoleName;
import com.billing.exception.BadRequestException;
import com.billing.exception.CompanyInactiveException;
import com.billing.multitenancy.TenantContextHolder;
import com.billing.repository.CompanyRepository;
import com.billing.repository.EmailProviderSettingRepository;
import com.billing.repository.PasswordResetOtpRepository;
import com.billing.repository.RefreshTokenRepository;
import com.billing.repository.UserMfaDeviceRepository;
import com.billing.repository.UserRepository;
import com.billing.security.JwtService;
import com.billing.security.LoginAttemptService;
import com.billing.util.SanitizeData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginSecurityFixesTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private PasswordResetOtpRepository passwordResetOtpRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private EmailProviderSettingRepository emailProviderSettingRepository;
    @Mock
    private UserMfaDeviceRepository userMfaDeviceRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private SecretEncryptionService secretEncryptionService;
    @Mock
    private TotpService totpService;
    @Mock
    private MfaService mfaService;

    @BeforeEach
    void bindTenant() {
        TenantContextHolder.setTenantId("test");
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private PasswordResetService resetService() {
        return new PasswordResetService(userRepository, companyRepository, passwordResetOtpRepository,
                refreshTokenRepository, passwordEncoder, emailService, auditLogService, notificationService,
                emailProviderSettingRepository);
    }

    private AuthService authService() {
        return new AuthService(userRepository, companyRepository, refreshTokenRepository, userMfaDeviceRepository,
                passwordEncoder, jwtService, new UserMapper(), secretEncryptionService, totpService, mfaService,
                new LoginAttemptService());
    }

    @Test
    void secretsSurviveSanitizerButOtherFieldsAreCleaned() {
        Map<String, Object> param = new HashMap<>();
        param.put("password", "Pass<word>1");
        param.put("confirmPassword", "a>b&c<d");
        param.put("fullName", "<b>Ravi</b>");

        Map<String, Object> clean = SanitizeData.sanitizeMapObjPreservingSecrets(param);

        assertEquals("Pass<word>1", clean.get("password"));
        assertEquals("a>b&c<d", clean.get("confirmPassword"));
        assertEquals("Ravi", clean.get("fullName"));
    }

    @Test
    void otpEndpointsKeepAttemptCountOnWrongOtp() throws Exception {
        for (String method : List.of("verifyOtp", "confirmOtp")) {
            Transactional tx = PasswordResetService.class.getMethod(method, Map.class).getAnnotation(Transactional.class);
            assertNotNull(tx, method);
            assertTrue(List.of(tx.noRollbackFor()).contains(BadRequestException.class), method + " must not roll back attempts");
        }
    }

    @Test
    void wrongOtpIncrementsAttemptsAndFifthConsumesChallenge() {
        PasswordResetOtp challenge = PasswordResetOtp.builder()
                .id(5L).userId(7L).otpHash("hash").channel("EMAIL")
                .expiresAt(LocalDateTime.now().plusMinutes(5)).attempts(0).consumed(false)
                .build();
        when(passwordResetOtpRepository.findById(5L)).thenReturn(Optional.of(challenge));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        Map<String, Object> param = Map.of("challengeId", 5L, "otp", "000000");

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThrows(BadRequestException.class, () -> resetService().verifyOtp(param));
            assertEquals(attempt, challenge.getAttempts());
        }
        assertTrue(challenge.isConsumed());
    }

    @Test
    void decoyChallengeIdLooksLikeARealOne() {
        Company company = Company.builder().id(1L).code("TEST").name("Test").build();
        when(companyRepository.findByCodeIgnoreCase("TEST")).thenReturn(Optional.of(company));
        when(emailProviderSettingRepository.findFirstByCompanyAndActiveTrueOrderByIdDesc(company))
                .thenReturn(Optional.of(new com.billing.entity.EmailProviderSetting()));
        when(passwordResetOtpRepository.findTopByOrderByIdDesc())
                .thenReturn(Optional.of(PasswordResetOtp.builder().id(41L).build()));

        PasswordResetChallengeResponse response = resetService().requestOtp(Map.of("identifier", "nobody@example.com"));

        long id = response.getChallengeId();
        assertTrue(id >= 42L && id <= 44L, "decoy id " + id + " must sit next to real ids");
    }

    @Test
    void inactiveCompanyBlocksUserLogin() {
        User user = User.builder().id(3L).username("ravi").email("ravi@example.com").password("hash")
                .role(RoleName.USER).active(true).build();
        when(userRepository.findAllByUsernameIgnoreCase("ravi")).thenReturn(List.of(user));
        lenient().when(userRepository.findAllByEmailIgnoreCase("ravi")).thenReturn(List.of());
        lenient().when(userRepository.findAllByMobileNumber("ravi")).thenReturn(List.of());
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);
        when(companyRepository.findByCodeIgnoreCase("TEST"))
                .thenReturn(Optional.of(Company.builder().id(1L).code("TEST").name("Test").active(false).build()));

        assertThrows(CompanyInactiveException.class,
                () -> authService().login(Map.of("username", "ravi", "password", "secret123")));
    }

    @Test
    void logoutRevokesAccessTokensToo() {
        User user = User.builder().id(3L).username("ravi").email("ravi@example.com").password("hash")
                .role(RoleName.USER).active(true).build();
        RefreshToken token = RefreshToken.builder().token("rt").user(user).expiryDate(LocalDateTime.now().plusDays(1)).build();
        when(refreshTokenRepository.findByToken("rt")).thenReturn(Optional.of(token));
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        authService().logout("rt");

        assertNotNull(user.getTokensValidAfter());
        verify(refreshTokenRepository).delete(token);
    }
}
