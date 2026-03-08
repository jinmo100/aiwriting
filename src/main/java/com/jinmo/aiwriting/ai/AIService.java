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
            log.info("原始响应类型: {}", response != null ? response.getClass().getSimpleName() : "null");
            log.info("原始响应长度: {}", response != null ? response.length() : 0);
            log.info("原始响应前200字符: {}", response != null && response.length() > 200 ? response.substring(0, 200) : response);

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
            log.debug("原始响应内容: {}", response);

            // 清理可能的Markdown标记
            String json = response
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*$", "")
                .trim();

            // 处理SSE流式响应格式（如果API返回了 data: 前缀）
            if (json.startsWith("data:")) {
                log.warn("检测到SSE流式响应格式，尝试提取JSON内容");
                // 提取最后一个data块的内容
                String[] lines = json.split("\n");
                StringBuilder jsonContent = new StringBuilder();
                for (String line : lines) {
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        if (!data.equals("[DONE]")) {
                            try {
                                // 尝试解析SSE格式
                                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(data);
                                if (node.has("choices") && node.get("choices").isArray()) {
                                    com.fasterxml.jackson.databind.JsonNode choices = node.get("choices");
                                    if (choices.size() > 0) {
                                        com.fasterxml.jackson.databind.JsonNode choice = choices.get(0);
                                        if (choice.has("delta") && choice.get("delta").has("content")) {
                                            jsonContent.append(choice.get("delta").get("content").asText());
                                        } else if (choice.has("message") && choice.get("message").has("content")) {
                                            jsonContent.append(choice.get("message").get("content").asText());
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.debug("无法解析SSE行: {}", data);
                            }
                        }
                    }
                }
                if (jsonContent.length() > 0) {
                    json = jsonContent.toString();
                    log.info("从SSE响应中提取到JSON内容，长度: {}", json.length());
                }
            }

            log.debug("最终解析JSON: {}", json);
            return objectMapper.readValue(json, ScoringResult.class);
        } catch (JsonProcessingException e) {
            log.error("解析评分结果失败，原始响应: {}", response, e);
            throw new BusinessException("解析评分结果失败: " + e.getMessage(), e);
        }
    }
}
