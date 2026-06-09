package com.jinmo.essayevaluator.embedding;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户 Embedding 配置实体。
 *
 * <p>API Key 只允许写入 {@code apiKeyEncrypted}；响应 DTO 只暴露 hasApiKey/preview，不返回明文或密文。</p>
 */
@Data
@TableName("embedding_configs")
public class EmbeddingConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ownerUserId;

    private String configName;

    private EmbeddingProviderType providerType;

    private String providerLabel;

    private String baseUrl;

    private String apiKeyEncrypted;

    private String modelName;

    private Integer dimensions;

    private Integer timeoutSeconds;

    private Double inputTokenPricePerMillion;

    private String currency;

    private Boolean isDefault;

    private String lastTestStatus;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastTestErrorCode;

    private String lastTestMessage;

    private Integer lastTestLatencyMs;

    private LocalDateTime lastTestedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
