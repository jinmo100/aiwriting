package com.jinmo.essayevaluator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.essayevaluator.domain.dto.AuthResponse;
import com.jinmo.essayevaluator.domain.dto.AuthUserResponse;
import com.jinmo.essayevaluator.domain.dto.RegisterRequest;
import com.jinmo.essayevaluator.service.AuthService;
import com.jinmo.essayevaluator.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.web.context.SecurityContextRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoBean
    private SecurityContextRepository securityContextRepository;

    @Test
    void registerReturnsAuthenticatedUser() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(new AuthResponse(
            true,
            new AuthUserResponse(7L, "student01", "student01@example.com", "小明", "USER", "ACTIVE")
        ));

        mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new RegisterRequest(
                    "student01",
                    "student01@example.com",
                    "abc12345",
                    "小明"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.authenticated").value(true))
            .andExpect(jsonPath("$.data.user.username").value("student01"));
    }

    @Test
    void meReturnsUnauthenticatedWhenNoSessionUser() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.authenticated").value(false));
    }
}
