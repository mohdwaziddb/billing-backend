package com.billing.security;

import com.billing.multitenancy.TenantContextHolder;
import com.billing.repository.CompanyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final CompanyRepository companyRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = authHeader.substring(7);
            Long userId;
            String username;
            String authType;
            try {
                authType = jwtService.extractAuthType(token);
                userId = jwtService.extractUserId(token);
                username = jwtService.extractUsername(token);
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            // Bind tenant from the token BEFORE any user lookup: tenant ids
            // collide across databases (every tenant starts at id 1), so the
            // lookup must already run in the token's own database.
            // (RequestInterceptor later re-validates header-vs-token binding.)
            if (("USER".equals(authType) || "SUPER_ADMIN".equals(authType))
                    && (TenantContextHolder.getTenant() == null || TenantContextHolder.getTenant().isBlank())) {
                String claimDb = jwtService.extractDatabaseName(token);
                if (claimDb == null || claimDb.isBlank()) {
                    String claimCode = jwtService.extractCompanyCode(token);
                    claimDb = claimCode != null ? claimCode.toLowerCase(java.util.Locale.ROOT) : null;
                }
                if (claimDb != null && !claimDb.isBlank()) {
                    TenantContextHolder.setTenantId(claimDb);
                }
            }

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if ("PLATFORM_ADMIN".equals(authType)) {                    PlatformAdminPrincipal platformAdminPrincipal = new PlatformAdminPrincipal(username);
                    if (jwtService.isTokenValid(token, platformAdminPrincipal)) {
                        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                                platformAdminPrincipal,
                                null,
                                platformAdminPrincipal.getAuthorities()
                        );
                        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    }
                    filterChain.doFilter(request, response);
                    return;
                }

                if ("SUPER_ADMIN".equals(authType)) {
                    String code = jwtService.extractCompanyCode(token);
                    String dbName = jwtService.extractDatabaseName(token);
                    TenantSuperAdminPrincipal superAdminPrincipal =
                            new TenantSuperAdminPrincipal(username, code, dbName);
                    if (!jwtService.isTokenValid(token, superAdminPrincipal)
                            || !isSuperAdminCompanyActive(code, dbName)) {
                        SecurityContextHolder.clearContext();
                        writeCompanyInactiveResponse(response);
                        return;
                    }
                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                            superAdminPrincipal,
                            null,
                            superAdminPrincipal.getAuthorities()
                    );
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    if (dbName != null && !dbName.isBlank()
                            && (TenantContextHolder.getTenant() == null || TenantContextHolder.getTenant().isBlank())) {
                        TenantContextHolder.setTenantId(dbName);
                    }
                    filterChain.doFilter(request, response);
                    return;
                }

                UserDetails userDetails = userId != null
                        ? userDetailsService.loadUserById(userId)
                        : userDetailsService.loadUserByUsername(username);
                if (jwtService.isTokenValid(token, userDetails)) {
                    if (userDetails instanceof CustomUserDetails customUserDetails
                            && !customUserDetails.isCompanyActive()) {
                        SecurityContextHolder.clearContext();
                        writeCompanyInactiveResponse(response);
                        return;
                    }
                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    if (userDetails instanceof CustomUserDetails customUserDetails) {
                        String dbName = customUserDetails.getDatabaseName();
                        if (dbName == null || dbName.isBlank()) {
                            dbName = jwtService.extractDatabaseName(token);
                        }
                        if (dbName == null || dbName.isBlank()) {
                            String code = jwtService.extractCompanyCode(token);
                            // DATABASE-per-tenant: database = lower(code), no billing_company_ prefix (e.g. MAACREATION -> maacreation)
                            dbName = code != null ? code.toLowerCase(java.util.Locale.ROOT) : null;
                        }
                        if (dbName != null && !dbName.isBlank()) {
                            if (TenantContextHolder.getTenant() == null || TenantContextHolder.getTenant().isBlank()) {
                                TenantContextHolder.setTenantId(dbName);
                            }
                        }
                    }
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * Fail-closed company check for tenant super-admin tokens: the token is
     * honored only while its company row exists and is active. Tenant context
     * is set from the token claims first so the lookup hits the right database.
     */
    private boolean isSuperAdminCompanyActive(String companyCode, String databaseName) {
        if (companyCode == null || companyCode.isBlank() || databaseName == null || databaseName.isBlank()) {
            return false;
        }
        try {
            if (TenantContextHolder.getTenant() == null || TenantContextHolder.getTenant().isBlank()) {
                TenantContextHolder.setTenantId(databaseName.trim());
            }
            return companyRepository.findByCodeIgnoreCase(companyCode.trim().toUpperCase(java.util.Locale.ROOT))
                    .or(() -> companyRepository.findByCodeIgnoreCase(companyCode.trim()))
                    .map(com.billing.entity.Company::isActive)
                    .orElse(false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void writeCompanyInactiveResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"success\":false,\"message\":\"Company is inactive\"}");
    }
}
