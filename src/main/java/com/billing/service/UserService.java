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
import com.billing.util.DataTypeUtility;
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
    public UserProfileResponse getProfile(Map<String, Object> param, String email) {
        Map<String, Object> sanitizedParam = param;
        if (sanitizedParam == null) {
            sanitizedParam = Map.of();
        }
        String emailFilter = DataTypeUtility.stringValue(sanitizedParam.get("email"));
        if (emailFilter.length() > 0) {
            // placeholder for future extension with braces
        }
        return getProfile(email);
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

    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> pageCompanyUsers(Map<String, Object> param, String email) {
        String nameFilter = DataTypeUtility.stringValue(param.get("name"));
        if (nameFilter.length() == 0) {
            nameFilter = null;
        }
        String usernameFilter = DataTypeUtility.stringValue(param.get("username"));
        if (usernameFilter.length() == 0) {
            usernameFilter = null;
        }
        String mobileNumberFilter = DataTypeUtility.stringValue(param.get("mobileNumber"));
        if (mobileNumberFilter.length() == 0) {
            mobileNumberFilter = DataTypeUtility.stringValue(param.get("mobile_number"));
        }
        if (mobileNumberFilter.length() == 0) {
            mobileNumberFilter = DataTypeUtility.stringValue(param.get("mobile"));
        }
        if (mobileNumberFilter.length() == 0) {
            mobileNumberFilter = null;
        }
        String userEmailFilter = DataTypeUtility.stringValue(param.get("email"));
        if (userEmailFilter.length() == 0) {
            userEmailFilter = null;
        }
        String searchFilter = DataTypeUtility.stringValue(param.get("search"));
        if (searchFilter.length() == 0) {
            searchFilter = null;
        }
        RoleName roleValue = null;
        String roleString = DataTypeUtility.stringValue(param.get("role"));
        if (roleString.length() > 0) {
            try {
                roleValue = RoleName.valueOf(roleString.toUpperCase());
            } catch (Exception exception) {
                roleValue = null;
            }
        }
        Boolean activeStatus = null;
        Object activeObject = param.get("active");
        if (activeObject != null) {
            if (activeObject instanceof Boolean) {
                activeStatus = (Boolean) activeObject;
            } else {
                String activeString = DataTypeUtility.stringValue(activeObject);
                if (activeString.length() > 0) {
                    activeStatus = DataTypeUtility.booleanValue(activeString);
                }
            }
        }
        int pageNumber = DataTypeUtility.integerValue(param.get("page"));
        int pageSize = DataTypeUtility.integerValue(param.get("size"));
        if (pageSize == 0) {
            pageSize = 20;
        }
        return pageCompanyUsers(email, pageNumber, pageSize, nameFilter, usernameFilter, mobileNumberFilter, userEmailFilter, searchFilter, roleValue, activeStatus);
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponse> activeReferralUsers(String email) {
        Company company = accessControlService.getCurrentCompany(email);
        return userRepository.findByCompanyOrderByCreatedAtDesc(company).stream()
                .filter(User::isActive)
                .map(userMapper::toProfile)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponse> activeReferralUsers(Map<String, Object> param, String email) {
        Map<String, Object> sanitizedParam = param;
        if (sanitizedParam == null) {
            sanitizedParam = Map.of();
        }
        String searchFilter = DataTypeUtility.stringValue(sanitizedParam.get("search"));
        if (searchFilter.length() > 0) {
            String normalizedSearch = searchFilter.trim().toLowerCase();
            if (normalizedSearch.length() > 0) {
                // placeholder for filtering with braces
            }
        }
        return activeReferralUsers(email);
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
    public UserProfileResponse createCompanyUser(Map<String, Object> param, String email) {
        CompanyUserRequest companyUserRequest = mapToCompanyUserRequest(param);
        return createCompanyUser(email, companyUserRequest);
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
    public UserProfileResponse updateCompanyUser(Map<String, Object> param, Long userId, String email) {
        Long userIdValue = DataTypeUtility.getForeignKeyValue(userId);
        if (userIdValue == null) {
            userIdValue = DataTypeUtility.getForeignKeyValue(param.get("userId"));
        }
        if (userIdValue == null) {
            userIdValue = DataTypeUtility.getForeignKeyValue(param.get("user_id"));
        }
        CompanyUserRequest companyUserRequest = mapToCompanyUserRequest(param);
        return updateCompanyUser(email, userIdValue, companyUserRequest);
    }

    @Transactional
    public UserProfileResponse updateCompanyUser(Map<String, Object> param, String email) {
        Long userIdValue = DataTypeUtility.getForeignKeyValue(param.get("userId"));
        if (userIdValue == null) {
            userIdValue = DataTypeUtility.getForeignKeyValue(param.get("user_id"));
        }
        if (userIdValue == null) {
            userIdValue = DataTypeUtility.getForeignKeyValue(param.get("id"));
        }
        CompanyUserRequest companyUserRequest = mapToCompanyUserRequest(param);
        return updateCompanyUser(email, userIdValue, companyUserRequest);
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

    private CompanyUserRequest mapToCompanyUserRequest(Map<String, Object> param) {
        CompanyUserRequest companyUserRequest = new CompanyUserRequest();
        String fullNameValue = DataTypeUtility.stringValue(param.get("fullName"));
        if (fullNameValue.length() == 0) {
            fullNameValue = DataTypeUtility.stringValue(param.get("full_name"));
        }
        companyUserRequest.setFullName(fullNameValue);
        String mobileNumberValue = DataTypeUtility.stringValue(param.get("mobileNumber"));
        if (mobileNumberValue.length() == 0) {
            mobileNumberValue = DataTypeUtility.stringValue(param.get("mobile_number"));
        }
        if (mobileNumberValue.length() == 0) {
            mobileNumberValue = DataTypeUtility.stringValue(param.get("mobile"));
        }
        companyUserRequest.setMobileNumber(mobileNumberValue);
        String emailValue = DataTypeUtility.stringValue(param.get("email"));
        companyUserRequest.setEmail(emailValue);
        String usernameValue = DataTypeUtility.stringValue(param.get("username"));
        if (usernameValue.length() == 0) {
            usernameValue = DataTypeUtility.stringValue(param.get("userName"));
        }
        companyUserRequest.setUsername(usernameValue);
        String passwordValue = DataTypeUtility.stringValue(param.get("password"));
        if (passwordValue.length() == 0) {
            passwordValue = null;
        }
        companyUserRequest.setPassword(passwordValue);
        String roleString = DataTypeUtility.stringValue(param.get("role"));
        if (roleString.length() > 0) {
            try {
                companyUserRequest.setRole(RoleName.valueOf(roleString.toUpperCase()));
            } catch (Exception exception) {
                companyUserRequest.setRole(RoleName.USER);
            }
        } else {
            Object roleObject = param.get("role");
            if (roleObject instanceof RoleName) {
                companyUserRequest.setRole((RoleName) roleObject);
            } else {
                companyUserRequest.setRole(RoleName.USER);
            }
        }
        Object activeObject = param.get("active");
        if (activeObject != null) {
            if (activeObject instanceof Boolean) {
                companyUserRequest.setActive((Boolean) activeObject);
            } else {
                String activeString = DataTypeUtility.stringValue(activeObject);
                if (activeString.length() > 0) {
                    companyUserRequest.setActive(DataTypeUtility.booleanValue(activeString));
                } else {
                    companyUserRequest.setActive(true);
                }
            }
        } else {
            companyUserRequest.setActive(true);
        }
        return companyUserRequest;
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
