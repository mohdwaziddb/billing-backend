package com.billing.core;

/**
 * Single static place for the project's own site domains/URLs.
 *
 * Domain kabhi change karna ho (e.g. biziotechnologies.com -> kuch aur)
 * to SIRF yahi class badlo — RequestInterceptor aur baaki backend code
 * yahi se padhta hai. Kahi bhi domain string hardcode mat karo.
 *
 * NOTE: third-party vendor URLs (MSG91, Pinnacle, Google Fonts) yaha nahi —
 * wo apne provider constants me rehte hai. Yaha sirf APNI site.
 * Env-driven values (FRONTEND_URL, CORS origins, DB credentials) bhi yaha
 * nahi — wo application-*.properties / environment se aate hai.
 */
public final class AppDomains {

    /** Production main domain (bare + www dono main host hai). */
    public static final String MAIN_DOMAIN = "biziotechnologies.com";

    public static final String WWW_MAIN_DOMAIN = "www." + MAIN_DOMAIN;

    /** Full main-site URL (unknown-subdomain redirect target). */
    public static final String MAIN_SITE_URL = "https://" + MAIN_DOMAIN;

    /** Live tenant suffix: &lt;tenant&gt;.biziotechnologies.com */
    public static final String LIVE_TENANT_SUFFIX = "." + MAIN_DOMAIN;

    /** Local dev loopback hosts (main hosts me counted hote hai). */
    public static final String LOCALHOST = "localhost";

    public static final String LOCAL_LOOPBACK = "127.0.0.1";

    /** Local tenant suffix: &lt;tenant&gt;.localhost */
    public static final String LOCAL_TENANT_SUFFIX = ".localhost";

    /** Local backend fallback host (dev direct calls, Host header missing). */
    public static final String LOCAL_BACKEND_HOST = "localhost:9009";

    private AppDomains() {
    }

    public static boolean isMainDomain(String host) {
        if (host == null) {
            return false;
        }
        return MAIN_DOMAIN.equalsIgnoreCase(host) || WWW_MAIN_DOMAIN.equalsIgnoreCase(host);
    }

    public static boolean isLiveTenantSubdomain(String hostOnly) {
        if (hostOnly == null) {
            return false;
        }
        String host = hostOnly.toLowerCase(java.util.Locale.ROOT);
        return host.endsWith(LIVE_TENANT_SUFFIX)
                && !MAIN_DOMAIN.equalsIgnoreCase(host)
                && !WWW_MAIN_DOMAIN.equalsIgnoreCase(host);
    }
}
