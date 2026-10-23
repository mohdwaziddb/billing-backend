package com.billing.service;

import com.billing.dto.user.UserProfileResponse;
import com.billing.entity.Company;
import com.billing.entity.User;
import com.billing.entity.enums.RoleName;
import com.billing.exception.BadRequestException;
import com.billing.repository.RefreshTokenRepository;
import com.billing.repository.UserRepository;
import com.billing.service.storage.TenantImageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileRulesTest {

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

    private final Company company = Company.builder().id(1L).name("Acme").build();

    private UserService service() {
        return new UserService(userRepository, new UserMapper(), accessControlService, passwordEncoder, auditLogService,
                refreshTokenRepository, tenantImageService);
    }

    @Test
    void onlyNameChangesEvenIfDisabledFieldsAreSent() {
        User user = user(7L, RoleName.USER);
        stubCurrent(user);
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        // Frontend se "disabled" hata ke bheja gaya data: sirf naam lagna chahiye.
        Map<String, Object> param = new HashMap<>();
        param.put("fullName", "New Name");
        param.put("email", "new@acme.in");
        param.put("mobileNumber", "9876543210");
        param.put("role", "OWNER");
        param.put("active", "false");
        param.put("username", "hacker");

        UserProfileResponse response = service().updateOwnProfile(param, "u");

        assertEquals("New Name", response.getFullName());
        assertEquals("ravi@acme.in", response.getEmail());
        assertEquals("9999999999", response.getMobileNumber());
        assertEquals("USER", response.getRole());
        assertEquals("ravi", response.getUsername());
        assertEquals(true, response.isActive());
    }

    @Test
    void blankOrTooLongNameIsRejected() {
        User user = user(7L, RoleName.USER);
        stubCurrent(user);

        assertThrows(BadRequestException.class, () -> service().updateOwnProfile(Map.of("fullName", "  "), "u"));
        assertThrows(BadRequestException.class, () -> service().updateOwnProfile(Map.of("fullName", "x".repeat(121)), "u"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void superAdminSessionCannotEditProfile() {
        User synthetic = User.builder().fullName("Super").role(RoleName.OWNER).superAdmin(true).build();
        when(accessControlService.getCurrentUser("u")).thenReturn(synthetic);

        assertThrows(BadRequestException.class, () -> service().updateOwnProfile(Map.of("fullName", "A"), "u"));
    }

    @Test
    void photoIsSavedBeforeOldFileCleanup() {
        User user = user(7L, RoleName.USER);
        user.setPhotoUrl("{\"url\":\"/uploads/test/old.jpg\",\"key\":\"test/old.jpg\"}");
        stubCurrent(user);
        String next = "{\"url\":\"/uploads/test/me.jpg\",\"key\":\"test/me.jpg\",\"file_name\":\"me.jpg\"}";
        when(tenantImageService.store(eq(company), eq("user-photo"), any(), anyString(), eq("Photo"))).thenReturn(next);
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        UserProfileResponse response = service().uploadOwnPhoto("u", new MockMultipartFile("photo", "me.jpg", "image/jpeg", new byte[]{1}));

        assertEquals("/uploads/test/me.jpg", response.getPhotoUrl());
        assertEquals("me.jpg", response.getPhotoFile().getFileName());
        var order = inOrder(userRepository, tenantImageService);
        order.verify(userRepository).save(user);
        order.verify(tenantImageService).cleanupReplaced("{\"url\":\"/uploads/test/old.jpg\",\"key\":\"test/old.jpg\"}", next);
    }

    @Test
    void removingPhotoClearsColumnAndDeletesFile() {
        User user = user(7L, RoleName.USER);
        String old = "{\"url\":\"/uploads/test/me.jpg\",\"key\":\"test/me.jpg\"}";
        user.setPhotoUrl(old);
        stubCurrent(user);
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        UserProfileResponse response = service().deleteOwnPhoto("u");

        assertEquals(null, response.getPhotoUrl());
        verify(tenantImageService).delete(old);
    }

    private void stubCurrent(User user) {
        lenient().when(accessControlService.getCurrentUser("u")).thenReturn(user);
        lenient().when(accessControlService.requireCompany(user)).thenReturn(company);
        lenient().when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
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
