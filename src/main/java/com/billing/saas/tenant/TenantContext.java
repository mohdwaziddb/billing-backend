package com.billing.saas.tenant;

public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_COMPANY_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCompanyId(Long companyId) {
        CURRENT_COMPANY_ID.set(companyId);
    }

    public static Long getCompanyId() {
        return CURRENT_COMPANY_ID.get();
    }

    public static boolean hasTenant() {
        return CURRENT_COMPANY_ID.get() != null;
    }

    public static void clear() {
        CURRENT_COMPANY_ID.remove();
    }
}
