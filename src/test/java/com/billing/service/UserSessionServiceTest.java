package com.billing.service;

import com.billing.entity.User;
import com.billing.entity.UserSession;
import com.billing.entity.enums.RoleName;
import com.billing.entity.enums.UserSessionStatus;
import com.billing.exception.BadRequestException;
import com.billing.repository.RefreshTokenRepository;
import com.billing.repository.UserRepository;
import com.billing.repository.UserSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Devices: hata hua device turant band, apna device khud nahi hatta, Owner ke devices sirf Owner. */
@ExtendWith(MockitoExtension.class)
class UserSessionServiceTest {

    @Mock
    private UserSessionRepository userSessionRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AccessControlService accessControlService;

    private final User user = User.builder().id(5L).fullName("Asha").role(RoleName.USER).active(true).build();

    private UserSessionService service() {
        UserSessionService service = new UserSessionService(userSessionRepository, refreshTokenRepository, userRepository, accessControlService);
        ReflectionTestUtils.setField(service, "refreshTokenExpiration", 7L * 24 * 3600 * 1000);
        return service;
    }

    private UserSession session(long id, String key, UserSessionStatus status) {
        return UserSession.builder().id(id).user(user).sessionKey(key).status(status)
                .loginAt(LocalDateTime.now()).lastActiveAt(LocalDateTime.now()).build();
    }

    @Test
    void endedSessionIsNotActive() {
        when(userSessionRepository.findBySessionKey("gone")).thenReturn(Optional.of(session(1, "gone", UserSessionStatus.REVOKED)));
        when(userSessionRepository.findBySessionKey("live")).thenReturn(Optional.of(session(2, "live", UserSessionStatus.ACTIVE)));
        when(userSessionRepository.findBySessionKey("missing")).thenReturn(Optional.empty());

        assertFalse(service().isActiveAndTouch("gone"));
        assertFalse(service().isActiveAndTouch("missing"));
        assertTrue(service().isActiveAndTouch("live"));
    }

    @Test
    void endingASessionDeletesItsRefreshToken() {
        UserSession live = session(2, "live", UserSessionStatus.ACTIVE);

        service().end(live, UserSessionStatus.LOGGED_OUT, "Logged out");

        assertEquals(UserSessionStatus.LOGGED_OUT, live.getStatus());
        assertEquals("Logged out", live.getEndedReason());
        verify(refreshTokenRepository).deleteBySession(live);
    }

    @Test
    void cannotRemoveTheDeviceYouAreUsing() {
        when(accessControlService.getCurrentUser("asha")).thenReturn(user);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userSessionRepository.findById(2L)).thenReturn(Optional.of(session(2, "mine", UserSessionStatus.ACTIVE)));

        assertThrows(BadRequestException.class, () -> service().logoutMyDevice("asha", 2L, "mine"));
        verify(refreshTokenRepository, never()).deleteBySession(any());
    }

    @Test
    void logoutOthersKeepsCurrentDevice() {
        UserSession mine = session(1, "mine", UserSessionStatus.ACTIVE);
        UserSession phone = session(2, "phone", UserSessionStatus.ACTIVE);
        when(accessControlService.getCurrentUser("asha")).thenReturn(user);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userSessionRepository.findByUserAndStatus(user, UserSessionStatus.ACTIVE)).thenReturn(List.of(mine, phone));

        assertEquals(1, service().logoutMyOtherDevices("asha", "mine"));
        assertEquals(UserSessionStatus.ACTIVE, mine.getStatus());
        assertEquals(UserSessionStatus.REVOKED, phone.getStatus());
    }

    @Test
    void adminCannotManageOwnerDevices() {
        User owner = User.builder().id(1L).fullName("Owner").role(RoleName.OWNER).active(true).build();
        User admin = User.builder().id(2L).fullName("Admin").role(RoleName.ADMIN).active(true).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(accessControlService.getCurrentUser("admin")).thenReturn(admin);
        when(accessControlService.isCompanyOwner(admin)).thenReturn(false);

        assertThrows(BadRequestException.class, () -> service().userSessions("admin", 1L, null));
    }
}
