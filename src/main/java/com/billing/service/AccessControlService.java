package com.billing.service;

import com.billing.entity.Company;
import com.billing.entity.User;
import com.billing.exception.ResourceNotFoundException;
import com.billing.exception.BadRequestException;
import com.billing.repository.CompanyOwnerRepository;
import com.billing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final UserRepository userRepository;
    private final CompanyOwnerRepository companyOwnerRepository;

    @Transactional(readOnly = true)
    public User getCurrentUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public Company getCurrentCompany(String email) {
        return requireCompany(getCurrentUser(email));
    }

    @Transactional(readOnly = true)
    public boolean isCompanyOwner(User user) {
        Company company = requireCompany(user);
        return user.isActive() && companyOwnerRepository.existsByCompanyAndUserAndUserActiveTrue(company, user);
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
}
