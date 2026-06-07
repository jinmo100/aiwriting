package com.jinmo.aiwriting.domain.enums;

import com.jinmo.aiwriting.common.exception.BusinessException;

import java.util.Arrays;

/**
 * Stable essay type codes exposed by API and stored with each submission.
 */
public enum EssayType {
    GENERAL("通用英语作文", false, true, 80, 800, 50, 1500, 12000),
    JUNIOR_GENERAL("初中英语作文", true, true, 50, 120, 40, 250, 3000),
    JUNIOR_ZHONGKAO("中考英语作文", true, true, 80, 120, 60, 250, 3000),
    SENIOR_GENERAL("高中英语作文", true, true, 100, 250, 80, 500, 5000),
    SENIOR_GAOKAO("高考英语作文", true, true, 80, 150, 60, 300, 5000),
    CET4("大学英语四级作文", true, true, 120, 180, 100, 400, 6000),
    CET6("大学英语六级作文", true, true, 150, 220, 120, 500, 6000),
    IELTS_TASK_1("雅思 Task 1 图表作文", true, true, 150, 220, 130, 500, 8000),
    IELTS_TASK_2("雅思 Task 2 议论文", true, true, 250, 350, 220, 700, 8000),
    TOEFL_INDEPENDENT("托福独立写作", true, true, 300, 450, 250, 800, 8000),
    TOEFL_INTEGRATED("托福综合写作（暂缓开放）", true, false, 0, 0, 0, 0, 0);

    private final String displayName;
    private final boolean taskPromptRequired;
    private final boolean enabledInUi;
    private final int recommendedMinWords;
    private final int recommendedMaxWords;
    private final int rejectBelowWords;
    private final int hardMaxWords;
    private final int hardMaxChars;

    EssayType(
        String displayName,
        boolean taskPromptRequired,
        boolean enabledInUi,
        int recommendedMinWords,
        int recommendedMaxWords,
        int rejectBelowWords,
        int hardMaxWords,
        int hardMaxChars
    ) {
        this.displayName = displayName;
        this.taskPromptRequired = taskPromptRequired;
        this.enabledInUi = enabledInUi;
        this.recommendedMinWords = recommendedMinWords;
        this.recommendedMaxWords = recommendedMaxWords;
        this.rejectBelowWords = rejectBelowWords;
        this.hardMaxWords = hardMaxWords;
        this.hardMaxChars = hardMaxChars;
    }

    public static EssayType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return GENERAL;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
            .filter(type -> type.name().equals(normalized))
            .findFirst()
            .orElseThrow(() -> new BusinessException("不支持的作文类型: " + code));
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTaskPromptRequired() {
        return taskPromptRequired;
    }

    public boolean isEnabledInUi() {
        return enabledInUi;
    }

    public int getRecommendedMinWords() {
        return recommendedMinWords;
    }

    public int getRecommendedMaxWords() {
        return recommendedMaxWords;
    }

    public int getRejectBelowWords() {
        return rejectBelowWords;
    }

    public int getHardMaxWords() {
        return hardMaxWords;
    }

    public int getHardMaxChars() {
        return hardMaxChars;
    }
}
