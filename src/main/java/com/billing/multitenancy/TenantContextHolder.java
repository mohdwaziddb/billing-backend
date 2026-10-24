package com.billing.multitenancy;

public class TenantContextHolder {

    // Plain ThreadLocal (Inheritable nahi): background thread (jaise s3-cleanup executor)
    // apne banane wali request ka tenant hamesha ke liye na pakad le.
    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    public static void setTenantId(String tenant) {
        CONTEXT.set(tenant);
    }

    public static String getTenant() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
