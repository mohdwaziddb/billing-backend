package com.billing.service;

import com.billing.dto.PageResponse;
import com.billing.dto.paymentmode.PaymentModeRequest;
import com.billing.dto.paymentmode.PaymentModeResponse;
import com.billing.core.appconfig.ApplicationConstant;
import com.billing.entity.Company;
import com.billing.entity.PaymentModeMaster;
import com.billing.entity.enums.PaymentModeType;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.multitenancy.TenantContextHolder;
import com.billing.repository.PaymentModeMasterRepository;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    // Sirf Cash (counter) + Online (gateway, dropdown me gateway ke naam se — e.g. "Razorpay").
    // UPI / Card ab gateway checkout ke andar aate hain; purane default modes V20261023_04 se delete.
    private static final List<String> DEFAULT_MODES = List.of("Cash", "Online");

    /** Gateway (Razorpay etc.) se aayi payments isi mode code se record hoti hain. */
    public static final String ONLINE_MODE_CODE = "ONLINE";


    private static final Logger log = LoggerFactory.getLogger(PaymentModeMasterService.class);

    private final PaymentModeMasterRepository paymentModeMasterRepository;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;
    private final AuditNameResolver auditNameResolver;

    @Transactional(readOnly = true)
    public List<PaymentModeResponse> list(String email, String search, Boolean active) {
        Company company = accessControlService.getCurrentCompany(email);
        return paymentModeMasterRepository.findAllByCompanyWithFilters(company, active, normalizeSearch(search)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentModeResponse> page(String email, String search, Boolean active, int page, int size) {
        Company company = accessControlService.getCurrentCompany(email);
        return PageResponse.from(paymentModeMasterRepository.findPageByCompanyWithFilters(company, active, normalizeSearch(search), pageRequest(page, size))
                .map(this::toResponse));
    }

    @Transactional
    public PageResponse<PaymentModeResponse> page(Map<String, Object> param, String email) {
        String search = DataTypeUtility.stringValue(param.get("search"));
        if (search.length() == 0) {
            search = null;
        }
        Boolean active = null;
        Object activeRaw = param.get("active");
        if (activeRaw != null) {
            String activeStr = DataTypeUtility.stringValue(activeRaw);
            if (activeStr.length() > 0) {
                active = DataTypeUtility.booleanValue(activeRaw);
            }
        }
        int page = DataTypeUtility.integerValue(param.get("page"));
        int size = DataTypeUtility.integerValue(param.get("size"));
        if (size == 0) {
            size = 20;
        }
        return page(email, search, active, page, size);
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
                .active(request.getActive() == null || request.getActive())
                .modeType(resolveModeType(modeCode, request.getModeType()))
                .requiresReference(request.getRequiresReference() != null && request.getRequiresReference())
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
        // Gateway payments isi code se record hoti hain; rename se code toot jaata.
        if (ONLINE_MODE_CODE.equals(mode.getModeCode()) && !ONLINE_MODE_CODE.equals(modeCode)) {
            throw new BadRequestException("Online payment mode cannot be renamed");
        }

        mode.setModeName(modeName);
        mode.setModeCode(modeCode);
        mode.setDescription(blankToNull(request.getDescription()));
        // active na bheja ho to purani value rahe (pehle chup-chaap inactive ho jata tha).
        if (request.getActive() != null) {
            mode.setActive(request.getActive());
        }
        mode.setModeType(resolveModeType(modeCode, request.getModeType()));
        if (request.getRequiresReference() != null) {
            mode.setRequiresReference(request.getRequiresReference());
        }
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
    public PaymentModeResponse create(Map<String, Object> param, String email) {
        PaymentModeRequest paymentModeRequest = mapToPaymentModeRequest(param);
        return create(email, paymentModeRequest);
    }

    @Transactional
    public PaymentModeResponse update(Map<String, Object> param, Long modeId, String email) {
        PaymentModeRequest paymentModeRequest = mapToPaymentModeRequest(param);
        return update(email, modeId, paymentModeRequest);
    }

    @Transactional
    public void ensureDefaults(Company company) {
        // Live safety: GET requests run on the read-only READ pool (tenant key
        // ends with "_read", Hikari readOnly=true). Spring kabhi read-write tx
        // me setReadOnly(false) nahi karta, isliye read pool par INSERT hamesha
        // "Connection is read-only" dega. Seed sirf WRITE pool par karo; read
        // path se aaye to chup-chaap skip (caller GET tha, POST par seed hoga).
        String tenant = null;
        try {
            tenant = TenantContextHolder.getTenant();
        } catch (Exception ignored) {
            tenant = null;
        }
        if (tenant != null && tenant.endsWith(ApplicationConstant.CONNECTION_READ_STRING)) {
            log.warn("[PaymentMode] skipping default seed on read-only tenant '{}' — defaults seed on write paths", tenant);
            return;
        }
        for (String modeName : DEFAULT_MODES) {
            String modeCode = toCode(modeName);
            if (!paymentModeMasterRepository.existsByCompanyAndModeCodeIgnoreCase(company, modeCode)) {
                paymentModeMasterRepository.save(PaymentModeMaster.builder()
                        .company(company)
                        .modeName(modeName)
                        .modeCode(modeCode)
                        .active(true)
                        .modeType(ONLINE_MODE_CODE.equals(modeCode) ? PaymentModeType.ONLINE : PaymentModeType.OFFLINE)
                        .build());
            }
        }
    }

    public String requireActiveModeCode(Company company, String mode) {
        return requireActiveMode(company, mode).getModeCode();
    }

    public PaymentModeMaster requireActiveMode(Company company, String mode) {
        String modeCode = toCode(mode);
        return paymentModeMasterRepository.findByCompanyAndModeCodeIgnoreCaseAndActiveTrue(company, modeCode)
                .orElseThrow(() -> new BadRequestException("Select an active payment mode"));
    }

    /**
     * Manual entry (invoice initial payment / Record Payment) ke liye: ONLINE
     * mode yahan allowed nahi — wo sirf gateway confirm hone par record hota hai.
     */
    public String requireActiveOfflineModeCode(Company company, String mode) {
        PaymentModeMaster paymentMode = requireActiveMode(company, mode);
        if (paymentMode.effectiveModeType() == PaymentModeType.ONLINE) {
            throw new BadRequestException("Online payment is collected through the payment gateway. Use Collect Online.");
        }
        return paymentMode.getModeCode();
    }

    /** Gateway payments ka mode code; mode missing ho to seed karke. */
    public String onlineModeCode(Company company) {
        paymentModeMasterRepository.findByCompanyAndModeCodeIgnoreCase(company, ONLINE_MODE_CODE)
                .orElseGet(() -> paymentModeMasterRepository.save(PaymentModeMaster.builder()
                        .company(company)
                        .modeName("Online")
                        .modeCode(ONLINE_MODE_CODE)
                        .active(true)
                        .modeType(PaymentModeType.ONLINE)
                        .build()));
        return ONLINE_MODE_CODE;
    }

    public Map<String, String> activeModeLabels(Company company) {
        return paymentModeMasterRepository.findAllByCompanyWithFilters(company, true, null).stream()
                .collect(LinkedHashMap::new, (map, mode) -> map.put(mode.getModeCode(), mode.getModeName()), LinkedHashMap::putAll);
    }

    private PaymentModeMaster getModeOrThrow(Company company, Long modeId) {
        return paymentModeMasterRepository.findById(modeId)
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
                .modeType(mode.effectiveModeType().name())
                .requiresReference(mode.isRequiresReference())
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
        data.put("modeType", mode.effectiveModeType().name());
        data.put("requiresReference", mode.isRequiresReference());
        return data;
    }

    /**
     * Type code se tay hota hai: sirf ONLINE code wala mode gateway (ONLINE) hai,
     * baaki sab OFFLINE. Warna Cash jaisa mode galti se ONLINE ho kar manual
     * entry me reject hone lagta.
     */
    private PaymentModeType resolveModeType(String modeCode, String requestedType) {
        PaymentModeType derived = ONLINE_MODE_CODE.equals(modeCode) ? PaymentModeType.ONLINE : PaymentModeType.OFFLINE;
        if (requestedType == null || requestedType.isBlank()) {
            return derived;
        }
        PaymentModeType requested;
        try {
            requested = PaymentModeType.valueOf(requestedType.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Invalid payment mode type");
        }
        if (requested != derived) {
            throw new BadRequestException(derived == PaymentModeType.ONLINE
                    ? "Online payment mode must stay Online"
                    : "Only the 'Online' mode can be an online (gateway) mode");
        }
        return derived;
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

    private PaymentModeRequest mapToPaymentModeRequest(Map<String, Object> param) {
        PaymentModeRequest paymentModeRequest = new PaymentModeRequest();
        String modeName = DataTypeUtility.stringValue(param.get("modeName"));
        if (modeName.length() == 0) {
            modeName = DataTypeUtility.stringValue(param.get("name"));
        }
        if (modeName.length() == 0) {
            modeName = null;
        }
        paymentModeRequest.setModeName(modeName);
        String description = DataTypeUtility.stringValue(param.get("description"));
        if (description.length() == 0) {
            description = null;
        }
        paymentModeRequest.setDescription(description);
        Object activeRaw = param.get("active");
        if (activeRaw == null) {
            activeRaw = param.get("isActive");
        }
        Boolean active = null;
        if (activeRaw != null) {
            String activeStr = DataTypeUtility.stringValue(activeRaw);
            if (activeStr.length() > 0) {
                active = DataTypeUtility.booleanValue(activeRaw);
            }
        }
        paymentModeRequest.setActive(active);
        String modeType = DataTypeUtility.stringValue(param.get("modeType"));
        if (modeType.length() == 0) {
            modeType = null;
        }
        paymentModeRequest.setModeType(modeType);
        Boolean requiresReference = null;
        Object requiresReferenceRaw = param.get("requiresReference");
        if (requiresReferenceRaw != null) {
            String requiresReferenceStr = DataTypeUtility.stringValue(requiresReferenceRaw);
            if (requiresReferenceStr.length() > 0) {
                requiresReference = DataTypeUtility.booleanValue(requiresReferenceRaw);
            }
        }
        paymentModeRequest.setRequiresReference(requiresReference);
        return paymentModeRequest;
    }
}
