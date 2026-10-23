package com.billing.service.payment;

import com.billing.dto.payment.gateway.PaymentGatewayMetadataResponse;
import com.billing.dto.payment.gateway.PaymentGatewayPublicConfigResponse;
import com.billing.dto.payment.gateway.PaymentGatewaySettingResponse;
import com.billing.entity.Company;
import com.billing.entity.PaymentGatewaySetting;
import com.billing.entity.enums.PaymentGatewayType;
import com.billing.exception.BadRequestException;
import com.billing.multitenancy.TenantContextHolder;
import com.billing.repository.PaymentGatewaySettingRepository;
import com.billing.service.AccessControlService;
import com.billing.service.AuditLogService;
import com.billing.service.AuditNameResolver;
import com.billing.service.CompanyRegistryService;
import com.billing.service.PaymentModeMasterService;
import com.billing.service.payment.gateway.PaymentGatewayConfigurationService;
import com.billing.service.payment.gateway.PaymentGatewayFactory;
import com.billing.service.payment.gateway.PaymentGatewayProvider;
import com.billing.service.payment.gateway.PaymentGatewayResolvedSettings;
import com.billing.service.payment.gateway.dto.GatewayHealthResult;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Company-wise payment gateway settings (owner only). */
@Service
@RequiredArgsConstructor
public class PaymentGatewaySettingService {

    private final PaymentGatewaySettingRepository paymentGatewaySettingRepository;
    private final PaymentGatewayFactory paymentGatewayFactory;
    private final PaymentGatewayConfigurationService paymentGatewayConfigurationService;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;
    private final AuditNameResolver auditNameResolver;
    private final CompanyRegistryService companyRegistryService;
    private final PaymentModeMasterService paymentModeMasterService;

