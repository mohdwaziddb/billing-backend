package com.billing.service;

import com.billing.dto.auth.PasswordResetChallengeResponse;
import com.billing.dto.auth.PasswordResetConfirmRequest;
import com.billing.dto.auth.PasswordResetRequestRequest;
import com.billing.dto.notification.NotificationStatus;
import com.billing.entity.Company;
import com.billing.entity.PasswordResetOtp;
import com.billing.entity.User;
import com.billing.exception.BadRequestException;
import com.billing.exception.UnauthorizedException;
import com.billing.multitenancy.TenantContextHolder;
import com.billing.repository.CompanyRepository;
import com.billing.repository.EmailProviderSettingRepository;
import com.billing.repository.PasswordResetOtpRepository;
import com.billing.repository.RefreshTokenRepository;
import com.billing.repository.SmsProviderSettingRepository;
import com.billing.repository.UserRepository;
import com.billing.service.sms.CommonSmsService;
import com.billing.service.sms.SmsSendResult;
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
 * Rules: 6-digit OTP, BCrypt-hashed at rest, 10-minute expiry, max 5
 * attempts, single active challenge per user, 60-second resend cooldown.
 * Unknown/inactive identifiers get a generic success (no user enumeration).
 * A provider (SMS or email) must be configured or reset is refused outright.
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
    private final CommonSmsService commonSmsService;
    private final AuditLogService auditLogService;
    private final SmsProviderSettingRepository smsProviderSettingRepository;
    private final EmailProviderSettingRepository emailProviderSettingRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public PasswordResetChallengeResponse requestOtp(Map<String, Object> param) {
        return requestOtp(mapToRequest(param));
    }

    @Transactional
    public PasswordResetChallengeResponse requestOtp(PasswordResetRequestRequest request) {
        String identifier = request.getIdentifier() == null ? "" : request.getIdentifier().trim();
        if (identifier.isEmpty()) {
            throw new BadRequestException("Email / Mobile / Username is required");
        }
        String channel = normalizeChannel(request.getChannel());
        java.util.Optional<User> userOpt = findActiveUser(identifier);
        if (userOpt.isEmpty()) {
            // Generic success: never reveal whether the identifier exists.
            return PasswordResetChallengeResponse.builder().build();
        }
        User user = userOpt.get();
        Company company = resolveTenantCompany()
                .orElseThrow(() -> new BadRequestException("Unable to resolve company for this request. Please contact administrator."));
        if (!company.isActive()) {
            return PasswordResetChallengeResponse.builder().build();
        }
        String resolvedChannel = resolveChannel(channel, user, company);
        if (resolvedChannel == null) {
            throw new BadRequestException("Password reset is not available. Please contact your administrator.");
        }
        List<PasswordResetOtp> existing = passwordResetOtpRepository.findByUserIdOrderByIdDesc(user.getId());
        PasswordResetOtp latest = existing.isEmpty() ? null : existing.get(0);
        if (latest != null && !latest.isConsumed()
                && latest.getCreatedAt() != null
                && latest.getCreatedAt().plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
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

        String destination;
        if ("SMS".equals(resolvedChannel)) {
            destination = user.getMobileNumber();
            SmsSendResult result = commonSmsService.sendOtp(company, destination,
                    "Your password reset OTP is " + otp + ". Valid for 10 minutes. Do not share it.");
            if (result.status() != NotificationStatus.SENT) {
                passwordResetOtpRepository.delete(challenge);
                throw new BadRequestException("Unable to send OTP right now. Please try again later.");
            }
        } else {
            destination = user.getEmail();
            var result = emailService.sendEmail(company,
                    "Password reset OTP",
                    "<p>Your password reset OTP is <b>" + otp + "</b>. Valid for 10 minutes. Do not share it.</p>",
                    List.of(destination), null, null, null, "system");
            if (result.status() != NotificationStatus.SENT) {
                passwordResetOtpRepository.delete(challenge);
                throw new BadRequestException("Unable to send OTP right now. Please try again later.");
            }
        }
        return PasswordResetChallengeResponse.builder()
                .challengeId(challenge.getId())
                .channel(resolvedChannel)
                .maskedDestination(maskDestination(resolvedChannel, destination))
                .expiresInSeconds(OTP_TTL_SECONDS)
                .build();
    }

    @Transactional
    public void confirmOtp(Map<String, Object> param) {
        confirmOtp(mapToConfirm(param));
    }

    @Transactional
    public void confirmOtp(PasswordResetConfirmRequest request) {
        if (request.getChallengeId() == null) {
            throw new BadRequestException("Invalid OTP request. Please request a new OTP.");
        }
        String otp = request.getOtp() == null ? "" : request.getOtp().trim();
        if (otp.isEmpty()) {
            throw new BadRequestException("OTP is required");
        }
        String newPassword = request.getNewPassword() == null ? "" : request.getNewPassword().trim();
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BadRequestException("New password must be at least 8 characters");
        }
        PasswordResetOtp challenge = passwordResetOtpRepository.findById(request.getChallengeId())
                .orElseThrow(() -> new BadRequestException("Invalid OTP request. Please request a new OTP."));
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
        User user = userRepository.findById(challenge.getUserId())
                .orElseThrow(() -> new BadRequestException("Invalid OTP request. Please request a new OTP."));
        if (!user.isActive()) {
            throw new BadRequestException("This user account is inactive.");
        }
        challenge.setConsumed(true);
        passwordResetOtpRepository.save(challenge);
        passwordResetOtpRepository.deleteByUserId(user.getId());
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenRepository.deleteByUser(user);
        auditPasswordReset(user);
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

    private String resolveChannel(String requested, User user, Company company) {
        if (requested != null) {
            if ("SMS".equals(requested)
                    && user.getMobileNumber() != null && !user.getMobileNumber().isBlank()
                    && hasSmsProvider(company)) {
                return "SMS";
            }
            if ("EMAIL".equals(requested)
                    && user.getEmail() != null && !user.getEmail().isBlank()
                    && hasEmailProvider(company)) {
                return "EMAIL";
            }
            return null;
        }
        if (user.getMobileNumber() != null && !user.getMobileNumber().isBlank() && hasSmsProvider(company)) {
            return "SMS";
        }
        if (user.getEmail() != null && !user.getEmail().isBlank() && hasEmailProvider(company)) {
            return "EMAIL";
        }
        return null;
    }

    private boolean hasSmsProvider(Company company) {
        try {
            return smsProviderSettingRepository.findFirstByCompanyAndActiveTrueOrderByIdDesc(company).isPresent();
        } catch (Exception ignored) {
            return false;
        }
    }

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
        if ("SMS".equals(channel)) {
            String digits = value.replaceAll("\\D", "");
            if (digits.length() <= 4) {
                return "XXXX";
            }
            return "XXXXXX" + digits.substring(digits.length() - 4);
        }
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

    private String normalizeChannel(String channel) {
        if (channel == null) {
            return null;
        }
        String value = channel.trim().toUpperCase(Locale.ROOT);
        if ("SMS".equals(value) || "EMAIL".equals(value)) {
            return value;
        }
        return null;
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
        request.setChannel(normalizeChannel(DataTypeUtility.stringValue(param.get("channel"))));
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
        return request;
    }
}
