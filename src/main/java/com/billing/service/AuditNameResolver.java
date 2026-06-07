package com.billing.service;

import com.billing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditNameResolver {

    private final UserRepository userRepository;

    public String displayName(String auditValue) {
        if (auditValue == null || auditValue.isBlank()) {
            return null;
        }

        String normalized = auditValue.trim();
        return userRepository.findByEmailIgnoreCase(normalized)
                .map(user -> user.getFullName() == null || user.getFullName().isBlank()
                        ? user.getEmail()
                        : user.getFullName())
                .orElse(normalized);
    }
}