    public List<PaymentGatewayMetadataResponse> providers() {
        return paymentGatewayFactory.providers().stream()
                .map(PaymentGatewayProvider::metadata)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentGatewaySettingResponse> list(String email) {
        Company company = accessControlService.requireOwnerCompany(email);
        String companyCode = webhookCompanyCode(company);
        return paymentGatewaySettingRepository.findAllByOrderByIdAsc().stream()
                .map(setting -> toResponse(setting, companyCode))
                .toList();
    }

    @Transactional
    public PaymentGatewaySettingResponse save(Map<String, Object> param, String email) {
        Company company = accessControlService.requireOwnerCompany(email);
        PaymentGatewayType gatewayType = paymentGatewayFactory.parseType(DataTypeUtility.stringValue(param.get("gatewayType")));
        PaymentGatewayProvider provider = paymentGatewayFactory.getProvider(gatewayType);

        Optional<PaymentGatewaySetting> existingSetting = paymentGatewaySettingRepository.findByGatewayType(gatewayType);
        PaymentGatewaySetting setting = existingSetting.orElseGet(() -> PaymentGatewaySetting.builder()
                .gatewayType(gatewayType)
                .build());
        Map<String, Object> oldData = existingSetting.isPresent() ? snapshot(setting) : null;

        String displayName = DataTypeUtility.stringValue(param.get("displayName"));
        setting.setCompany(company);
        setting.setDisplayName(displayName.length() == 0 ? provider.metadata().getGatewayName() : displayName);
        if (param.get("testMode") != null) {
            setting.setTestMode(DataTypeUtility.booleanValue(param.get("testMode")));
        }
        if (param.get("active") != null) {
            setting.setActive(DataTypeUtility.booleanValue(param.get("active")));
        }
        boolean makeDefault = param.get("defaultGateway") == null || DataTypeUtility.booleanValue(param.get("defaultGateway"));
        setting.setProviderConfig(paymentGatewayConfigurationService.serializeForStorage(
                gatewayType, configValues(param.get("config")), paymentGatewayConfigurationService.stored(setting)));

        if (setting.isActive()) {
            paymentGatewayConfigurationService.requireConfigured(setting);
            provider.validateConfiguration(paymentGatewayConfigurationService.resolved(setting));
        }
        // Ek hi default gateway: naya default bane to baaki ka default hatao.
        if (makeDefault && setting.isActive()) {
            for (PaymentGatewaySetting other : paymentGatewaySettingRepository.findAllByOrderByIdAsc()) {
                if (other.isDefaultGateway() && other.getGatewayType() != gatewayType) {
                    other.setDefaultGateway(false);
                    paymentGatewaySettingRepository.save(other);
                }
            }
        }
        setting.setDefaultGateway(setting.isActive() && makeDefault);

        PaymentGatewaySetting saved = paymentGatewaySettingRepository.save(setting);
        // Online payment mode dropdown me aaye (gateway on hone par).
        paymentModeMasterService.ensureDefaults(company);
        paymentModeMasterService.onlineModeCode(company);
        if (oldData == null) {
            auditLogService.logCreate(email, company, "Payment Gateway", "PaymentGatewaySetting", saved.getId(), snapshot(saved));
        } else {
            auditLogService.logUpdate(email, company, "Payment Gateway", "PaymentGatewaySetting", saved.getId(), oldData, snapshot(saved));
        }
        return toResponse(saved, webhookCompanyCode(company));
    }

    /** Saved ya form me abhi bhari keys se gateway connection check. */
    @Transactional(readOnly = true)
    public GatewayHealthResult test(Map<String, Object> param, String email) {
        accessControlService.requireOwnerCompany(email);
        PaymentGatewayType gatewayType = paymentGatewayFactory.parseType(DataTypeUtility.stringValue(param.get("gatewayType")));
        PaymentGatewaySetting stored = paymentGatewaySettingRepository.findByGatewayType(gatewayType).orElse(null);
        PaymentGatewaySetting probe = PaymentGatewaySetting.builder()
                .gatewayType(gatewayType)
                .testMode(param.get("testMode") != null
                        ? DataTypeUtility.booleanValue(param.get("testMode"))
                        : stored == null || stored.isTestMode())
                .build();
        probe.setProviderConfig(paymentGatewayConfigurationService.serializeForStorage(
                gatewayType, configValues(param.get("config")), paymentGatewayConfigurationService.stored(stored)));
        return paymentGatewayFactory.getProvider(gatewayType).testConnection(paymentGatewayConfigurationService.resolved(probe));
    }

    /** Billing screens: online payment available hai ya nahi. */
    @Transactional(readOnly = true)
    public PaymentGatewayPublicConfigResponse publicConfig() {
        Optional<PaymentGatewaySetting> active = activeGateway();
        if (active.isEmpty()) {
            return PaymentGatewayPublicConfigResponse.builder()
                    .onlineEnabled(false)
                    .onlineModeCode(PaymentModeMasterService.ONLINE_MODE_CODE)
                    .build();
        }
        PaymentGatewaySetting setting = active.get();
        PaymentGatewayMetadataResponse metadata = paymentGatewayFactory.getProvider(setting.getGatewayType()).metadata();
        return PaymentGatewayPublicConfigResponse.builder()
                .onlineEnabled(true)
                .gatewayType(setting.getGatewayType().name())
                .gatewayName(setting.getDisplayName() != null ? setting.getDisplayName() : metadata.getGatewayName())
                .testMode(setting.isTestMode())
                .supportsCheckout(metadata.isSupportsCheckout())
                .supportsPaymentLink(metadata.isSupportsPaymentLink())
                .onlineModeCode(PaymentModeMasterService.ONLINE_MODE_CODE)
                .build();
    }

    /** Default active gateway (warna pehla active). */
    @Transactional(readOnly = true)
    public Optional<PaymentGatewaySetting> activeGateway() {
        return paymentGatewaySettingRepository.findByActiveTrueOrderByDefaultGatewayDescIdAsc().stream().findFirst();
    }

    public PaymentGatewaySetting requireActiveGateway() {
        return activeGateway().orElseThrow(() -> new BadRequestException("Online payment is not enabled. Configure it in Setup > Payment Gateway."));
    }

    public PaymentGatewaySetting requireGateway(PaymentGatewayType gatewayType) {
        return paymentGatewaySettingRepository.findByGatewayType(gatewayType)
                .orElseThrow(() -> new BadRequestException("Payment gateway is not configured"));
    }

    public PaymentGatewayResolvedSettings resolved(PaymentGatewaySetting setting) {
        return paymentGatewayConfigurationService.resolved(setting);
    }

    private PaymentGatewaySettingResponse toResponse(PaymentGatewaySetting setting, String companyCode) {
        return PaymentGatewaySettingResponse.builder()
                .id(setting.getId())
                .gatewayType(setting.getGatewayType().name())
                .displayName(setting.getDisplayName())
                .testMode(setting.isTestMode())
                .active(setting.isActive())
                .defaultGateway(setting.isDefaultGateway())
                .config(paymentGatewayConfigurationService.masked(setting))
                .webhookUrl("/v1/payment-gateway/webhook/" + setting.getGatewayType().name()
                        + (companyCode == null ? "" : "?company_code=" + companyCode))
                .updatedAt(setting.getUpdatedAt())
                .updatedBy(auditNameResolver.displayName(setting.getUpdatedBy()))
                .build();
    }

    private Map<String, Object> snapshot(PaymentGatewaySetting setting) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("gatewayType", setting.getGatewayType() != null ? setting.getGatewayType().name() : null);
        data.put("displayName", setting.getDisplayName());
        data.put("testMode", setting.isTestMode());
        data.put("active", setting.isActive());
        data.put("defaultGateway", setting.isDefaultGateway());
        // Secrets audit me masked hi jaate hain.
        data.put("config", paymentGatewayConfigurationService.masked(setting));
        return data;
    }

    private Map<String, String> configValues(Object raw) {
        Map<String, String> values = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = DataTypeUtility.stringValue(entry.getKey());
                if (key.length() > 0) {
                    values.put(key, DataTypeUtility.stringValue(entry.getValue()));
                }
            }
        }
        return values;
    }

    private String webhookCompanyCode(Company company) {
        String tenant = TenantContextHolder.getTenant();
        String databaseName = tenant == null ? null : tenant.replace("_read", "").replace("_write", "");
        String code = companyRegistryService.resolveCompanyCodeByDatabase(databaseName);
        if (code != null) {
            return code;
        }
        return company != null ? company.getCode() : null;
    }
}
