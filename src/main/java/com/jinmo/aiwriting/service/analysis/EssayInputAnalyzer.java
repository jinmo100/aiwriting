package com.jinmo.aiwriting.service.analysis;

import com.jinmo.aiwriting.domain.dto.RubricScoringResult;
import com.jinmo.aiwriting.domain.enums.EssayType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class EssayInputAnalyzer {

    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B-\\u200D\\uFEFF]");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");
    private static final Pattern REPEATED_CHAR = Pattern.compile("(?s)(.)\\1{15,}");

    private final SafetyAnalyzer safetyAnalyzer;

    public InputInspection analyze(EssayType essayType, String taskPrompt, String content, int wordCount, int charCount) {
        List<String> warnings = new ArrayList<>();
        List<String> rejections = new ArrayList<>();

        analyzeTypeAndLength(essayType, taskPrompt, wordCount, charCount, warnings, rejections);
        analyzeCharacters(content, warnings, rejections);
        analyzeLanguageMix(content, warnings, rejections);

        SafetyAnalysis safetyAnalysis = safetyAnalyzer.analyze(taskPrompt, content);
        if (safetyAnalysis.rejected()) {
            safetyAnalysis.issues().stream()
                .filter(issue -> "REJECT".equals(issue.action()))
                .map(SafetyIssue::message)
                .forEach(rejections::add);
        } else if (safetyAnalysis.warned()) {
            safetyAnalysis.confidenceWarnings().forEach(warnings::add);
        }

        AnalysisStatus status = !rejections.isEmpty()
            ? AnalysisStatus.REJECT
            : warnings.isEmpty() ? AnalysisStatus.PASS : AnalysisStatus.WARN;

        return new InputInspection(
            new RubricScoringResult.InputAnalysis(
                status.name(),
                wordCount,
                charCount,
                warnings,
                rejections
            ),
            safetyAnalysis
        );
    }

    private void analyzeTypeAndLength(
        EssayType essayType,
        String taskPrompt,
        int wordCount,
        int charCount,
        List<String> warnings,
        List<String> rejections
    ) {
        if (!essayType.isEnabledInUi()) {
            rejections.add("该作文类型暂缓开放");
        }
        if (essayType.isTaskPromptRequired() && (taskPrompt == null || taskPrompt.isBlank())) {
            rejections.add("该作文类型需要填写题目/任务要求");
        }
        if (charCount > essayType.getHardMaxChars()) {
            rejections.add("作文内容超过该类型字符上限 " + essayType.getHardMaxChars());
        }
        if (essayType.getHardMaxWords() > 0 && wordCount > essayType.getHardMaxWords()) {
            rejections.add("作文英文词数超过该类型硬上限 " + essayType.getHardMaxWords());
        }
        int obviousTooShort = obviousTooShortThreshold(essayType);
        if (wordCount < obviousTooShort) {
            rejections.add("作文英文词数过少，无法进行有效评分");
        } else if (wordCount < essayType.getRecommendedMinWords()) {
            warnings.add("英文词数低于建议范围 " + essayType.getRecommendedMinWords() + "-" + essayType.getRecommendedMaxWords());
        } else if (wordCount > essayType.getRecommendedMaxWords()) {
            warnings.add("英文词数高于建议范围 " + essayType.getRecommendedMinWords() + "-" + essayType.getRecommendedMaxWords());
        }
    }

    private void analyzeCharacters(String content, List<String> warnings, List<String> rejections) {
        if (content == null) {
            rejections.add("作文内容不能为空");
            return;
        }
        if (ZERO_WIDTH.matcher(content).find()) {
            rejections.add("作文正文包含零宽字符，请清理后重新提交");
        }
        if (CONTROL.matcher(content).find()) {
            rejections.add("作文正文包含不可见控制字符，请清理后重新提交");
        }
        if (REPEATED_CHAR.matcher(content).find()) {
            rejections.add("作文正文包含异常重复字符，请清理后重新提交");
        }

        int emojiCount = countEmoji(content);
        if (emojiCount > 10) {
            rejections.add("作文正文包含过多 emoji 或特殊表情");
        } else if (emojiCount > 0) {
            warnings.add("作文正文包含 emoji 或特殊表情，可能影响评分置信度");
        }

        double specialRatio = specialSymbolRatio(content);
        if (specialRatio > 0.35) {
            rejections.add("作文正文特殊符号比例过高");
        } else if (specialRatio > 0.18) {
            warnings.add("作文正文特殊符号比例偏高");
        }
    }

    private void analyzeLanguageMix(String content, List<String> warnings, List<String> rejections) {
        int latinLetters = 0;
        int cjkLetters = 0;
        int otherLetters = 0;
        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            if (isLatinLetter(codePoint)) {
                latinLetters++;
            } else if (isCjk(codePoint)) {
                cjkLetters++;
            } else if (Character.isLetter(codePoint)) {
                otherLetters++;
            }
            i += Character.charCount(codePoint);
        }

        int languageLetters = latinLetters + cjkLetters + otherLetters;
        if (languageLetters == 0 || latinLetters == 0) {
            rejections.add("作文正文必须主要由英文构成");
            return;
        }

        double cjkRatio = cjkLetters / (double) languageLetters;
        double otherRatio = otherLetters / (double) languageLetters;
        double nonEnglishRatio = (cjkLetters + otherLetters) / (double) languageLetters;

        if (cjkRatio > 0.35 || nonEnglishRatio > 0.45) {
            rejections.add("作文正文非英文内容比例过高");
        } else if (cjkRatio > 0.15 || otherRatio > 0.20 || nonEnglishRatio > 0.25) {
            warnings.add("作文正文包含较多非英文内容，评分置信度会降低");
        }
    }

    private int obviousTooShortThreshold(EssayType essayType) {
        return switch (essayType) {
            case GENERAL -> 50;
            case JUNIOR_GENERAL -> 40;
            case JUNIOR_ZHONGKAO -> 20;
            case SENIOR_GENERAL, SENIOR_GAOKAO -> 30;
            case CET4, CET6 -> 40;
            case IELTS_TASK_1, IELTS_TASK_2 -> 60;
            case TOEFL_INDEPENDENT -> 80;
            case TOEFL_INTEGRATED -> Integer.MAX_VALUE;
        };
    }

    private int countEmoji(String content) {
        int count = 0;
        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            if (isEmoji(codePoint)) {
                count++;
            }
            i += Character.charCount(codePoint);
        }
        return count;
    }

    private double specialSymbolRatio(String content) {
        int visible = 0;
        int special = 0;
        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            i += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                continue;
            }
            visible++;
            if (!Character.isLetterOrDigit(codePoint) && !isCommonWritingPunctuation(codePoint)) {
                special++;
            }
        }
        return visible == 0 ? 0 : special / (double) visible;
    }

    private boolean isLatinLetter(int codePoint) {
        return (codePoint >= 'A' && codePoint <= 'Z') || (codePoint >= 'a' && codePoint <= 'z');
    }

    private boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
            || script == Character.UnicodeScript.HIRAGANA
            || script == Character.UnicodeScript.KATAKANA
            || script == Character.UnicodeScript.HANGUL;
    }

    private boolean isEmoji(int codePoint) {
        return (codePoint >= 0x1F300 && codePoint <= 0x1FAFF)
            || (codePoint >= 0x2600 && codePoint <= 0x27BF);
    }

    private boolean isCommonWritingPunctuation(int codePoint) {
        return ".,;:!?'-\"()[]{}%/\\\n\r\t“.，”‘’：；！？（）—–".indexOf(codePoint) >= 0;
    }
}
