package com.billing.service;

import com.billing.entity.Company;
import com.billing.entity.User;
import com.billing.entity.enums.RoleName;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.multitenancy.TenantContextHolder;
import com.billing.repository.CompanyRepository;
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
    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public User getCurrentUser() {
        Object principal = currentPrincipal();
        if (principal instanceof com.billing.security.TenantSuperAdminPrincipal superAdmin) {
            return syntheticSuperAdminUser(superAdmin);
        }
        CustomUserDetails currentUser = getAuthenticatedUserDetails();
        return userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public User getCurrentUser(String ignoredIdentifier) {
        Object principal = currentPrincipal();
        if (principal instanceof com.billing.security.TenantSuperAdminPrincipal superAdmin) {
            return syntheticSuperAdminUser(superAdmin);
        }
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
        if (user.getCompany() != null) {
            return user.getCompany();
        }
        // DATABASE-per-tenant: User.company is @Transient, resolve company from current tenant connection.
        return resolveTenantCompany();
    }

    /**
     * Transient (never persisted) OWNER user for a tenant super-admin session.
     * Lets the whole tenant API surface work unchanged: company scoping,
     * owner gates and audit fields resolve from the attached company.
     */
    private User syntheticSuperAdminUser(com.billing.security.TenantSuperAdminPrincipal superAdmin) {
        Company company = null;
        try {
            if (superAdmin.getCompanyCode() != null && !superAdmin.getCompanyCode().isBlank()) {
                company = companyRepository
                        .findByCodeIgnoreCase(superAdmin.getCompanyCode().trim().toUpperCase(java.util.Locale.ROOT))
                        .or(() -> companyRepository.findByCodeIgnoreCase(superAdmin.getCompanyCode().trim()))
                        .orElse(null);
            }
        } catch (Exception ignored) {
            company = null;
        }
        if (company == null) {
            try {
                company = resolveTenantCompany();
            } catch (Exception e) {
                throw new AccessDeniedException("Unable to resolve company for super-admin session");
            }
        }
        return User.builder()
                .fullName("Super Admin")
                .username(superAdmin.getUsername())
                .mobileNumber("")
                .email(superAdmin.getUsername())
                .password("")
                .role(RoleName.OWNER)
                .active(true)
                .company(company)
                .superAdmin(true)
                .build();
    }

    private Object currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getPrincipal();
    }

    private Company resolveTenantCompany() {
        String tenant = TenantContextHolder.getTenant();
        String db = tenant == null ? null : tenant.replace("_read", "").replace("_write", "");
        if (db != null && !db.isBlank()) {
            try {
                java.util.Optional<Company> companyOpt = companyRepository
                        .findByCodeIgnoreCase(db.toUpperCase(java.util.Locale.ROOT));
                if (companyOpt.isPresent()) {
                    return companyOpt.get();
                }
            } catch (Exception ignored) {
                // fall through to error below
            }
        }
        throw new BadRequestException("This action requires a company-scoped user account");
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
