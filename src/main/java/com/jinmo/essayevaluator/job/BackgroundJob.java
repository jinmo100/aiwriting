package com.jinmo.essayevaluator.job;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 轻量通用后台任务实体。
 *
 * <p>payloadJson/resultJson 只保存 handler 所需的安全输入和安全结果摘要，业务层不得写入明文
 * API Key、系统 prompt 或其他可还原密钥的内容。</p>
 */
@Data
@TableName("background_jobs")
public class BackgroundJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    private BackgroundJobType jobType;

    private Long ownerUserId;

    private Long requestedByUserId;

    private BackgroundJobStatus status;

    private String businessKey;

    private String payloadJson;

    private String resultJson;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorCode;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorMessage;

    private Integer attemptCount;

    private Integer maxAttempts;

    private LocalDateTime runAfter;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lockedBy;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime lockedUntil;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
