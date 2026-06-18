package com.billing.service;

import com.billing.entity.Company;
import com.billing.entity.User;
import com.billing.entity.enums.RoleName;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.UserRepository;
import com.billing.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User getCurrentUser() {
        CustomUserDetails currentUser = getAuthenticatedUserDetails();
        return userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public User getCurrentUser(String ignoredIdentifier) {
        return getCurrentUser();
    }

    @Transactional(readOnly = true)
    public Company getCurrentCompany() {
        return requireCompany(getCurrentUser());
    }

    @Transactional(readOnly = true)
    public Company getCurrentCompany(String email) {
        return requireCompany(getCurrentUser(email));
    }

    @Transactional(readOnly = true)
    public boolean isCompanyOwner(User user) {
        requireCompany(user);
        return user.isActive() && user.getRole() == RoleName.OWNER;
    }

    public boolean isSuperAdmin(User user) {
        return user != null && user.isActive() && user.getRole() == RoleName.SUPER_ADMIN;
    }

    @Transactional(readOnly = true)
    public boolean isSuperAdmin() {
        return isSuperAdmin(getCurrentUser());
    }

    @Transactional(readOnly = true)
    public boolean isCompanyOwner() {
        return isCompanyOwner(getCurrentUser());
    }

    @Transactional(readOnly = true)
    public Company requireOwnerCompany() {
        User user = getCurrentUser();
        if (!isCompanyOwner(user)) {
            throw new AccessDeniedException("Only company owner can perform this action");
        }
        return requireCompany(user);
    }

    @Transactional(readOnly = true)
    public Company requireOwnerCompany(String email) {
        User user = getCurrentUser(email);
        if (!isCompanyOwner(user)) {
            throw new AccessDeniedException("Only company owner can perform this action");
        }
        return requireCompany(user);
    }

    public Company requireCompany(User user) {
        if (user.getCompany() == null) {
            throw new BadRequestException("This action requires a company-scoped user account");
        }
        return user.getCompany();
    }

    private CustomUserDetails getAuthenticatedUserDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("User is not authenticated");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails customUserDetails) {
            return customUserDetails;
        }
        if (principal instanceof String username) {
            String normalized = username.trim();
            java.util.List<User> candidates = new java.util.ArrayList<>();
            candidates.addAll(userRepository.findAllByUsernameIgnoreCase(normalized));
            candidates.addAll(userRepository.findAllByEmailIgnoreCase(normalized));
            candidates.addAll(userRepository.findAllByMobileNumber(normalized));
            java.util.Map<Long, User> byId = new java.util.LinkedHashMap<>();
            for (User candidate : candidates) {
                byId.putIfAbsent(candidate.getId(), candidate);
            }
            java.util.List<User> uniqueCandidates = new java.util.ArrayList<>(byId.values());
            if (uniqueCandidates.size() != 1) {
                throw new AccessDeniedException("Unable to resolve authenticated user");
            }
            return uniqueCandidates.stream().findFirst()
                    .map(CustomUserDetails::new)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        }

        throw new AccessDeniedException("Unable to resolve authenticated user");
    }
}
