package com.jinmo.essayevaluator.job;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 后台任务类型。
 *
 * <p>V1 只承载 RAG 索引和 RAG Feedback 生成，避免把轻量框架扩展成通用任务平台。</p>
 */
public enum BackgroundJobType {
    RAG_INDEX("RAG_INDEX"),
    RAG_FEEDBACK("RAG_FEEDBACK");

    @EnumValue
    @JsonValue
    private final String value;

    BackgroundJobType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
