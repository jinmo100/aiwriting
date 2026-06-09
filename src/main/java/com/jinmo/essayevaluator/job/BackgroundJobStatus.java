package com.jinmo.essayevaluator.job;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 后台任务状态。
 *
 * <p>PENDING/RUNNING 视为活跃任务，会参与数据库唯一索引防重复；其余状态均为终态。</p>
 */
public enum BackgroundJobStatus {
    PENDING("PENDING"),
    RUNNING("RUNNING"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED"),
    SKIPPED("SKIPPED");

    @EnumValue
    @JsonValue
    private final String value;

    BackgroundJobStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
