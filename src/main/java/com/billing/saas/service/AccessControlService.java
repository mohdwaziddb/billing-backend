package com.billing.saas.service;

import com.billing.saas.entity.Company;
import com.billing.saas.entity.User;
import com.billing.saas.exception.BadRequestException;
import com.billing.saas.exception.ResourceNotFoundException;
import com.billing.saas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User getCurrentUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public Company getCurrentCompany(String email) {
        return requireCompany(getCurrentUser(email));
    }

    public Company requireCompany(User user) {
        if (user.getCompany() == null) {
            throw new BadRequestException("This action requires a company-scoped user account");
        }
        return user.getCompany();
    }
}
