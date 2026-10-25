package com.billing.service;

import com.billing.dto.user.UserSessionResponse;
import com.billing.entity.User;
import com.billing.entity.UserSession;
import com.billing.entity.enums.RoleName;
import com.billing.entity.enums.UserSessionStatus;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.RefreshTokenRepository;
import com.billing.repository.UserRepository;
import com.billing.repository.UserSessionRepository;
import com.billing.security.RateLimitingFilter;
import com.billing.util.UserAgentParser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * User ke devices (login sessions): login par row, refresh par "abhi active",
 * logout / remote logout par khatam. Access token ka {@code sid} claim yahi
 * session pehchanta hai, isliye kisi device ko hatate hi wo turant bahar.
 */
@Service
@RequiredArgsConstructor
public class UserSessionService {

    /** last_active_at itni der me ek baar hi likhte hain (har request par write nahi). */
    private static final long TOUCH_EVERY_MINUTES = 5;
    /** Khatam hue sessions ki history kitne din. */
    private static final long HISTORY_DAYS = 90;
    /** List me khatam hue sessions zyada se zyada itne. */
    private static final int ENDED_SHOWN = 10;

    private final UserSessionRepository userSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControlService;

    @Value("${app.refresh-token-expiration}")
    private long refreshTokenExpiration;

