package com.jinmo.aiwriting.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.aiwriting.ai.prompt.ScoringPrompt;
import com.jinmo.aiwriting.common.exception.BusinessException;
import com.jinmo.aiwriting.domain.dto.ScoringResult;
import com.jinmo.aiwriting.domain.entity.ApiConfig;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI评分服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private final ModelFactory modelFactory;
    private final ObjectMapper objectMapper;

    /**
     * 评分作文
     */
    public ScoringResult scoreEssay(String essayContent, ApiConfig config) {
        try {
            log.info("开始评分作文，字数: {}", essayContent.split("\\s+").length);

            // 创建模型
            ChatLanguageModel model = modelFactory.createModel(config);

            // 构建Prompt
            String prompt = ScoringPrompt.build(essayContent);

            // 调用AI
            long startTime = System.currentTimeMillis();
            String response = model.generate(prompt);
            long duration = System.currentTimeMillis() - startTime;

            log.info("AI评分完成，耗时: {}ms", duration);
            log.debug("AI响应: {}", response);

            // 解析结果
            return parseScoringResult(response);

        } catch (Exception e) {
            log.error("AI评分失败", e);
            throw new BusinessException("AI评分失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析评分结果
     */
    private ScoringResult parseScoringResult(String response) {
        try {
            // 清理可能的Markdown标记
            String json = response
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*$", "")
                .trim();

            return objectMapper.readValue(json, ScoringResult.class);
        } catch (JsonProcessingException e) {
            log.error("解析评分结果失败: {}", response, e);
            throw new BusinessException("解析评分结果失败", e);
        }
    }
}
