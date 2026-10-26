package com.billing.service;

import com.billing.entity.UserMfaDevice;
import com.billing.repository.UserMfaDeviceRepository;
import com.billing.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MfaLockoutTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMfaDeviceRepository userMfaDeviceRepository;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private TotpService totpService;
    @Mock
    private SecretEncryptionService secretEncryptionService;
    @Mock
    private AuditLogService auditLogService;

    private MfaService mfaService;

    @BeforeEach
    void setUp() {
        mfaService = new MfaService(userRepository, userMfaDeviceRepository, accessControlService,
                totpService, secretEncryptionService, auditLogService);
    }

    @Test
    void fifthWrongCodeLocksFor15Minutes() {
        UserMfaDevice device = UserMfaDevice.builder().userId(7L).enabled(true).failedAttempts(4).build();
        when(userMfaDeviceRepository.findByUserIdAndEnabledTrue(7L)).thenReturn(List.of(device));

        int attempts = mfaService.recordFailedAttempts(7L);

        assertEquals(MfaService.MAX_FAILED_ATTEMPTS, attempts);
        assertNotNull(device.getLockedUntil());
        assertTrue(device.getLockedUntil().isAfter(LocalDateTime.now().plusMinutes(MfaService.LOCK_MINUTES - 1)));
    }

    @Test
    void afterLockExpiresUserGetsFreshAttempts() {
        // 15 min pehle lock laga tha (counter 5 par): ab pehla galat code dobara lock nahi karega.
        UserMfaDevice device = UserMfaDevice.builder().userId(7L).enabled(true)
                .failedAttempts(MfaService.MAX_FAILED_ATTEMPTS)
                .lockedUntil(LocalDateTime.now().minusSeconds(1))
                .build();
        when(userMfaDeviceRepository.findByUserIdAndEnabledTrue(7L)).thenReturn(List.of(device));

        int attempts = mfaService.recordFailedAttempts(7L);

        assertEquals(1, attempts);
        assertEquals(1, device.getFailedAttempts());
        assertNull(device.getLockedUntil());
    }
}
