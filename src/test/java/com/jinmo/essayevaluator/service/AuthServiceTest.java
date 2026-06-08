package com.jinmo.essayevaluator.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.jinmo.essayevaluator.common.exception.BusinessException;
import com.jinmo.essayevaluator.config.AuthProperties;
import com.jinmo.essayevaluator.domain.dto.LoginRequest;
import com.jinmo.essayevaluator.domain.dto.RegisterRequest;
import com.jinmo.essayevaluator.domain.entity.UserAccount;
import com.jinmo.essayevaluator.mapper.UserAccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAccountMapper userAccountMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void registerCreatesActiveUserWithEncodedPasswordAndUserRole() {
        AuthProperties properties = new AuthProperties();
        properties.setRegistrationEnabled(true);
        AuthService service = new AuthService(userAccountMapper, passwordEncoder, properties);
        when(userAccountMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("abc12345")).thenReturn("bcrypt-hash");

        var response = service.register(new RegisterRequest(
            "Student01",
            "Student01@Example.COM",
            "abc12345",
            "小明"
        ));

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper).insert(userCaptor.capture());
        UserAccount user = userCaptor.getValue();
        assertEquals("student01", user.getUsername());
        assertEquals("student01@example.com", user.getEmail());
        assertEquals("小明", user.getDisplayName());
        assertEquals("bcrypt-hash", user.getPasswordHash());
        assertEquals("USER", user.getRole());
        assertEquals("ACTIVE", user.getStatus());
        assertTrue(response.authenticated());
        assertEquals("student01", response.user().username());
    }

    @Test
    void registerRejectsWhenRegistrationDisabled() {
        AuthProperties properties = new AuthProperties();
        properties.setRegistrationEnabled(false);
        AuthService service = new AuthService(userAccountMapper, passwordEncoder, properties);

        BusinessException error = assertThrows(BusinessException.class, () -> service.register(new RegisterRequest(
            "student01",
            "student01@example.com",
            "abc12345",
            "小明"
        )));

        assertEquals("当前暂未开放注册", error.getMessage());
    }

    @Test
    void loginAcceptsUsernameOrEmailAndUpdatesLastLogin() {
        AuthService service = new AuthService(userAccountMapper, passwordEncoder, new AuthProperties());
        UserAccount user = activeUser();
        when(userAccountMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("abc12345", "bcrypt-hash")).thenReturn(true);

        var response = service.login(new LoginRequest("student01@example.com", "abc12345"));

        assertTrue(response.authenticated());
        assertEquals(7L, response.user().id());
        assertEquals("student01", response.user().username());
        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper).updateById(userCaptor.capture());
        assertTrue(userCaptor.getValue().getLastLoginAt() != null);
    }

    @Test
    void loginRejectsDisabledUser() {
        AuthService service = new AuthService(userAccountMapper, passwordEncoder, new AuthProperties());
        UserAccount user = activeUser();
        user.setStatus("DISABLED");
        when(userAccountMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        BusinessException error = assertThrows(BusinessException.class, () -> service.login(new LoginRequest("student01", "abc12345")));

        assertEquals("账号已被禁用", error.getMessage());
    }

    private static UserAccount activeUser() {
        UserAccount user = new UserAccount();
        user.setId(7L);
        user.setUsername("student01");
        user.setEmail("student01@example.com");
        user.setDisplayName("小明");
        user.setPasswordHash("bcrypt-hash");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        return user;
    }
}
