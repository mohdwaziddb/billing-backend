package com.billing.security;

import com.billing.entity.User;
import com.billing.entity.enums.RoleName;
import com.billing.exception.BadRequestException;
import com.billing.exception.UnauthorizedException;
import com.billing.util.OutboundUrlGuard;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityGuardsTest {

    @Test
    void outboundGuardBlocksInternalTargets() {
        assertThrows(BadRequestException.class, () -> OutboundUrlGuard.requirePublicUrl("http://127.0.0.1:8080/actuator"));
        assertThrows(BadRequestException.class, () -> OutboundUrlGuard.requirePublicUrl("http://169.254.169.254/latest/meta-data"));
        assertThrows(BadRequestException.class, () -> OutboundUrlGuard.requirePublicUrl("http://10.0.0.5/sms"));
        assertThrows(BadRequestException.class, () -> OutboundUrlGuard.requirePublicUrl("http://192.168.1.10/sms"));
        assertThrows(BadRequestException.class, () -> OutboundUrlGuard.requirePublicUrl("http://[::1]/sms"));
        assertThrows(BadRequestException.class, () -> OutboundUrlGuard.requirePublicUrl("file:///etc/passwd"));
        assertDoesNotThrow(() -> OutboundUrlGuard.requirePublicUrl("https://8.8.8.8/api?x=1"));
    }

    @Test
    void forwardedHeaderIgnoredFromDirectClient() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        // Proxy ke bina aaya: header spoof ho sakta hai, socket IP hi maano.
        assertEquals("203.0.113.9", RateLimitingFilter.clientIp(request));
    }

    @Test
    void forwardedHeaderReadFromRightBehindProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");
        // Client ne "1.2.3.4" fake bheja, proxy ne asli 203.0.113.9 append kiya.
        request.addHeader("X-Forwarded-For", "1.2.3.4, 203.0.113.9");
        assertEquals("203.0.113.9", RateLimitingFilter.clientIp(request));
    }

    @Test
    void accountLocksAfterRepeatedFailures() {
        LoginAttemptService service = new LoginAttemptService();
        String key = LoginAttemptService.key("tenant_a", "Owner@Example.com");
        for (int attempt = 1; attempt < LoginAttemptService.MAX_FAILURES; attempt++) {
            service.recordFailure(key);
            assertDoesNotThrow(() -> service.assertNotLocked(key));
        }
        service.recordFailure(key);
        assertThrows(UnauthorizedException.class, () -> service.assertNotLocked(key));
        // Case/space alag ho to bhi same account (bypass nahi).
        assertThrows(UnauthorizedException.class,
                () -> service.assertNotLocked(LoginAttemptService.key("tenant_a", " owner@example.com ")));
    }

    @Test
    void accessTokenIssuedBeforeCutoffIsRejected() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "test-secret-test-secret-test-secret-1234567890");
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 900_000L);
        User user = User.builder().id(7L).email("a@b.com").password("x").role(RoleName.USER).active(true).build();
        String token = jwtService.generateAccessToken(new CustomUserDetails(user, "T", "tenant_a"));

        assertTrue(jwtService.isTokenValid(token, new CustomUserDetails(user, "T", "tenant_a")));

        // Deactivated user: valid token bhi band.
        user.setActive(false);
        assertFalse(jwtService.isTokenValid(token, new CustomUserDetails(user, "T", "tenant_a")));
        user.setActive(true);

        // Password reset/deactivate ke baad cutoff aage: purana token band.
        user.setTokensValidAfter(LocalDateTime.now().plusSeconds(5));
        assertFalse(jwtService.isTokenValid(token, new CustomUserDetails(user, "T", "tenant_a")));
    }
}
