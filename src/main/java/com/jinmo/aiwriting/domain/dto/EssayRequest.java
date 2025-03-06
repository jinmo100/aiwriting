package com.jinmo.aiwriting.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EssayRequest(
    @NotBlank(message = "作文内容不能为空")
    @Size(min = 100, max = 2000, message = "作文长度必须在100-2000字之间")
    String content
) {} 