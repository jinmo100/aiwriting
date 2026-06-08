package com.jinmo.essayevaluator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用户认证相关配置。
 */
@Data
@ConfigurationProperties(prefix = "essay-evaluator.auth")
public class AuthProperties {

    /**
     * 是否开放前端自助注册。
     */
    private boolean registrationEnabled = true;

    /**
     * 第一版密码最小长度。
     */
    private int minPasswordLength = 8;
}
