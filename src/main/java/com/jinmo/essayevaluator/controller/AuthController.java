package com.jinmo.essayevaluator.controller;

import com.jinmo.essayevaluator.common.response.ApiResponse;
import com.jinmo.essayevaluator.domain.dto.AuthResponse;
import com.jinmo.essayevaluator.domain.dto.LoginRequest;
import com.jinmo.essayevaluator.domain.dto.RegisterRequest;
import com.jinmo.essayevaluator.security.UserAccountPrincipal;
import com.jinmo.essayevaluator.service.AuthService;
import com.jinmo.essayevaluator.service.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Tag(name = "用户认证", description = "注册、登录、登出和当前用户")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;
    private final SecurityContextRepository securityContextRepository;

    @Operation(summary = "注册")
    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(
        @Valid @RequestBody RegisterRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        rateLimitService.check("auth:register:ip:" + clientIp(httpRequest), 10, Duration.ofHours(1));
        AuthResponse response = authService.register(request);
        saveAuthentication(response, httpRequest, httpResponse);
        return ApiResponse.success("注册成功", response);
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        String principal = request.usernameOrEmail() == null ? "" : request.usernameOrEmail().trim().toLowerCase();
        rateLimitService.check("auth:login:ip:" + clientIp(httpRequest), 10, Duration.ofMinutes(15));
        rateLimitService.check("auth:login:user:" + principal, 10, Duration.ofMinutes(15));
        AuthResponse response = authService.login(request);
        saveAuthentication(response, httpRequest, httpResponse);
        return ApiResponse.success("登录成功", response);
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContext emptyContext = SecurityContextHolder.createEmptyContext();
        securityContextRepository.saveContext(emptyContext, request, response);
        return ApiResponse.success("已退出登录", null);
    }

    @Operation(summary = "当前用户")
    @GetMapping("/me")
    public ApiResponse<AuthResponse> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof UserAccountPrincipal principal)) {
            return ApiResponse.success(AuthResponse.unauthenticated());
        }
        return ApiResponse.success(new AuthResponse(true, principal.toResponse()));
    }

    private void saveAuthentication(AuthResponse response, HttpServletRequest request, HttpServletResponse servletResponse) {
        if (!response.authenticated() || response.user() == null) {
            return;
        }
        UserAccountPrincipal principal = UserAccountPrincipal.from(response.user());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, servletResponse);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
