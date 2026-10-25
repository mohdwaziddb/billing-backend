package com.billing.service;

import com.billing.dto.company.CompanySettingsRequest;
import com.billing.dto.user.CompanyUserRequest;
import com.billing.entity.Company;
import com.billing.entity.User;
import com.billing.entity.enums.RoleName;
import com.billing.exception.BadRequestException;
import com.billing.repository.RefreshTokenRepository;
import com.billing.repository.TaxMasterRepository;
import com.billing.repository.UserRepository;
import com.billing.service.storage.TenantImageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Setup pages (Users, Tax Master) ke service rules — frontend inhi par bharosa karta hai. */
@ExtendWith(MockitoExtension.class)
class SetupMasterRulesTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private TenantImageService tenantImageService;
    @Mock
    private UserSessionService userSessionService;
    @Mock
    private TaxMasterRepository taxMasterRepository;

    private final Company company = Company.builder().id(1L).name("Acme").build();

    private UserService userService() {
        return new UserService(userRepository, new UserMapper(), accessControlService, passwordEncoder, auditLogService,
                refreshTokenRepository, tenantImageService, userSessionService);
    }

    @Test
    void validUserRequestPasses() {
        assertDoesNotThrow(() -> userService().validateUserRequest(request("Asha Rao", "asha.rao", "asha@acme.in", "9876543210", "Secret#123")));
    }

    @Test
    void userRequestRejectsBadEmailMobileUsernameAndShortPassword() {
        UserService service = userService();
        assertEquals("Enter a valid email address",
                assertThrows(BadRequestException.class, () -> service.validateUserRequest(request("Asha", "asha", "asha@acme", "9876543210", null))).getMessage());
        assertEquals("Enter a 10-digit mobile number",
                assertThrows(BadRequestException.class, () -> service.validateUserRequest(request("Asha", "asha", "asha@acme.in", "98765", null))).getMessage());
        assertThrows(BadRequestException.class, () -> service.validateUserRequest(request("Asha", "asha rao", "asha@acme.in", "9876543210", null)));
        assertEquals("Full name is required",
                assertThrows(BadRequestException.class, () -> service.validateUserRequest(request("  ", "asha", "asha@acme.in", "9876543210", null))).getMessage());
        assertEquals("Password must be at least 8 characters",
                assertThrows(BadRequestException.class, () -> service.validateUserRequest(request("Asha", "asha", "asha@acme.in", "9876543210", "short"))).getMessage());
    }

    @Test
    void ownerCannotDisableOwnAccount() {
        User owner = user(5L, RoleName.OWNER);
        lenient().when(accessControlService.requireOwnerCompany("u")).thenReturn(company);
        lenient().when(accessControlService.getCurrentUser("u")).thenReturn(owner);
        lenient().when(userRepository.findById(5L)).thenReturn(Optional.of(owner));

        BadRequestException error = assertThrows(BadRequestException.class, () -> userService().deactivateCompanyUser("u", 5L));

        assertEquals("You cannot disable your own account", error.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void nonOwnerCannotCreateOwnerUser() {
        User admin = user(9L, RoleName.ADMIN);
        lenient().when(accessControlService.getCurrentCompany("u")).thenReturn(company);
        lenient().when(accessControlService.getCurrentUser("u")).thenReturn(admin);
        lenient().when(accessControlService.isCompanyOwner(admin)).thenReturn(false);
        CompanyUserRequest ownerRequest = request("New Owner", "new.owner", "owner2@acme.in", "9876500000", "Secret#123");
        ownerRequest.setRole(RoleName.OWNER);

        BadRequestException error = assertThrows(BadRequestException.class, () -> userService().createCompanyUser("u", ownerRequest));

        assertEquals("Only an owner can create another owner", error.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void defaultTaxMustBeActive() {
        TaxValidator validator = new TaxValidator(taxMasterRepository);
        assertThrows(BadRequestException.class, () -> validator.validateDefaultIsActive(true, false));
        assertDoesNotThrow(() -> validator.validateDefaultIsActive(true, true));
        assertDoesNotThrow(() -> validator.validateDefaultIsActive(false, false));
        assertDoesNotThrow(() -> validator.validateDefaultIsActive(null, false));
    }

    @Test
    void companySettingsRequireValidContactAndTaxFormats() {
        assertDoesNotThrow(() -> CompanyService.validateSettingsRequest(companyRequest("9876543210", "07ABCDE1234F1Z5", "110094")));
        assertEquals("Enter a 10-digit mobile number",
                assertThrows(BadRequestException.class, () -> CompanyService.validateSettingsRequest(companyRequest("98765", null, null))).getMessage());
        assertThrows(BadRequestException.class, () -> CompanyService.validateSettingsRequest(companyRequest("9876543210", "test1", null)));
        assertEquals("Enter a 6-digit pincode",
                assertThrows(BadRequestException.class, () -> CompanyService.validateSettingsRequest(companyRequest("9876543210", null, "Pincode"))).getMessage());
    }

    private static CompanySettingsRequest companyRequest(String phone, String gstin, String pincode) {
        CompanySettingsRequest request = new CompanySettingsRequest();
        request.setName("Acme Traders");
        request.setEmail("accounts@acme.in");
        request.setPhone(phone);
        request.setGstRegistered(gstin != null);
        request.setGstin(gstin);
        request.setPincode(pincode);
        request.setPanNumber("abcde1234f");
        return request;
    }

    private static CompanyUserRequest request(String fullName, String username, String email, String mobile, String password) {
        CompanyUserRequest request = new CompanyUserRequest();
        request.setFullName(fullName);
        request.setUsername(username);
        request.setEmail(email);
        request.setMobileNumber(mobile);
        request.setPassword(password);
        request.setRole(RoleName.USER);
        request.setActive(true);
        return request;
    }

    private static User user(Long id, RoleName role) {
        return User.builder()
                .id(id)
                .fullName("Ravi")
                .username("ravi")
                .email("ravi@acme.in")
                .mobileNumber("9999999999")
                .password("x")
                .role(role)
                .active(true)
                .build();
    }
}
