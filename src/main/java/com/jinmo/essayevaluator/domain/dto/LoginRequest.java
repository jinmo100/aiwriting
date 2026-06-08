package com.jinmo.essayevaluator.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "用户名或邮箱不能为空")
    String usernameOrEmail,

    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码长度不能超过 64 位")
    String password
) {
}
