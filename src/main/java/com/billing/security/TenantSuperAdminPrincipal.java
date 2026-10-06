package com.billing.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Tenant super-admin principal: credentials live on the tenant's own
 * companies row (super_admin_username/super_admin_password), NOT in users.
 * One per tenant database; full rights via PermissionService bypass.
 */
@Getter
public class TenantSuperAdminPrincipal implements UserDetails {

    private final String username;
    private final String companyCode;
    private final String databaseName;

    public TenantSuperAdminPrincipal(String username, String companyCode, String databaseName) {
        this.username = username;
        this.companyCode = companyCode;
        this.databaseName = databaseName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_OWNER"));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
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
        return true;
    }
}
