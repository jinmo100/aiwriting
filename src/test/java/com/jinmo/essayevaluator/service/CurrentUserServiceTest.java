package com.jinmo.essayevaluator.service;

import com.jinmo.essayevaluator.common.exception.BusinessException;
import com.jinmo.essayevaluator.security.UserAccountPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrentUserServiceTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireUserIdReadsAuthenticatedPrincipal() {
        UserAccountPrincipal principal = new UserAccountPrincipal(7L, "student01", "s@example.com", "小明", "USER", "ACTIVE");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        ));

        assertEquals(7L, new CurrentUserService().requireUserId());
    }

    @Test
    void requireUserIdRejectsAnonymousRequest() {
        BusinessException error = assertThrows(BusinessException.class, () -> new CurrentUserService().requireUserId());

        assertEquals("请先登录", error.getMessage());
    }

    @Test
    void requireAdminAllowsAdminPrincipal() {
        UserAccountPrincipal principal = new UserAccountPrincipal(1L, "admin", "admin@example.com", "管理员", "ADMIN", "ACTIVE");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        ));

        assertDoesNotThrow(() -> new CurrentUserService().requireAdmin());
    }

    @Test
    void requireAdminRejectsNonAdminPrincipal() {
        UserAccountPrincipal principal = new UserAccountPrincipal(7L, "student01", "s@example.com", "小明", "USER", "ACTIVE");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        ));

        BusinessException error = assertThrows(BusinessException.class, () -> new CurrentUserService().requireAdmin());

        assertEquals("需要管理员权限", error.getMessage());
    }
}
