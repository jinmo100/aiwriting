package com.jinmo.essayevaluator.security;

import com.jinmo.essayevaluator.domain.dto.AuthUserResponse;
import com.jinmo.essayevaluator.domain.entity.UserAccount;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

/**
 * 保存在 Spring Security Session 中的最小用户身份。
 */
public record UserAccountPrincipal(
    Long id,
    String username,
    String email,
    String displayName,
    String role,
    String status
) implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    public static UserAccountPrincipal from(AuthUserResponse user) {
        return new UserAccountPrincipal(
            user.id(),
            user.username(),
            user.email(),
            user.displayName(),
            user.role(),
            user.status()
        );
    }

    public static UserAccountPrincipal from(UserAccount user) {
        return new UserAccountPrincipal(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getDisplayName(),
            user.getRole(),
            user.getStatus()
        );
    }

    public AuthUserResponse toResponse() {
        return new AuthUserResponse(id, username, email, displayName, role, status);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return "ACTIVE".equals(status);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equals(status);
    }
}
