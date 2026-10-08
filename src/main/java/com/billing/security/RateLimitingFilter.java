package com.billing.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight in-memory rate limiter for brute-forceable public endpoints.
 * No external dependency so login/OTP/refresh abuse is blocked even on a
 * single instance. For multi-instance deployments replace with Redis.
 *
 * NOTE: instantiated as a @Bean in SecurityConfig (not @Component) so the
 * filter runs exactly once inside the security chain and is not
 * double-registered as a servlet filter.
 *
 * Limits (per client IP, sliding window):
 * - /api/v1/auth/login, /api/v1/platform-admin/login: 10/min
 * - /api/v1/auth/forgot-password/*: 5/min
 * - /api/v1/auth/refresh: 30/min
 */
public class RateLimitingFilter extends OncePerRequestFilter {

    private record Rule(int maxRequests, long windowMillis) {}

    private static final Map<String, Rule> RULES = Map.of(
            "/api/v1/auth/login", new Rule(10, 60_000L),
            "/api/v1/platform-admin/login", new Rule(10, 60_000L),
            "/api/v1/auth/forgot-password/request", new Rule(5, 60_000L),
            "/api/v1/auth/forgot-password/confirm", new Rule(10, 60_000L),
            "/api/v1/auth/refresh", new Rule(30, 60_000L));

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    private static final class Window {
        long windowStart;
        int count;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        Rule rule = RULES.get(path);
        if (rule == null || !"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String key = ruleKey(request, path);
        if (!allow(key, rule)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Too many attempts. Please try again in a minute.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private String ruleKey(HttpServletRequest request, String path) {
        String ip = clientIp(request);
        return path + "|" + ip;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }

    private boolean allow(String key, Rule rule) {
        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(key, k -> new Window());
        synchronized (window) {
            if (now - window.windowStart >= rule.windowMillis()) {
                window.windowStart = now;
                window.count = 1;
                return true;
            }
            window.count++;
            return window.count <= rule.maxRequests();
        }
    }
}
