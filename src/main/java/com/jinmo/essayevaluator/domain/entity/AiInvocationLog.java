package com.jinmo.essayevaluator.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 每一次 AI Provider 调用明细。只保存安全化观测字段，不保存原始 prompt/response/API Key。
 */
@Data
@TableName("ai_invocation_logs")
public class AiInvocationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long essayId;

    private Long scoreId;

    private Long apiConfigId;

    private Integer attemptNo;

    private String purpose;

    private String provider;

    private String endpointType;

    private String model;

    private String providerRequestId;

    private String status;

    private Integer latencyMs;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer totalTokens;

    private String usageSource;

    private BigDecimal estimatedCost;

    private String currency;

    private String failureCode;

    private String failureMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
