package com.billing.security;

import com.billing.entity.User;
import com.billing.entity.enums.RoleName;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final boolean active;
    private final boolean companyActive;
    private final String role;
    private final String companyCode;
    private final String databaseName;

    public CustomUserDetails(User user) {
        this(user, null, null);
    }

    public CustomUserDetails(User user, String companyCode, String databaseName) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.active = user.isActive();
        boolean companyActiveValue = true;
        if (user.getCompany() != null) {
            try {
                companyActiveValue = user.getCompany().isActive();
            } catch (Exception e) {
                // company relation may be removed in DATABASE-per-tenant mode
            }
        }
        this.companyActive = companyActiveValue;
        this.role = (user.getRole() == null ? RoleName.USER : user.getRole()).name();
        String code = companyCode;
        String db = databaseName;
        if ((code == null || db == null) && user.getCompany() != null) {
            try {
                if (code == null) {
                    code = user.getCompany().getCode();
                }
                if (db == null) {
                    // No silent default: unresolved database stays null and is handled
                    // explicitly by callers (AuthService / CustomUserDetailsService).
                    db = user.getCompany().getDatabaseName();
                }
            } catch (Exception e) {
                // ignore
            }
        }
        this.companyCode = code;
        this.databaseName = db;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    public Long getCompanyId() {
        return null;
    }

    public boolean isCompanyActive() {
        return companyActive;
    }
}
