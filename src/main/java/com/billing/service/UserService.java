package com.billing.service;

import com.billing.entity.Company;
import com.billing.dto.PageResponse;
import com.billing.entity.User;
import com.billing.entity.enums.RoleName;
import com.billing.exception.ResourceNotFoundException;
import com.billing.dto.user.CompanyUserRequest;
import com.billing.dto.user.UserProfileResponse;
import com.billing.exception.BadRequestException;
import com.billing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AccessControlService accessControlService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String email) {
        User user = accessControlService.getCurrentUser(email);
        return userMapper.toProfile(user);
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponse> listCompanyUsers(String email) {
        Company company = accessControlService.requireOwnerCompany(email);
        return userRepository.findByCompanyOrderByCreatedAtDesc(company).stream()
                .map(userMapper::toProfile)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> pageCompanyUsers(String email, int page, int size,
                                                              String name, String mobileNumber,
                                                              String userEmail, RoleName role, Boolean active) {
        return pageCompanyUsers(email, page, size, name, null, mobileNumber, userEmail, null, role, active);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> pageCompanyUsers(String email, int page, int size,
                                                              String name, String username, String mobileNumber,
                                                              String userEmail, String search,
                                                              RoleName role, Boolean active) {
        Company company = accessControlService.getCurrentCompany(email);
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
        return PageResponse.from(userRepository.searchCompanyUsers(
                company,
                blankToNull(name),
                blankToNull(username),
                blankToNull(mobileNumber),
                blankToNull(userEmail),
                blankToNull(search),
                role,
                active,
                pageable
        ).map(userMapper::toProfile));
    }

    @Transactional
    public UserProfileResponse createCompanyUser(String email, CompanyUserRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BadRequestException("Password is required");
        }
        validateUniqueUser(company, request.getUsername(), request.getMobileNumber(), request.getEmail(), null);
        RoleName role = resolveRole(request.getRole());

        User user = User.builder()
                .company(company)
                .fullName(request.getFullName())
                .username(normalizeUsername(request.getUsername()))
                .mobileNumber(normalizeMobile(request.getMobileNumber()))
                .email(normalizeEmail(request.getEmail()))
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .active(Boolean.TRUE.equals(request.getActive()))
                .build();

        User saved = userRepository.save(user);
        auditLogService.logCreate(email, company, "User", "User", saved.getId(), snapshot(saved));
        return userMapper.toProfile(saved);
    }

    @Transactional
    public UserProfileResponse updateCompanyUser(String email, Long userId, CompanyUserRequest request) {
        Company company = accessControlService.requireOwnerCompany(email);
        User user = userRepository.findByIdAndCompany(userId, company)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Map<String, Object> oldData = snapshot(user);

        validateUniqueUser(company, request.getUsername(), request.getMobileNumber(), request.getEmail(), user.getId());
        RoleName role = resolveRole(request.getRole());
        if (user.getRole() == RoleName.OWNER && user.isActive() && role != RoleName.OWNER) {
            ensureAnotherOwnerExists(company, user.getId());
        }
        if (user.getRole() == RoleName.OWNER && user.isActive() && Boolean.FALSE.equals(request.getActive())) {
            ensureAnotherOwnerExists(company, user.getId());
        }

        user.setFullName(request.getFullName());
        user.setUsername(normalizeUsername(request.getUsername()));
        user.setMobileNumber(normalizeMobile(request.getMobileNumber()));
        user.setEmail(normalizeEmail(request.getEmail()));
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user.setRole(role);
        user.setActive(Boolean.TRUE.equals(request.getActive()));

        User saved = userRepository.save(user);
        auditLogService.logUpdate(email, company, "User", "User", saved.getId(), oldData, snapshot(saved));
        return userMapper.toProfile(saved);
    }

    @Transactional
    public void deactivateCompanyUser(String email, Long userId) {
        Company company = accessControlService.requireOwnerCompany(email);
        User user = userRepository.findByIdAndCompany(userId, company)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Map<String, Object> oldData = snapshot(user);
        if (user.getRole() == RoleName.OWNER && user.isActive()) {
            ensureAnotherOwnerExists(company, user.getId());
        }
        user.setActive(false);
        User saved = userRepository.save(user);
        auditLogService.logDelete(email, company, "User", "User", saved.getId(), oldData);
    }

    private void ensureAnotherOwnerExists(Company company, Long excludedUserId) {
        boolean hasAnotherOwner = userRepository.findByCompanyOrderByCreatedAtDesc(company).stream()
                .anyMatch(user -> user.getRole() == RoleName.OWNER && user.isActive() && !user.getId().equals(excludedUserId));
        if (!hasAnotherOwner) {
            throw new BadRequestException("Company must have at least one active owner");
        }
    }

    private void validateUniqueUser(Company company, String username, String mobileNumber, String email, Long currentUserId) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedMobile = normalizeMobile(mobileNumber);
        String normalizedEmail = normalizeEmail(email);
        List<String> messages = new ArrayList<>();

        userRepository.findByCompanyAndUsernameIgnoreCase(company, normalizedUsername)
                .filter(existing -> currentUserId == null || !existing.getId().equals(currentUserId))
                .ifPresent(existing -> messages.add("Username already exists in this company."));

        userRepository.findByCompanyAndMobileNumber(company, normalizedMobile)
                .filter(existing -> currentUserId == null || !existing.getId().equals(currentUserId))
                .ifPresent(existing -> messages.add("Mobile number already exists in this company."));

        userRepository.findByCompanyAndEmailIgnoreCase(company, normalizedEmail)
                .filter(existing -> currentUserId == null || !existing.getId().equals(currentUserId))
                .ifPresent(existing -> messages.add("Email already exists in this company."));

        if (!messages.isEmpty()) {
            throw new BadRequestException(String.join(" ", messages));
        }
    }

    private String normalizeEmail(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeMobile(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeUsername(String value) {
        return value == null ? null : value.trim();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private RoleName resolveRole(RoleName role) {
        return role == null ? RoleName.USER : role;
    }

    private Map<String, Object> snapshot(User user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fullName", user.getFullName());
        data.put("username", user.getUsername());
        data.put("mobileNumber", user.getMobileNumber());
        data.put("email", user.getEmail());
        data.put("role", user.getRole() != null ? user.getRole().name() : null);
        data.put("active", user.isActive());
        return data;
    }
}
