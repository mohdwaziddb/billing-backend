package com.billing.security;

import com.billing.entity.User;
import com.billing.multitenancy.TenantContextHolder;
import com.billing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = findByLoginIdentifier(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        // NOTE: security filters run BEFORE RequestInterceptor, so tenant can be null here.
        // Null db is tolerated: JwtAuthenticationFilter falls back to JWT claims, then
        // RequestInterceptor sets/validates the tenant (unknown host -> explicit error).
        String tenant = TenantContextHolder.getTenant();
        String db = tenant != null ? tenant.replace("_read", "").replace("_write", "").trim() : null;
        if (db != null && db.isEmpty()) {
            db = null;
        }
        // Convention: database = lower(companyCode), no billing_company_ prefix (e.g. maacreation -> MAACREATION)
        String code = db != null
                ? db.replace("billing_company_", "").toUpperCase(java.util.Locale.ROOT)
                : null;
        return new CustomUserDetails(user, code, db);
    }

    public UserDetails loadUserById(Long userId) throws UsernameNotFoundException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        // NOTE: security filters run BEFORE RequestInterceptor, so tenant can be null here.
        // Null db is tolerated: JwtAuthenticationFilter falls back to JWT claims, then
        // RequestInterceptor sets/validates the tenant (unknown host -> explicit error).
        String tenant = TenantContextHolder.getTenant();
        String db = tenant != null ? tenant.replace("_read", "").replace("_write", "").trim() : null;
        if (db != null && db.isEmpty()) {
            db = null;
        }
        // Convention: database = lower(companyCode), no billing_company_ prefix (e.g. maacreation -> MAACREATION)
        String code = db != null
                ? db.replace("billing_company_", "").toUpperCase(java.util.Locale.ROOT)
                : null;
        return new CustomUserDetails(user, code, db);
    }

    private java.util.Optional<User> findByLoginIdentifier(String username) {
        String normalized = username == null ? null : username.trim();
        java.util.List<User> candidates = new java.util.ArrayList<>();
        candidates.addAll(userRepository.findAllByUsernameIgnoreCase(normalized));
        candidates.addAll(userRepository.findAllByEmailIgnoreCase(normalized));
        candidates.addAll(userRepository.findAllByMobileNumber(normalized));
        java.util.Map<Long, User> byId = new java.util.LinkedHashMap<>();
        for (User candidate : candidates) {
            byId.putIfAbsent(candidate.getId(), candidate);
        }
        java.util.List<User> uniqueCandidates = new java.util.ArrayList<>(byId.values());
        return uniqueCandidates.size() == 1 ? uniqueCandidates.stream().findFirst() : java.util.Optional.empty();
    }
}
