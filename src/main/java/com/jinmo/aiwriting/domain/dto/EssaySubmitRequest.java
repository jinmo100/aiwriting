package com.jinmo.aiwriting.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 作文提交请求DTO
 */
public record EssaySubmitRequest(
    @NotBlank(message = "作文内容不能为空")
    @Size(min = 50, max = 10000, message = "作文字数应在50-10000字之间")
    String content,

    String essayType,  // IELTS, TOEFL, CET4, CET6

    Long configId  // 可选，不传则使用默认配置
) {
}
