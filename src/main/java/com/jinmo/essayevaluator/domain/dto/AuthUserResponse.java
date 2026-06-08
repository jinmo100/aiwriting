package com.jinmo.essayevaluator.domain.dto;

import com.jinmo.essayevaluator.domain.entity.UserAccount;

public record AuthUserResponse(
    Long id,
    String username,
    String email,
    String displayName,
    String role,
    String status
) {
    public static AuthUserResponse fromEntity(UserAccount user) {
        if (user == null) {
            return null;
        }
        return new AuthUserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getDisplayName(),
            user.getRole(),
            user.getStatus()
        );
    }
}
