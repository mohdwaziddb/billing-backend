package com.billing.util;

import com.billing.exception.BadRequestException;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

/**
 * SSRF guard for tenant-configured outbound URLs (SMS/WhatsApp provider API URL,
 * remote logo images). Tenant admin URL set kar sakta hai, isliye server se
 * internal network / cloud metadata (169.254.169.254) / localhost hit na ho.
 *
 * Har resolved IP check hota hai. HttpClient redirects follow nahi karta
 * (default NEVER), isliye redirect se bypass nahi.
 */
public final class OutboundUrlGuard {

    private OutboundUrlGuard() {
    }

    /** Valid public http(s) URL ka URI deta hai, warna BadRequestException. */
    public static URI requirePublicUrl(String url) {
        String value = url == null ? "" : url.trim();
        URI uri;
        try {
            uri = URI.create(value);
        } catch (Exception e) {
            throw new BadRequestException("Invalid provider URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BadRequestException("Provider URL must start with http:// or https://");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BadRequestException("Invalid provider URL");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (Exception e) {
            throw new BadRequestException("Provider host could not be resolved: " + host);
        }
        for (InetAddress address : addresses) {
            if (isInternalAddress(address)) {
                throw new BadRequestException("Provider URL points to an internal/private address and is not allowed");
            }
        }
        return uri;
    }

    public static boolean isPublicUrl(String url) {
        try {
            requirePublicUrl(url);
            return true;
        } catch (BadRequestException e) {
            return false;
        }
    }

    public static boolean isInternalAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] ip = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = ip[0] & 0xFF;
            int second = ip[1] & 0xFF;
            // 100.64.0.0/10 (carrier-grade NAT / kai cloud internal networks), 0.0.0.0/8
            return (first == 100 && second >= 64 && second <= 127) || first == 0;
        }
        if (address instanceof Inet6Address) {
            // fc00::/7 unique-local (IPv6 private range)
            return (ip[0] & 0xFE) == 0xFC;
        }
        return false;
    }
}