    /** Naya login: current request ke browser / IP se device row. */
    @Transactional
    public UserSession start(User user) {
        LocalDateTime now = LocalDateTime.now();
        HttpServletRequest request = currentRequest();
        String userAgent = request == null ? null : request.getHeader("User-Agent");
        UserAgentParser.Device device = UserAgentParser.parse(userAgent);
        userSessionRepository.deleteEndedBefore(user, now.minusDays(HISTORY_DAYS));
        return userSessionRepository.save(UserSession.builder()
                .user(user)
                .sessionKey(UUID.randomUUID().toString().replace("-", ""))
                .deviceName(device.displayName())
                .deviceType(device.deviceType())
                .browser(device.browser())
                .os(device.os())
                .ipAddress(request == null ? null : readableIp(RateLimitingFilter.clientIp(request)))
                .userAgent(userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 512)))
                .loginAt(now)
                .lastActiveAt(now)
                .status(UserSessionStatus.ACTIVE)
                .build());
    }

    /**
     * Har authenticated request (JwtAuthenticationFilter): session abhi bhi ACTIVE hai?
     * Saath me har 5 min me ek baar last_active_at update.
     */
    @Transactional
    public boolean isActiveAndTouch(String sessionKey) {
        UserSession session = userSessionRepository.findBySessionKey(sessionKey).orElse(null);
        if (session == null || session.getStatus() != UserSessionStatus.ACTIVE) {
            return false;
        }
        markActive(session);
        return true;
    }

    @Transactional
    public void markActive(UserSession session) {
        LocalDateTime now = LocalDateTime.now();
        if (session.getLastActiveAt() == null || session.getLastActiveAt().isBefore(now.minusMinutes(TOUCH_EVERY_MINUTES))) {
            session.setLastActiveAt(now);
            userSessionRepository.save(session);
        }
    }

    /** Session khatam + uska refresh token delete (us device ka agla refresh fail). */
    @Transactional
    public void end(UserSession session, UserSessionStatus status, String reason) {
        if (session == null || session.getStatus() != UserSessionStatus.ACTIVE) {
            return;
        }
        session.setStatus(status);
        session.setEndedAt(LocalDateTime.now());
        session.setEndedReason(reason);
        userSessionRepository.save(session);
        refreshTokenRepository.deleteBySession(session);
    }

    /** Password badla / reset / user band: saare devices bahar. */
    @Transactional
    public void revokeAll(User user, String reason) {
        for (UserSession session : userSessionRepository.findByUserAndStatus(user, UserSessionStatus.ACTIVE)) {
            end(session, UserSessionStatus.REVOKED, reason);
        }
    }

    @Transactional
    public List<UserSessionResponse> mySessions(String email, String currentSessionKey) {
        return listFor(requireRealUser(email), currentSessionKey);
    }

    @Transactional
    public void logoutMyDevice(String email, Long sessionId, String currentSessionKey) {
        User user = requireRealUser(email);
        UserSession session = sessionOf(user, sessionId);
        if (session.getSessionKey().equals(currentSessionKey)) {
            throw new BadRequestException("This is the device you are using. Use Logout instead.");
        }
        end(session, UserSessionStatus.REVOKED, "Logged out from another device");
    }

    @Transactional
    public int logoutMyOtherDevices(String email, String currentSessionKey) {
        User user = requireRealUser(email);
        int count = 0;
        for (UserSession session : userSessionRepository.findByUserAndStatus(user, UserSessionStatus.ACTIVE)) {
            if (!session.getSessionKey().equals(currentSessionKey)) {
                end(session, UserSessionStatus.REVOKED, "Logged out from another device");
                count++;
            }
        }
        return count;
    }

    @Transactional
    public List<UserSessionResponse> userSessions(String email, Long userId, String currentSessionKey) {
        return listFor(requireManageableUser(email, userId), currentSessionKey);
    }

    @Transactional
    public void logoutUserDevice(String email, Long userId, Long sessionId) {
        User target = requireManageableUser(email, userId);
        User actor = accessControlService.getCurrentUser(email);
        String by = actor.getFullName() == null || actor.getFullName().isBlank() ? "admin" : actor.getFullName();
        end(sessionOf(target, sessionId), UserSessionStatus.REVOKED, "Logged out by " + by);
    }

    private List<UserSessionResponse> listFor(User user, String currentSessionKey) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiredBefore = now.minusSeconds(refreshTokenExpiration / 1000);
        List<UserSession> sessions = userSessionRepository.findByUserOrderByLastActiveAtDesc(user);
        for (UserSession session : sessions) {
            // Refresh token ki umar se zyada der chup = wo device ab login nahi reh sakta.
            if (session.getStatus() == UserSessionStatus.ACTIVE && session.getLastActiveAt().isBefore(expiredBefore)) {
                end(session, UserSessionStatus.EXPIRED, "Session expired");
            }
        }
        List<UserSession> active = sessions.stream().filter(s -> s.getStatus() == UserSessionStatus.ACTIVE).toList();
        List<UserSession> ended = sessions.stream().filter(s -> s.getStatus() != UserSessionStatus.ACTIVE)
                .sorted(Comparator.comparing(UserSession::getEndedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(ENDED_SHOWN)
                .toList();
        return java.util.stream.Stream.concat(active.stream(), ended.stream())
                .map(session -> toResponse(session, currentSessionKey))
                .toList();
    }

    private UserSessionResponse toResponse(UserSession session, String currentSessionKey) {
        return UserSessionResponse.builder()
                .id(session.getId())
                .deviceName(session.getDeviceName())
                .deviceType(session.getDeviceType())
                .browser(session.getBrowser())
                .os(session.getOs())
                .ipAddress(session.getIpAddress())
                .loginAt(session.getLoginAt())
                .lastActiveAt(session.getLastActiveAt())
                .status(session.getStatus().name())
                .endedAt(session.getEndedAt())
                .endedReason(session.getEndedReason())
                .current(session.getSessionKey().equals(currentSessionKey))
                .build();
    }

    private UserSession sessionOf(User user, Long sessionId) {
        UserSession session = userSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found"));
        if (!session.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Device not found");
        }
        return session;
    }

    /** Super-admin (company code login) ka DB user nahi hota, uske devices yahan nahi. */
    private User requireRealUser(String email) {
        User current = accessControlService.getCurrentUser(email);
        if (current.isSuperAdmin() || current.getId() == null) {
            throw new BadRequestException("Devices are not tracked for the super admin login");
        }
        return userRepository.findById(current.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /** Users page: Owner ke devices sirf Owner hata/dekh sakta hai (UserService ka role rule). */
    private User requireManageableUser(String email, Long userId) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User actor = accessControlService.getCurrentUser(email);
        if (target.getRole() == RoleName.OWNER && !accessControlService.isCompanyOwner(actor)) {
            throw new BadRequestException("Only an owner can manage another owner's devices");
        }
        return target;
    }

    /** IPv6 loopback ("0:0:0:0:0:0:0:1") ko padhne layak 127.0.0.1. */
    private static String readableIp(String ip) {
        return "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip) ? "127.0.0.1" : ip;
    }

    private HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }
}
