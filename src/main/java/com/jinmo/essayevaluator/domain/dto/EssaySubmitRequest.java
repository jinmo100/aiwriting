package com.jinmo.essayevaluator.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 作文提交请求DTO。
 */
public record EssaySubmitRequest(
    @NotBlank(message = "作文内容不能为空")
    @Size(max = 12000, message = "作文内容不能超过12000个字符")
    String content,

    String essayType,

    @Size(max = 4000, message = "题目/任务要求不能超过4000个字符")
    String taskPrompt,

    Long configId,

    @Size(max = 160, message = "idempotencyKey 不能超过160个字符")
    String idempotencyKey
) {
}
