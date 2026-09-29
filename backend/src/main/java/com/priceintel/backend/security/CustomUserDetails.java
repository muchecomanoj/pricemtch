package com.priceintel.backend.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.priceintel.backend.constants.SecurityConstants;
import com.priceintel.backend.constants.UserStatus;
import com.priceintel.backend.entity.User;

import lombok.Getter;

/**
 * Adapts our {@link User} to Spring Security. Authorities:
 * <ul>
 *   <li>SUPER_ADMIN → {@code ROLE_SUPER_ADMIN}</li>
 *   <li>tenant user → {@code ROLE_<ACCESS_TYPE>} (e.g. ROLE_ADMIN, ROLE_MANAGER)</li>
 * </ul>
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final transient User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String role = user.isSuperAdmin()
                ? "SUPER_ADMIN"
                : (user.getAccessType() != null ? user.getAccessType().name() : "VIEWER");
        return List.of(new SimpleGrantedAuthority(SecurityConstants.ROLE_PREFIX + role));
    }

    public Long getUserId() {
        return user.getId();
    }

    public Long getTenantId() {
        return user.getTenantId();
    }

    public boolean isSuperAdmin() {
        return user.isSuperAdmin();
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getStatus() != UserStatus.DELETED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() == UserStatus.ACTIVE;
    }
}
