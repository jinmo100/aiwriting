package com.jinmo.essayevaluator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jinmo.essayevaluator.common.exception.BusinessException;
import com.jinmo.essayevaluator.config.AuthProperties;
import com.jinmo.essayevaluator.domain.dto.AuthResponse;
import com.jinmo.essayevaluator.domain.dto.AuthUserResponse;
import com.jinmo.essayevaluator.domain.dto.LoginRequest;
import com.jinmo.essayevaluator.domain.dto.RegisterRequest;
import com.jinmo.essayevaluator.domain.entity.UserAccount;
import com.jinmo.essayevaluator.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 用户注册与账号基础规则。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!authProperties.isRegistrationEnabled()) {
            throw new BusinessException("当前暂未开放注册");
        }

        String username = normalizeRequired(request.username(), "用户名不能为空").toLowerCase(Locale.ROOT);
        String email = normalizeRequired(request.email(), "邮箱不能为空").toLowerCase(Locale.ROOT);
        String displayName = StringUtils.hasText(request.displayName()) ? request.displayName().trim() : username;
        validatePassword(request.password());
        ensureUsernameAvailable(username);
        ensureEmailAvailable(email);

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setEmailVerified(false);

        userAccountMapper.insert(user);
        return new AuthResponse(true, AuthUserResponse.fromEntity(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String usernameOrEmail = normalizeRequired(request.usernameOrEmail(), "用户名或邮箱不能为空").toLowerCase();
        UserAccount user = userAccountMapper.selectOne(
            new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, usernameOrEmail)
                .or()
                .eq(UserAccount::getEmail, usernameOrEmail)
                .last("LIMIT 1")
        );
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userAccountMapper.updateById(user);
        return new AuthResponse(true, AuthUserResponse.fromEntity(user));
    }

    private void ensureUsernameAvailable(String username) {
        Long count = userAccountMapper.selectCount(
            new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username)
        );
        if (count != null && count > 0) {
            throw new BusinessException("用户名已存在");
        }
    }

    private void ensureEmailAvailable(String email) {
        Long count = userAccountMapper.selectCount(
            new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getEmail, email)
        );
        if (count != null && count > 0) {
            throw new BusinessException("邮箱已存在");
        }
    }

    private void validatePassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw new BusinessException("密码不能为空");
        }
        if (password.length() < authProperties.getMinPasswordLength() || password.length() > 64) {
            throw new BusinessException("密码长度应为 8-64 位");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessException("密码至少需要包含字母和数字");
        }
    }

    private static String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }
}
