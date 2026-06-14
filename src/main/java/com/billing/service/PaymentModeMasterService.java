package com.billing.service;

import com.billing.dto.PageResponse;
import com.billing.dto.paymentmode.PaymentModeRequest;
import com.billing.dto.paymentmode.PaymentModeResponse;
import com.billing.entity.Company;
import com.billing.entity.PaymentModeMaster;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.PaymentModeMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentModeMasterService {

    private static final List<String> DEFAULT_MODES = List.of("Cash", "UPI", "Card", "Bank Transfer", "Cheque", "Wallet", "Other");

    private final PaymentModeMasterRepository paymentModeMasterRepository;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;
    private final AuditNameResolver auditNameResolver;

    @Transactional
    public List<PaymentModeResponse> list(String email, String search, Boolean active) {
        Company company = accessControlService.getCurrentCompany(email);
        ensureDefaults(company);
        return paymentModeMasterRepository.findAllByCompanyWithFilters(company, active, normalizeSearch(search)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PageResponse<PaymentModeResponse> page(String email, String search, Boolean active, int page, int size) {
        Company company = accessControlService.getCurrentCompany(email);
        ensureDefaults(company);
        return PageResponse.from(paymentModeMasterRepository.findPageByCompanyWithFilters(company, active, normalizeSearch(search), pageRequest(page, size))
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PaymentModeResponse get(String email, Long modeId) {
        Company company = accessControlService.getCurrentCompany(email);
        return toResponse(getModeOrThrow(company, modeId));
    }

    @Transactional
    public PaymentModeResponse create(String email, PaymentModeRequest request) {
        Company company = accessControlService.requireOwnerCompany(email);
        String modeName = normalizeName(request.getModeName());
        String modeCode = toCode(modeName);
        validateUnique(company, modeName, modeCode, null);

        PaymentModeMaster mode = PaymentModeMaster.builder()
                .company(company)
                .modeName(modeName)
                .modeCode(modeCode)
                .description(blankToNull(request.getDescription()))
                .active(Boolean.TRUE.equals(request.getActive()))
                .build();
        PaymentModeMaster saved = paymentModeMasterRepository.save(mode);
        auditLogService.logCreate(email, company, "Payment Mode", "PaymentMode", saved.getId(), snapshot(saved));
        return toResponse(saved);
    }

    @Transactional
    public PaymentModeResponse update(String email, Long modeId, PaymentModeRequest request) {
        Company company = accessControlService.requireOwnerCompany(email);
        PaymentModeMaster mode = getModeOrThrow(company, modeId);
        Map<String, Object> oldData = snapshot(mode);
        String modeName = normalizeName(request.getModeName());
        String modeCode = toCode(modeName);
        validateUnique(company, modeName, modeCode, modeId);

        mode.setModeName(modeName);
        mode.setModeCode(modeCode);
        mode.setDescription(blankToNull(request.getDescription()));
        mode.setActive(Boolean.TRUE.equals(request.getActive()));
        PaymentModeMaster saved = paymentModeMasterRepository.save(mode);
        auditLogService.logUpdate(email, company, "Payment Mode", "PaymentMode", saved.getId(), oldData, snapshot(saved));
        return toResponse(saved);
    }

    @Transactional
    public void delete(String email, Long modeId) {
        Company company = accessControlService.requireOwnerCompany(email);
        PaymentModeMaster mode = getModeOrThrow(company, modeId);
        Map<String, Object> oldData = snapshot(mode);
        mode.setActive(false);
        PaymentModeMaster saved = paymentModeMasterRepository.save(mode);
        auditLogService.logDelete(email, company, "Payment Mode", "PaymentMode", saved.getId(), oldData);
    }

    @Transactional
    public void ensureDefaults(Company company) {
        for (String modeName : DEFAULT_MODES) {
            String modeCode = toCode(modeName);
            if (!paymentModeMasterRepository.existsByCompanyAndModeCodeIgnoreCase(company, modeCode)) {
                paymentModeMasterRepository.save(PaymentModeMaster.builder()
                        .company(company)
                        .modeName(modeName)
                        .modeCode(modeCode)
                        .active(true)
                        .build());
            }
        }
    }

    public String requireActiveModeCode(Company company, String mode) {
        String modeCode = toCode(mode);
        paymentModeMasterRepository.findByCompanyAndModeCodeIgnoreCaseAndActiveTrue(company, modeCode)
                .orElseThrow(() -> new BadRequestException("Select an active payment mode"));
        return modeCode;
    }

    public Map<String, String> activeModeLabels(Company company) {
        return paymentModeMasterRepository.findAllByCompanyWithFilters(company, true, null).stream()
                .collect(LinkedHashMap::new, (map, mode) -> map.put(mode.getModeCode(), mode.getModeName()), LinkedHashMap::putAll);
    }

    private PaymentModeMaster getModeOrThrow(Company company, Long modeId) {
        return paymentModeMasterRepository.findByIdAndCompany(modeId, company)
                .orElseThrow(() -> new ResourceNotFoundException("Payment mode not found"));
    }

    private void validateUnique(Company company, String modeName, String modeCode, Long excludedId) {
        boolean codeExists = excludedId == null
                ? paymentModeMasterRepository.existsByCompanyAndModeCodeIgnoreCase(company, modeCode)
                : paymentModeMasterRepository.existsByCompanyAndModeCodeIgnoreCaseAndIdNot(company, modeCode, excludedId);
        if (codeExists) {
            throw new BadRequestException("Payment mode already exists");
        }
        boolean nameExists = excludedId == null
                ? paymentModeMasterRepository.existsByCompanyAndModeNameIgnoreCase(company, modeName)
                : paymentModeMasterRepository.existsByCompanyAndModeNameIgnoreCaseAndIdNot(company, modeName, excludedId);
        if (nameExists) {
            throw new BadRequestException("Payment mode already exists");
        }
    }

    private PaymentModeResponse toResponse(PaymentModeMaster mode) {
        return PaymentModeResponse.builder()
                .id(mode.getId())
                .modeName(mode.getModeName())
                .modeCode(mode.getModeCode())
                .description(mode.getDescription())
                .active(mode.isActive())
                .createdAt(mode.getCreatedAt())
                .updatedAt(mode.getUpdatedAt())
                .createdBy(auditNameResolver.displayName(mode.getCreatedBy()))
                .updatedBy(auditNameResolver.displayName(mode.getUpdatedBy()))
                .build();
    }

    private Map<String, Object> snapshot(PaymentModeMaster mode) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("modeName", mode.getModeName());
        data.put("modeCode", mode.getModeCode());
        data.put("description", mode.getDescription());
        data.put("active", mode.isActive());
        return data;
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Payment mode name is required");
        }
        return value.trim();
    }

    private String toCode(String value) {
        String normalized = Normalizer.normalize(normalizeName(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ENGLISH)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            throw new BadRequestException("Payment mode name is required");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeSearch(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
    }
}
