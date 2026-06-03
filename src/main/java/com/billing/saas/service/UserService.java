package com.billing.saas.service;

import com.billing.saas.dto.user.CompanyUserRequest;
import com.billing.saas.dto.user.UserProfileResponse;
import com.billing.saas.entity.Company;
import com.billing.saas.entity.User;
import com.billing.saas.entity.enums.RoleName;
import com.billing.saas.exception.BadRequestException;
import com.billing.saas.exception.ResourceNotFoundException;
import com.billing.saas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AccessControlService accessControlService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return userMapper.toProfile(user);
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponse> listCompanyUsers(String email) {
        Company company = accessControlService.getCurrentCompany(email);
        return userRepository.findByCompanyOrderByCreatedAtDesc(company).stream()
                .map(userMapper::toProfile)
                .toList();
    }

    @Transactional
    public UserProfileResponse createCompanyUser(String email, CompanyUserRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BadRequestException("Password is required");
        }
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new BadRequestException("This email address is already registered.");
        }

        User user = User.builder()
                .company(company)
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .active(Boolean.TRUE.equals(request.getActive()))
                .build();

        return userMapper.toProfile(userRepository.save(user));
    }

    @Transactional
    public UserProfileResponse updateCompanyUser(String email, Long userId, CompanyUserRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        User user = userRepository.findByIdAndCompany(userId, company)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.getEmail().equalsIgnoreCase(request.getEmail())
                && userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new BadRequestException("This email address is already registered.");
        }
        if (user.getRole() == RoleName.OWNER && request.getRole() != RoleName.OWNER) {
            ensureAnotherOwnerExists(company, user.getId());
        }
        if (user.getRole() == RoleName.OWNER && Boolean.FALSE.equals(request.getActive())) {
            ensureAnotherOwnerExists(company, user.getId());
        }

        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user.setRole(request.getRole());
        user.setActive(Boolean.TRUE.equals(request.getActive()));

        return userMapper.toProfile(userRepository.save(user));
    }

    @Transactional
    public void deactivateCompanyUser(String email, Long userId) {
        Company company = accessControlService.getCurrentCompany(email);
        User user = userRepository.findByIdAndCompany(userId, company)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() == RoleName.OWNER) {
            ensureAnotherOwnerExists(company, user.getId());
        }
        user.setActive(false);
        userRepository.save(user);
    }

    private void ensureAnotherOwnerExists(Company company, Long excludedUserId) {
        boolean hasAnotherOwner = userRepository.findByCompanyOrderByCreatedAtDesc(company).stream()
                .anyMatch(user -> user.getRole() == RoleName.OWNER
                        && user.isActive()
                        && !user.getId().equals(excludedUserId));
        if (!hasAnotherOwner) {
            throw new BadRequestException("Company must have at least one active owner");
        }
    }
}
