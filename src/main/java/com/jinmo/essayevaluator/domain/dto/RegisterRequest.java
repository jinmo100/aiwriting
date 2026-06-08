package com.jinmo.essayevaluator.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度应为 3-32 位")
    String username,

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 120, message = "邮箱长度不能超过 120 位")
    String email,

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度应为 8-64 位")
    String password,

    @Size(max = 60, message = "显示名称不能超过 60 位")
    String displayName
) {
}
