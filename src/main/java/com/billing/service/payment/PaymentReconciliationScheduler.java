package com.billing.service.payment;

import com.billing.multitenancy.RequestInterceptor;
import com.billing.multitenancy.TenantContextHolder;
import com.billing.service.CompanyRegistryService;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Webhook miss ho jaye (server down, URL galat) to bhi online payment invoice
 * par aa jaye: har ACTIVE tenant ke open transactions gateway se check.
 */
@Component
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationScheduler.class);

    private final CompanyRegistryService companyRegistryService;
    private final RequestInterceptor requestInterceptor;
    private final OnlinePaymentService onlinePaymentService;

    @Value("${app.payment.reconcile.enabled:true}")
    private boolean enabled;

    // Payment links 7 din tak valid rehte hain; isliye 8 din tak ke open transactions.
    @Value("${app.payment.reconcile.lookback-hours:192}")
    private long lookbackHours;

    @Scheduled(fixedDelayString = "${app.payment.reconcile.interval-ms:900000}", initialDelayString = "${app.payment.reconcile.initial-delay-ms:120000}")
    public void reconcile() {
        if (!enabled) {
            return;
        }
        List<Map<String, Object>> tenants;
        try {
            tenants = companyRegistryService.listTenants(null, "ACTIVE", 100000, 0);
        } catch (Exception e) {
            log.warn("Payment reconcile skipped: could not list tenants: {}", e.getMessage());
            return;
        }
        LocalDateTime createdTo = LocalDateTime.now().minusMinutes(10);
        LocalDateTime createdFrom = LocalDateTime.now().minusHours(lookbackHours);
        for (Map<String, Object> tenant : tenants) {
            String databaseName = DataTypeUtility.stringValue(tenant.get("database_name"));
            if (databaseName.isEmpty()) {
                continue;
            }
            try {
                requestInterceptor.ensureTenant(databaseName, companyRegistryService.resolveDbHostByDatabase(databaseName));
                TenantContextHolder.setTenantId(databaseName);
                int settled = onlinePaymentService.reconcileOpenTransactions(createdFrom, createdTo);
                if (settled > 0) {
                    log.info("[{}] Payment reconcile settled {} online payments", databaseName, settled);
                }
            } catch (Exception e) {
                // Purane tenant me payment_transactions table abhi na ho to bhi baaki tenants chalein.
                log.warn("[{}] Payment reconcile failed: {}", databaseName, e.getMessage());
            } finally {
                TenantContextHolder.clear();
            }
        }
    }
}
