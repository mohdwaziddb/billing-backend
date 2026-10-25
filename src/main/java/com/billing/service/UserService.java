package com.billing.service;

import com.billing.entity.Company;
import com.billing.dto.PageResponse;
import com.billing.entity.User;
import com.billing.entity.enums.RoleName;
import com.billing.exception.ResourceNotFoundException;
import com.billing.dto.user.CompanyUserRequest;
import com.billing.dto.user.UserProfileResponse;
import com.billing.exception.BadRequestException;
import com.billing.repository.RefreshTokenRepository;
import com.billing.repository.UserRepository;
import com.billing.service.storage.TenantImageService;
import com.billing.util.CompanyFileJson;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AccessControlService accessControlService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TenantImageService tenantImageService;
    private final UserSessionService userSessionService;

    // Users page ke rules (controller Map leta hai, isliye DTO annotations nahi chalte — yahin check).
    static final String USER_EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$";
    static final String USER_MOBILE_PATTERN = "^\\d{10}$";
    static final String USERNAME_PATTERN = "^[A-Za-z0-9._@-]{3,50}$";
    static final int USER_PASSWORD_MIN_LENGTH = 8;
    // BCrypt sirf pehle 72 bytes padhta hai — usse lamba password chup-chaap truncate hota.
    static final int USER_PASSWORD_MAX_BYTES = 72;

    private static final String PHOTO_KIND = "user-photo";

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String email) {
        User user = accessControlService.getCurrentUser(email);
        // DATABASE-per-tenant: User.company is @Transient, resolve from tenant connection.
        Company company = null;
        try {
            company = accessControlService.requireCompany(user);
        } catch (Exception ignored) {
            // profile works without company
        }
        return userMapper.toProfile(user, company);
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
        return userRepository.findAllByOrderByCreatedAtDesc().stream()
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
        return userRepository.findAllByOrderByCreatedAtDesc().stream()
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
        validateUserRequest(request);
        validateUniqueUser(company, request.getUsername(), request.getMobileNumber(), request.getEmail(), null);
        RoleName role = resolveRole(request.getRole());
        // USERS:ADD wala admin khud se bada (OWNER) user na bana sake.
        if (role == RoleName.OWNER && !accessControlService.isCompanyOwner(accessControlService.getCurrentUser(email))) {
            throw new BadRequestException("Only an owner can create another owner");
        }

        User user = User.builder()
                .company(company)
                .fullName(request.getFullName().trim())
                .username(normalizeUsername(request.getUsername()))
                .mobileNumber(normalizeMobile(request.getMobileNumber()))
                .email(normalizeEmail(request.getEmail()))
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .active(request.getActive() == null || request.getActive())
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Map<String, Object> oldData = snapshot(user);

        validateUserRequest(request);
        validateUniqueUser(company, request.getUsername(), request.getMobileNumber(), request.getEmail(), user.getId());
        RoleName role = resolveRole(request.getRole());
        if (role != user.getRole()) {
            ensureNotCurrentUser(email, user, "You cannot change your own role");
        }
        if (Boolean.FALSE.equals(request.getActive()) && user.isActive()) {
            ensureNotCurrentUser(email, user, "You cannot deactivate your own account");
        }
        if (user.getRole() == RoleName.OWNER && user.isActive() && role != RoleName.OWNER) {
            ensureAnotherOwnerExists(company, user.getId());
        }
        if (user.getRole() == RoleName.OWNER && user.isActive() && Boolean.FALSE.equals(request.getActive())) {
            ensureAnotherOwnerExists(company, user.getId());
        }

        user.setFullName(request.getFullName().trim());
        user.setUsername(normalizeUsername(request.getUsername()));
        user.setMobileNumber(normalizeMobile(request.getMobileNumber()));
        user.setEmail(normalizeEmail(request.getEmail()));
        boolean wasActive = user.isActive();
        boolean passwordChanged = request.getPassword() != null && !request.getPassword().isBlank();
        if (passwordChanged) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user.setRole(role);
        // active na bheja ho to purani value rahe (pehle chup-chaap inactive ho jata tha).
        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }
        if (passwordChanged || (wasActive && !user.isActive())) {
            revokeSessions(user);
        }

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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Map<String, Object> oldData = snapshot(user);
        ensureNotCurrentUser(email, user, "You cannot disable your own account");
        if (user.getRole() == RoleName.OWNER && user.isActive()) {
            ensureAnotherOwnerExists(company, user.getId());
        }
        user.setActive(false);
        revokeSessions(user);
        User saved = userRepository.save(user);
        auditLogService.logDelete(email, company, "User", "User", saved.getId(), oldData);
    }

    /**
     * Logged-in user apni profile khud badle (koi bhi role, USERS permission nahi chahiye).
     * SIRF naam (photo alag endpoint se). Request me email / mobile / username / role
     * kuch bhi aaye, padha hi nahi jaata — wo sirf owner Users page se badalta hai.
     */
    @Transactional
    public UserProfileResponse updateOwnProfile(Map<String, Object> param, String email) {
        User user = requireOwnEditableUser(email);
        Company company = accessControlService.requireCompany(user);
        Map<String, Object> oldData = snapshot(user);

        String fullName = DataTypeUtility.stringValue(param.get("fullName"));
        if (fullName.length() == 0) {
            throw new BadRequestException("Full name is required");
        }
        if (fullName.length() > 120) {
            throw new BadRequestException("Full name is too long");
        }

        user.setFullName(fullName);
        User saved = userRepository.save(user);
        auditLogService.logUpdate(email, company, "User", "User", saved.getId(), oldData, snapshot(saved));
        return userMapper.toProfile(saved, company);
    }

    @Transactional
    public UserProfileResponse uploadOwnPhoto(String email, MultipartFile file) {
        User user = requireOwnEditableUser(email);
        return savePhoto(email, accessControlService.requireCompany(user), user, file);
    }

    @Transactional
    public UserProfileResponse deleteOwnPhoto(String email) {
        User user = requireOwnEditableUser(email);
        return removePhoto(email, accessControlService.requireCompany(user), user);
    }

    /** Users page (owner): kisi bhi company user ki photo add/badle. */
    @Transactional
    public UserProfileResponse uploadUserPhoto(String email, Long userId, MultipartFile file) {
        Company company = accessControlService.requireOwnerCompany(email);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return savePhoto(email, company, user, file);
    }

    @Transactional
    public UserProfileResponse deleteUserPhoto(String email, Long userId) {
        Company company = accessControlService.requireOwnerCompany(email);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return removePhoto(email, company, user);
    }

    private UserProfileResponse savePhoto(String email, Company company, User user, MultipartFile file) {
        Map<String, Object> oldData = snapshot(user);
        String oldDbValue = user.getPhotoUrl();
        String nextDbValue = tenantImageService.store(company, PHOTO_KIND, file, oldDbValue, "Photo");
        user.setPhotoUrl(nextDbValue);
        User saved = userRepository.save(user);
        // Purani file DB save ke BAAD hatao (failed save par photo kabhi orphan/gayab na ho).
        tenantImageService.cleanupReplaced(oldDbValue, nextDbValue);
        auditLogService.logUpdate(email, company, "User", "User", saved.getId(), oldData, snapshot(saved));
        return userMapper.toProfile(saved, company);
    }

    private UserProfileResponse removePhoto(String email, Company company, User user) {
        String oldDbValue = user.getPhotoUrl();
        if (DataTypeUtility.stringValue(oldDbValue).length() == 0) {
            return userMapper.toProfile(user, company);
        }
        Map<String, Object> oldData = snapshot(user);
        user.setPhotoUrl(null);
        User saved = userRepository.save(user);
        tenantImageService.delete(oldDbValue);
        auditLogService.logUpdate(email, company, "User", "User", saved.getId(), oldData, snapshot(saved));
        return userMapper.toProfile(saved, company);
    }

    /** Current user ka DB row (super-admin session synthetic hota hai, uski profile DB me nahi). */
    private User requireOwnEditableUser(String email) {
        User current = accessControlService.getCurrentUser(email);
        if (current.isSuperAdmin() || current.getId() == null) {
            throw new BadRequestException("Super admin profile cannot be edited here");
        }
        return userRepository.findById(current.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    // Password badla ya user deactivate hua: purane access tokens + refresh tokens turant band.
    private void revokeSessions(User user) {
        user.setTokensValidAfter(LocalDateTime.now());
        userSessionService.revokeAll(user, "Password or account status changed");
        refreshTokenRepository.deleteByUser(user);
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
        // Role zaroori hai: pehle missing/galat role chup-chaap USER ban jata tha (edit par owner/admin demote).
        String roleString = DataTypeUtility.stringValue(param.get("role"));
        if (roleString.length() > 0) {
            try {
                companyUserRequest.setRole(RoleName.valueOf(roleString.toUpperCase()));
            } catch (Exception exception) {
                throw new BadRequestException("Select a valid role");
            }
        } else {
            Object roleObject = param.get("role");
            if (roleObject instanceof RoleName) {
                companyUserRequest.setRole((RoleName) roleObject);
            } else {
                companyUserRequest.setRole(null);
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

    // Khud ko disable karne se session turant mar jata hai aur wapas aane ka rasta nahi bachta.
    private void ensureNotCurrentUser(String email, User targetUser, String message) {
        User currentUser = accessControlService.getCurrentUser(email);
        if (currentUser != null && currentUser.getId() != null && Objects.equals(currentUser.getId(), targetUser.getId())) {
            throw new BadRequestException(message);
        }
    }

    void validateUserRequest(CompanyUserRequest request) {
        String fullName = DataTypeUtility.stringValue(request.getFullName());
        String username = DataTypeUtility.stringValue(request.getUsername());
        String userEmail = DataTypeUtility.stringValue(request.getEmail());
        String mobileNumber = DataTypeUtility.stringValue(request.getMobileNumber());
        if (fullName.length() == 0) {
            throw new BadRequestException("Full name is required");
        }
        if (fullName.length() > 100) {
            throw new BadRequestException("Full name must be 100 characters or less");
        }
        if (username.length() == 0) {
            throw new BadRequestException("Username is required");
        }
        if (!username.matches(USERNAME_PATTERN)) {
            throw new BadRequestException("Username must be 3-50 characters: letters, numbers, dot, underscore, @ or hyphen (no spaces)");
        }
        if (userEmail.length() == 0) {
            throw new BadRequestException("Email address is required");
        }
        if (!userEmail.matches(USER_EMAIL_PATTERN)) {
            throw new BadRequestException("Enter a valid email address");
        }
        if (mobileNumber.length() == 0) {
            throw new BadRequestException("Mobile number is required");
        }
        if (!mobileNumber.matches(USER_MOBILE_PATTERN)) {
            throw new BadRequestException("Enter a 10-digit mobile number");
        }
        if (request.getRole() == null) {
            throw new BadRequestException("Role is required");
        }
        String password = request.getPassword();
        if (password != null && !password.isBlank() && password.trim().length() < USER_PASSWORD_MIN_LENGTH) {
            throw new BadRequestException("Password must be at least " + USER_PASSWORD_MIN_LENGTH + " characters");
        }
        if (password != null && password.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > USER_PASSWORD_MAX_BYTES) {
            throw new BadRequestException("Password must be " + USER_PASSWORD_MAX_BYTES + " characters or less");
        }
    }

    private void ensureAnotherOwnerExists(Company company, Long excludedUserId) {
        boolean hasAnotherOwner = userRepository.findAllByOrderByCreatedAtDesc().stream()
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

        // Reserved: tenant super-admin login (companies row). A normal user
        // with this identifier would shadow or be shadowed by it at login.
        String superAdminUsername = company != null ? company.getSuperAdminUsername() : null;
        if (superAdminUsername != null && !superAdminUsername.isBlank()) {
            String reserved = superAdminUsername.trim();
            if (reserved.equalsIgnoreCase(normalizedUsername)
                    || reserved.equalsIgnoreCase(normalizedEmail)
                    || reserved.equalsIgnoreCase(normalizedMobile)) {
                messages.add("This identifier is reserved for super-admin login.");
            }
        }

        // findAll* variants: purane duplicate rows hon to bhi Optional lookup crash na kare.
        if (hasOtherUser(userRepository.findAllByUsernameIgnoreCase(normalizedUsername), currentUserId)) {
            messages.add("Username already exists in this company.");
        } else if (hasOtherUser(userRepository.findAllByEmailIgnoreCase(normalizedUsername), currentUserId)
                || hasOtherUser(userRepository.findAllByMobileNumber(normalizedUsername), currentUserId)) {
            // Login username / email / mobile teeno se hota hai — kisi aur ka email/mobile username na bane.
            messages.add("Username is already used as another user's email or mobile number.");
        }

        if (hasOtherUser(userRepository.findAllByMobileNumber(normalizedMobile), currentUserId)) {
            messages.add("Mobile number already exists in this company.");
        } else if (hasOtherUser(userRepository.findAllByUsernameIgnoreCase(normalizedMobile), currentUserId)) {
            messages.add("Mobile number is already used as another user's username.");
        }

        if (hasOtherUser(userRepository.findAllByEmailIgnoreCase(normalizedEmail), currentUserId)) {
            messages.add("Email already exists in this company.");
        } else if (hasOtherUser(userRepository.findAllByUsernameIgnoreCase(normalizedEmail), currentUserId)) {
            messages.add("Email is already used as another user's username.");
        }

        if (!messages.isEmpty()) {
            throw new BadRequestException(String.join(" ", messages));
        }
    }

    private boolean hasOtherUser(List<User> matches, Long currentUserId) {
        if (matches == null) {
            return false;
        }
        return matches.stream().anyMatch(existing -> currentUserId == null || !Objects.equals(existing.getId(), currentUserId));
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
        data.put("photo", CompanyFileJson.extractFileName(user.getPhotoUrl()));
        return data;
    }
}
