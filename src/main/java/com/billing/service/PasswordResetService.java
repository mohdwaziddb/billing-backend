package com.billing.service;

import com.billing.core.appconfig.ProviderMessages;
import com.billing.dto.auth.PasswordResetChallengeResponse;
import com.billing.dto.auth.PasswordResetConfirmRequest;
import com.billing.dto.auth.PasswordResetRequestRequest;
import com.billing.dto.notification.NotificationChannelType;
import com.billing.dto.notification.NotificationStatus;
import com.billing.entity.Company;
import com.billing.entity.PasswordResetOtp;
import com.billing.entity.User;
import com.billing.exception.BadRequestException;
import com.billing.multitenancy.TenantContextHolder;
import com.billing.repository.CompanyRepository;
import com.billing.repository.EmailProviderSettingRepository;
import com.billing.repository.PasswordResetOtpRepository;
import com.billing.repository.RefreshTokenRepository;
import com.billing.repository.UserRepository;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OTP-gated password reset. The old direct-set flow (identifier + new
 * password, no proof of ownership) is intentionally gone: anyone knowing an
 * email/mobile could hijack any account with it.
 *
 * Rules: 6-digit OTP over EMAIL ONLY, BCrypt-hashed at rest, 10-minute
 * expiry, max 5 attempts, single active challenge per user, 60-second
 * resend cooldown. Unknown/inactive identifiers get a generic success
 * (no user enumeration). An active EMAIL provider must be configured or
 * reset is refused outright. Flow is 3-step: request OTP -> verify OTP ->
 * set new + confirm password (confirm re-checks the OTP, so skipping the
 * verify step via direct API call cannot bypass proof of ownership).
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int OTP_LENGTH = 6;
    private static final long OTP_TTL_SECONDS = 600L;
    private static final int MAX_ATTEMPTS = 5;
    private static final long RESEND_COOLDOWN_SECONDS = 60L;
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final EmailProviderSettingRepository emailProviderSettingRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public PasswordResetChallengeResponse requestOtp(Map<String, Object> param) {
        return requestOtp(mapToRequest(param), null);
    }

    @Transactional
    public PasswordResetChallengeResponse requestOtp(Map<String, Object> param, Long authenticatedUserId) {
        return requestOtp(mapToRequest(param), authenticatedUserId);
    }

    @Transactional
    public PasswordResetChallengeResponse requestOtp(PasswordResetRequestRequest request) {
        return requestOtp(request, null);
    }

    @Transactional
    public PasswordResetChallengeResponse requestOtp(PasswordResetRequestRequest request, Long authenticatedUserId) {
        boolean publicFlow = authenticatedUserId == null;
        // Company-level checks user lookup se PEHLE: inka jawab har identifier
        // ke liye same hai, isliye account exist karta hai ya nahi ye nahi batate.
        Company company = resolveTenantCompany()
                .orElseThrow(() -> new BadRequestException("Unable to resolve company for this request. Please contact administrator."));
        if (!company.isActive()) {
            return PasswordResetChallengeResponse.builder().build();
        }
        if (!hasEmailProvider(company)) {
            throw new BadRequestException(ProviderMessages.EMAIL_NOT_CONFIGURED_USER);
        }
        User user;
        if (authenticatedUserId != null) {
            // Logged-in dashboard flow: the recipient is ALWAYS the
            // authenticated account — the posted identifier is ignored
            // entirely, so UI/DevTools tampering cannot redirect the OTP.
            user = userRepository.findById(authenticatedUserId)
                    .filter(User::isActive)
                    .orElse(null);
            if (user == null) {
                // Generic success: mirrors the identifier flow's
                // no-user-enumeration behaviour.
                return PasswordResetChallengeResponse.builder().build();
            }
        } else {
            String identifier = request.getIdentifier() == null ? "" : request.getIdentifier().trim();
            if (identifier.isEmpty()) {
                throw new BadRequestException("Email / Mobile / Username is required");
            }
            java.util.Optional<User> userOpt = findActiveUser(identifier);
            if (userOpt.isEmpty()) {
                // Never reveal whether the identifier exists: same shape as a real challenge.
                return decoyChallenge();
            }
            user = userOpt.get();
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            if (publicFlow) {
                return decoyChallenge();
            }
            throw new BadRequestException("No registered email address on this account. Please contact your administrator.");
        }
        // EMAIL ONLY: OTP always goes to the registered email.
        String resolvedChannel = "EMAIL";
        List<PasswordResetOtp> existing = passwordResetOtpRepository.findByUserIdOrderByIdDesc(user.getId());
        PasswordResetOtp latest = existing.isEmpty() ? null : existing.get(0);
        if (latest != null && !latest.isConsumed()
                && latest.getCreatedAt() != null
                && latest.getCreatedAt().plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
            if (publicFlow) {
                // Cooldown error sirf asli account par aata, isliye public flow me chup.
                return decoyChallenge();
            }
            throw new BadRequestException("Please wait a minute before requesting a new OTP.");
        }
        passwordResetOtpRepository.deleteByUserId(user.getId());

        String otp = generateOtp();
        PasswordResetOtp challenge = PasswordResetOtp.builder()
                .userId(user.getId())
                .otpHash(passwordEncoder.encode(otp))
                .channel(resolvedChannel)
                .expiresAt(LocalDateTime.now().plusSeconds(OTP_TTL_SECONDS))
                .attempts(0)
                .consumed(false)
                .build();
        challenge = passwordResetOtpRepository.save(challenge);

        // EMAIL ONLY: no SMS branch (removed). The destination is always the
        // user's registered email address.
        String destination = user.getEmail();
        var result = emailService.sendEmail(company,
                "Password reset OTP",
                "<p>Your password reset OTP is <b>" + otp + "</b>. Valid for 10 minutes. Do not share it.</p>"
                        + emailService.companySignatureHtml(company),
                List.of(destination), null, null, null, "system");
        // History (Mail): committed in its own transaction so failures are
        // recorded too. Owner decision: the exact OTP mail (code included) is
        // stored so Mail History shows what actually went. Anyone with history
        // access can read live OTPs — treat that permission accordingly.
        notificationService.logDirectNotification(company, NotificationChannelType.EMAIL, destination,
                "Password reset OTP", "<p>Your password reset OTP is <b>" + otp + "</b>. Valid for 10 minutes. Do not share it.</p>"
                        + emailService.companySignatureHtml(company),
                "EMAIL", null, null, result.status(),
                result.status() != NotificationStatus.SENT ? result.providerResponse() : null, "system");
        if (result.status() != NotificationStatus.SENT) {
            passwordResetOtpRepository.delete(challenge);
            throw new BadRequestException("Unable to send OTP right now. Please try again later.");
        }
        return PasswordResetChallengeResponse.builder()
                .challengeId(challenge.getId())
                .channel(resolvedChannel)
                // Public flow me masked email nahi: decoy response me ye ho nahi sakta,
                // to iske hone/na hone se account ka pata chal jata.
                .maskedDestination(publicFlow ? null : maskDestination(resolvedChannel, destination))
                .expiresInSeconds(OTP_TTL_SECONDS)
                .build();
    }

    /**
     * Unknown / email-less / cooldown identifiers ke liye asli jaisa challenge.
     * ID itna bada hai ki kisi real row se kabhi nahi takrata; verify par
     * "Incorrect OTP" milta hai, bilkul galat OTP jaisa.
     */
    private PasswordResetChallengeResponse decoyChallenge() {
        long decoyId = 1_000_000_000_000L + (secureRandom.nextLong() & 0xFFFFFFFFFFL);
        return PasswordResetChallengeResponse.builder()
                .challengeId(decoyId)
                .channel("EMAIL")
                .expiresInSeconds(OTP_TTL_SECONDS)
                .build();
    }

    @Transactional
    public void confirmOtp(Map<String, Object> param) {
        confirmOtp(mapToConfirm(param));
    }

    /**
     * Step 2 of 3: OTP-only check. Never consumes the challenge and never
     * touches the password — it only proves ownership so the UI can reveal
     * the new-password fields. Wrong guesses count toward MAX_ATTEMPTS.
     */
    @Transactional
    public void verifyOtp(Map<String, Object> param) {
        PasswordResetConfirmRequest request = mapToConfirm(param);
        requireValidChallenge(request.getChallengeId(), request.getOtp());
    }

    @Transactional
    public void confirmOtp(PasswordResetConfirmRequest request) {
        String newPassword = request.getNewPassword() == null ? "" : request.getNewPassword().trim();
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BadRequestException("New password must be at least 8 characters");
        }
        String confirmPassword = request.getConfirmPassword() == null ? "" : request.getConfirmPassword().trim();
        if (!newPassword.equals(confirmPassword)) {
            throw new BadRequestException("New password and confirm password must match.");
        }
        // Re-checks the OTP itself: skipping the verify step via direct API
        // call cannot bypass proof of ownership.
        PasswordResetOtp challenge = requireValidChallenge(request.getChallengeId(), request.getOtp());
        User user = userRepository.findById(challenge.getUserId())
                .orElseThrow(() -> new BadRequestException("Invalid OTP request. Please request a new OTP."));
        if (!user.isActive()) {
            throw new BadRequestException("This user account is inactive.");
        }
        challenge.setConsumed(true);
        passwordResetOtpRepository.save(challenge);
        passwordResetOtpRepository.deleteByUserId(user.getId());
        user.setPassword(passwordEncoder.encode(newPassword));
        // Reset se pehle ke saare access tokens band (refresh tokens neeche delete).
        user.setTokensValidAfter(LocalDateTime.now());
        userRepository.save(user);
        refreshTokenRepository.deleteByUser(user);
        auditPasswordReset(user);
    }

    private PasswordResetOtp requireValidChallenge(Long challengeId, String otpRaw) {
        if (challengeId == null) {
            throw new BadRequestException("Invalid OTP request. Please request a new OTP.");
        }
        String otp = otpRaw == null ? "" : otpRaw.trim();
        if (otp.isEmpty()) {
            throw new BadRequestException("OTP is required");
        }
        // Decoy challenge (unknown account) bhi galat OTP jaisa hi dikhe.
        PasswordResetOtp challenge = passwordResetOtpRepository.findById(challengeId)
                .orElseThrow(() -> new BadRequestException("Incorrect OTP. Please try again."));
        if (challenge.isConsumed()
                || challenge.getExpiresAt() == null
                || challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("OTP has expired. Please request a new OTP.");
        }
        if (challenge.getAttempts() >= MAX_ATTEMPTS) {
            challenge.setConsumed(true);
            passwordResetOtpRepository.save(challenge);
            throw new BadRequestException("Too many wrong attempts. Please request a new OTP.");
        }
        if (!passwordEncoder.matches(otp, challenge.getOtpHash())) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            if (challenge.getAttempts() >= MAX_ATTEMPTS) {
                challenge.setConsumed(true);
            }
            passwordResetOtpRepository.save(challenge);
            throw new BadRequestException("Incorrect OTP. Please try again.");
        }
        return challenge;
    }

    private void auditPasswordReset(User user) {
        try {
            Company company = resolveTenantCompany().orElse(null);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("password", "[PROTECTED]");
            auditLogService.logUpdate(user.getEmail(), company, "User", "User",
                    user.getId(), data, data);
        } catch (Exception ignored) {
            // Audit must never block a password reset.
        }
    }

    private java.util.Optional<User> findActiveUser(String identifier) {
        List<User> candidates = new ArrayList<>();
        candidates.addAll(userRepository.findAllByUsernameIgnoreCase(identifier));
        candidates.addAll(userRepository.findAllByEmailIgnoreCase(identifier));
        candidates.addAll(userRepository.findAllByMobileNumber(identifier));
        Map<Long, User> byId = new LinkedHashMap<>();
        for (User candidate : candidates) {
            byId.putIfAbsent(candidate.getId(), candidate);
        }
        List<User> unique = new ArrayList<>(byId.values());
        if (unique.size() != 1) {
            return java.util.Optional.empty();
        }
        User user = unique.get(0);
        return user.isActive() ? java.util.Optional.of(user) : java.util.Optional.empty();
    }

    // EMAIL ONLY: the requested channel (old clients may still send one) is
    // intentionally ignored — OTP always goes to the registered email.
    private boolean hasEmailProvider(Company company) {
        try {
            return emailProviderSettingRepository.findFirstByCompanyAndActiveTrueOrderByIdDesc(company).isPresent();
        } catch (Exception ignored) {
            return false;
        }
    }

    private String maskDestination(String channel, String destination) {
        if (destination == null) {
            return "";
        }
        String value = destination.trim();
        int at = value.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return value.charAt(0) + "***" + value.substring(at);
    }

    private String generateOtp() {
        int number = secureRandom.nextInt(900000) + 100000;
        return String.valueOf(number);
    }

    private java.util.Optional<Company> resolveTenantCompany() {
        String tenant = TenantContextHolder.getTenant();
        if (tenant == null || tenant.isBlank()) {
            return java.util.Optional.empty();
        }
        String db = tenant.replace("_read", "").replace("_write", "").trim();
        if (db.isEmpty()) {
            return java.util.Optional.empty();
        }
        try {
            return companyRepository.findByCodeIgnoreCase(db.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return java.util.Optional.empty();
        }
    }

    private PasswordResetRequestRequest mapToRequest(Map<String, Object> param) {
        PasswordResetRequestRequest request = new PasswordResetRequestRequest();
        String identifier = DataTypeUtility.stringValue(param.get("identifier"));
        if (identifier.length() == 0) {
            identifier = DataTypeUtility.stringValue(param.get("username"));
        }
        if (identifier.length() == 0) {
            identifier = DataTypeUtility.stringValue(param.get("email"));
        }
        if (identifier.length() == 0) {
            identifier = DataTypeUtility.stringValue(param.get("mobileNumber"));
        }
        if (identifier.length() == 0) {
            identifier = DataTypeUtility.stringValue(param.get("mobile"));
        }
        request.setIdentifier(identifier);
        // Channel ignored: OTP is always emailed (see resolveChannel).
        return request;
    }

    private PasswordResetConfirmRequest mapToConfirm(Map<String, Object> param) {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest();
        Object challengeRaw = param.get("challengeId");
        if (challengeRaw == null) {
            challengeRaw = param.get("challenge_id");
        }
        try {
            request.setChallengeId(challengeRaw == null ? null : Long.valueOf(String.valueOf(challengeRaw).trim()));
        } catch (Exception ignored) {
            request.setChallengeId(null);
        }
        request.setOtp(DataTypeUtility.stringValue(param.get("otp")));
        String password = DataTypeUtility.stringValue(param.get("newPassword"));
        if (password.length() == 0) {
            password = DataTypeUtility.stringValue(param.get("password"));
        }
        request.setNewPassword(password);
        String confirmPassword = DataTypeUtility.stringValue(param.get("confirmPassword"));
        if (confirmPassword.length() == 0) {
            confirmPassword = DataTypeUtility.stringValue(param.get("confirm_password"));
        }
        request.setConfirmPassword(confirmPassword);
        return request;
    }
}
