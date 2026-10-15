package com.billing.service;

import com.billing.dto.mfa.MfaSetupStartResponse;
import com.billing.dto.mfa.MfaStatusResponse;
import com.billing.entity.Company;
import com.billing.entity.User;
import com.billing.entity.UserMfaDevice;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.UserMfaDeviceRepository;
import com.billing.repository.UserRepository;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MfaService {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final long LOCK_MINUTES = 15L;
    private static final int MAX_DEVICES = 10;

    private final UserRepository userRepository;
    private final UserMfaDeviceRepository userMfaDeviceRepository;
    private final AccessControlService accessControlService;
    private final TotpService totpService;
    private final SecretEncryptionService secretEncryptionService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public MfaStatusResponse getStatus(Map<String, Object> param, String email) {
        return getStatus(email);
    }

    @Transactional(readOnly = true)
    public MfaStatusResponse getStatus(String email) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        List<UserMfaDevice> devices = userMfaDeviceRepository.findByUserIdOrderByIdAsc(user.getId());
        List<MfaStatusResponse.MfaDeviceResponse> deviceResponses = new ArrayList<>();
        boolean hasEnabled = false;
        for (UserMfaDevice device : devices) {
            if (device.isEnabled()) {
                hasEnabled = true;
                deviceResponses.add(MfaStatusResponse.MfaDeviceResponse.builder()
                        .id(device.getId())
                        .deviceName(device.getDeviceName())
                        .enrolledAt(device.getEnrolledAt())
                        .lastUsedAt(device.getLastUsedAt())
                        .build());
            }
        }
        return MfaStatusResponse.builder()
                .applyMfaByCompany(company.isApplyMfa())
                .mfaEnabled(hasEnabled)
                .devices(deviceResponses)
                .build();
    }

    @Transactional
    public MfaSetupStartResponse startSetup(Map<String, Object> param, String email) {
        String deviceName = DataTypeUtility.stringValue(param != null ? param.get("deviceName") : null);
        if (deviceName.isEmpty()) {
            deviceName = DataTypeUtility.stringValue(param != null ? param.get("device_name") : null);
        }
        if (deviceName.isEmpty()) {
            deviceName = DataTypeUtility.stringValue(param != null ? param.get("label") : null);
        }
        String code = DataTypeUtility.stringValue(param != null ? param.get("code") : null);
        if (!code.isEmpty()) {
            throw new BadRequestException("Code start step me mat bhejo, pehle QR scan karo phir confirm par code bhejo");
        }
        return startSetup(email, deviceName);
    }

    @Transactional
    public MfaSetupStartResponse startSetup(String email, String deviceName) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        if (!company.isApplyMfa()) {
            throw new BadRequestException("Company ne MFA enable nahi kiya hai. Admin se sampark kare");
        }
        String normalizedDevice = normalizeDeviceName(deviceName);
        List<UserMfaDevice> existing = userMfaDeviceRepository.findByUserIdOrderByIdAsc(user.getId());
        long enabledCount = existing.stream().filter(UserMfaDevice::isEnabled).count();
        if (enabledCount >= MAX_DEVICES) {
            throw new BadRequestException("Maximum " + MAX_DEVICES + " devices allowed. Pehle purana device hatao");
        }
        for (UserMfaDevice oldPending : existing) {
            if (!oldPending.isEnabled()
                    && oldPending.getDeviceName() != null
                    && oldPending.getDeviceName().equalsIgnoreCase(normalizedDevice)) {
                userMfaDeviceRepository.delete(oldPending);
            }
        }
        String secret = totpService.generateSecret();
        String encrypted = secretEncryptionService.encrypt(secret);
        UserMfaDevice pending = UserMfaDevice.builder()
                .userId(user.getId())
                .deviceName(normalizedDevice)
                .secretEncrypted(encrypted)
                .enabled(false)
                .failedAttempts(0)
                .build();
        userMfaDeviceRepository.save(pending);
        String accountName = user.getUsername() != null && !user.getUsername().isBlank()
                ? user.getUsername().trim()
                : user.getEmail();
        String otpAuthUrl = totpService.buildOtpAuthUrl(secret, accountName, company.getCode(), normalizedDevice);
        String qrBase64 = totpService.buildQrBase64(otpAuthUrl);
        writeAudit(email, company, "CREATE", pending.getId(), Map.of("deviceName", normalizedDevice));
        return MfaSetupStartResponse.builder()
                .deviceName(normalizedDevice)
                .otpAuthUrl(otpAuthUrl)
                .qrBase64(qrBase64)
                .secret(secret)
                .build();
    }

    @Transactional
    public Map<String, Object> confirmSetup(Map<String, Object> param, String email) {
        String deviceName = DataTypeUtility.stringValue(param != null ? param.get("deviceName") : null);
        if (deviceName.isEmpty()) {
            deviceName = DataTypeUtility.stringValue(param != null ? param.get("device_name") : null);
        }
        String code = DataTypeUtility.stringValue(param != null ? param.get("code") : null);
        if (code.isEmpty()) {
            code = DataTypeUtility.stringValue(param != null ? param.get("otp") : null);
        }
        return confirmSetup(email, deviceName, code);
    }

    @Transactional
    public Map<String, Object> confirmSetup(String email, String deviceName, String code) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        if (!company.isApplyMfa()) {
            throw new BadRequestException("Company ne MFA enable nahi kiya hai. Admin se sampark kare");
        }
        String normalizedDevice = normalizeDeviceName(deviceName);
        String normalizedCode = DataTypeUtility.stringValue(code).trim();
        if (normalizedCode.isEmpty()) {
            throw new BadRequestException("Verification code required");
        }
        UserMfaDevice pending = userMfaDeviceRepository
                .findFirstByUserIdAndDeviceNameIgnoreCaseOrderByIdDesc(user.getId(), normalizedDevice)
                .orElseThrow(() -> new BadRequestException("Pehle Add dabakar QR nikalo, phir code dalo"));
        if (pending.isEnabled()) {
            throw new BadRequestException("Ye device pehle se enabled hai");
        }
        String secret = secretEncryptionService.decrypt(pending.getSecretEncrypted());
        if (secret == null || secret.isBlank()) {
            userMfaDeviceRepository.delete(pending);
            throw new BadRequestException("Setup expire ho gaya. Dobara QR nikalo");
        }
        if (!totpService.verifyCode(secret, normalizedCode)) {
            throw new BadRequestException("Galat code. Authenticator me sahi 6-digit code dalo");
        }
        pending.setEnabled(true);
        pending.setEnrolledAt(LocalDateTime.now());
        pending.setFailedAttempts(0);
        pending.setLockedUntil(null);
        userMfaDeviceRepository.save(pending);
        writeAudit(email, company, "CREATE", pending.getId(), Map.of("deviceName", normalizedDevice, "enabled", true));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deviceId", pending.getId());
        data.put("deviceName", pending.getDeviceName());
        data.put("mfaEnabled", true);
        return data;
    }

    @Transactional
    public void deleteDevice(String email, Long deviceId) {
        Long normalizedId = DataTypeUtility.getForeignKeyValue(deviceId);
        if (normalizedId == null) {
            throw new BadRequestException("deviceId required");
        }
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        UserMfaDevice device = userMfaDeviceRepository.findById(normalizedId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found"));
        if (!device.getUserId().equals(user.getId())) {
            throw new AccessDeniedException("Ye device aapka nahi hai");
        }
        userMfaDeviceRepository.delete(device);
        writeAudit(email, company, "DELETE", normalizedId, Map.of("deviceName", String.valueOf(device.getDeviceName())));
    }

    @Transactional
    public Map<String, Object> resetUserMfa(String adminEmail, Long targetUserId) {
        Long normalizedTarget = DataTypeUtility.getForeignKeyValue(targetUserId);
        if (normalizedTarget == null) {
            throw new BadRequestException("userId required");
        }
        Company company = accessControlService.requireOwnerCompany(adminEmail);
        User target = userRepository.findById(normalizedTarget)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<UserMfaDevice> devices = userMfaDeviceRepository.findByUserIdOrderByIdAsc(normalizedTarget);
        int removed = devices.size();
        userMfaDeviceRepository.deleteByUserId(normalizedTarget);
        writeAudit(adminEmail, company, "DELETE", normalizedTarget,
                Map.of("resetUser", String.valueOf(target.getUsername()), "removedDevices", removed));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", normalizedTarget);
        data.put("removedDevices", removed);
        data.put("mfaEnabled", false);
        return data;
    }

    @Transactional(readOnly = true)
    public boolean hasEnabledDevices(Long userId) {
        Long normalizedId = DataTypeUtility.getForeignKeyValue(userId);
        if (normalizedId == null) {
            return false;
        }
        return !userMfaDeviceRepository.findByUserIdAndEnabledTrue(normalizedId).isEmpty();
    }

    private String normalizeDeviceName(String deviceName) {
        String normalized = DataTypeUtility.stringValue(deviceName).trim();
        if (normalized.isEmpty()) {
            throw new BadRequestException("deviceName required (jaise Phone ya Desktop)");
        }
        if (normalized.length() > 50) {
            normalized = normalized.substring(0, 50);
        }
        return normalized;
    }

    private void writeAudit(String email, Company company, String action, Long entityId, Map<String, Object> data) {
        try {
            if ("DELETE".equals(action)) {
                auditLogService.logCreate(email, company, "MFA", "user_mfa_device", entityId, data);
            } else {
                auditLogService.logCreate(email, company, "MFA", "user_mfa_device", entityId, data);
            }
        } catch (Exception ignored) {
            // Audit kabhi MFA flow block nahi karega
        }
    }
}
