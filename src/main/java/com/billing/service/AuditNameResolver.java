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

    private String userDisplayName(com.billing.entity.User user) {
        return user.getFullName() == null || user.getFullName().isBlank()
                ? user.getEmail()
                : user.getFullName();
    }
}
