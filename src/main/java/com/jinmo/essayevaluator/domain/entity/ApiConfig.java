package com.jinmo.essayevaluator.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.jinmo.essayevaluator.ai.provider.ProviderType;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * API配置实体
 * 用于存储用户的AI模型API配置
 */
@Data
@TableName("api_configs")
public class ApiConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configName;

    /**
     * 旧字段：历史 provider/品牌值。新逻辑使用 providerType。
     */
    private String provider;

    /**
     * 协议适配器类型，不是品牌。
     */
    private ProviderType providerType;

    /**
     * 前端展示标签，例如 OpenAI / OpenRouter / Gemini / Anthropic。
     */
    private String providerLabel;

    private String baseUrl;

    /**
     * 旧明文字段。新建/更新不再写入，读取时仅兼容旧数据。
     */
    private String apiKey;

    private String apiKeyEncrypted;

    private String modelName;

    private Double temperature;

    private Integer maxTokens;

    private Integer timeoutSeconds;

    private String modelParametersJson;

    private Boolean isDefault;

    private String lastTestStatus;

    private String lastTestErrorCode;

    private String lastTestMessage;

    private Integer lastTestLatencyMs;

    private LocalDateTime lastTestedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
