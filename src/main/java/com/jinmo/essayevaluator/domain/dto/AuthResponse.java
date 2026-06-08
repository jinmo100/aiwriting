package com.jinmo.essayevaluator.domain.dto;

public record AuthResponse(
    boolean authenticated,
    AuthUserResponse user
) {
    public static AuthResponse unauthenticated() {
        return new AuthResponse(false, null);
    }
}
