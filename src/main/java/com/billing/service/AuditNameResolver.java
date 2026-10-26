package com.billing.service;

import com.billing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

@Service
@RequiredArgsConstructor
public class AuditNameResolver {

    private final UserRepository userRepository;

    private static final String REQUEST_NAME_CACHE_ATTRIBUTE = AuditNameResolver.class.getName() + ".names";

    /**
     * Request-scoped memo: 50+ list/detail responses har row ke createdBy/updatedBy ke liye
     * yahi call karte hain (email path par har baar users SELECT). Ek HTTP request me ek
     * value ek hi baar resolve hoti hai; request ke bahar (scheduler) pehle jaisa direct.
     */
    public String displayName(String auditValue) {
        if (auditValue == null || auditValue.isBlank()) {
            return null;
        }
        Map<String, String> requestNameCache = requestNameCache();
        if (requestNameCache == null) {
            return resolveDisplayName(auditValue);
        }
        return requestNameCache.computeIfAbsent(auditValue, this::resolveDisplayName);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> requestNameCache() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return null;
        }
        Object existingCache = requestAttributes.getAttribute(REQUEST_NAME_CACHE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (existingCache instanceof Map<?, ?> cachedNames) {
            return (Map<String, String>) cachedNames;
        }
        Map<String, String> newCache = new HashMap<>();
        requestAttributes.setAttribute(REQUEST_NAME_CACHE_ATTRIBUTE, newCache, RequestAttributes.SCOPE_REQUEST);
        return newCache;
    }

    private String resolveDisplayName(String auditValue) {
        String normalized = auditValue.trim();
        if (normalized.chars().allMatch(Character::isDigit)) {
            try {
                return userRepository.findById(Long.parseLong(normalized))
                        .map(this::userDisplayName)
                        .orElse(normalized);
            } catch (NumberFormatException ignored) {
                return normalized;
            }
        }

        return userRepository.findAllByEmailIgnoreCase(normalized).stream().findFirst()
                .map(this::userDisplayName)
                .orElse(normalized);
    }

    /**
     * List/page response ke liye per-call memo: har row ka createdBy/updatedBy wahi
     * 1-2 users hote hain, par har row par alag users SELECT chalta tha (N+1).
     * Cache sirf is ek call tak — naam badle to agli request me naya hi aayega.
     */
    public UnaryOperator<String> memoized() {
        Map<String, String> resolvedNames = new HashMap<>();
        return auditValue -> {
            if (auditValue == null || auditValue.isBlank()) {
                return null;
            }
            return resolvedNames.computeIfAbsent(auditValue, this::displayName);
        };
    }

    private String userDisplayName(com.billing.entity.User user) {
        return user.getFullName() == null || user.getFullName().isBlank()
                ? user.getEmail()
                : user.getFullName();
    }
}
