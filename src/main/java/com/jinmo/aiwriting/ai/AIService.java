package com.jinmo.aiwriting.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.aiwriting.ai.provider.AIProviderRequest;
import com.jinmo.aiwriting.ai.provider.AIProviderResult;
import com.jinmo.aiwriting.ai.provider.ProviderAdapterRegistry;
import com.jinmo.aiwriting.ai.provider.ProviderType;
import com.jinmo.aiwriting.ai.prompt.ScoringPrompt;
import com.jinmo.aiwriting.common.exception.BusinessException;
import com.jinmo.aiwriting.domain.dto.ScoringResult;
import com.jinmo.aiwriting.domain.entity.ApiConfig;
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

    private final ProviderAdapterRegistry providerAdapterRegistry;
    private final ObjectMapper objectMapper;
    private final ScoringResultValidator scoringResultValidator;

    /**
     * 评分作文
     */
    public ScoringResult scoreEssay(String essayContent, ApiConfig config) {
        try {
            log.info("开始评分作文，字数: {}", essayContent.split("\\s+").length);

            // 构建Prompt
            String prompt = ScoringPrompt.build(essayContent);

            // 调用AI并解析结果
            long startTime = System.currentTimeMillis();
            AIProviderResult providerResult = callProvider(prompt, config);
            long duration = System.currentTimeMillis() - startTime;
            log.info("AI评分完成，耗时: {}ms", duration);
            return parseValidateOrRepair(prompt, providerResult.text(), config);

        } catch (Exception e) {
            log.error("AI评分失败", e);
            throw new BusinessException("AI评分失败: " + e.getMessage(), e);
        }
    }

    private AIProviderResult callProvider(String prompt, ApiConfig config) {
        ProviderType providerType = config.getProviderType() != null
            ? config.getProviderType()
            : ProviderType.OPENAI_CHAT_COMPLETIONS;
        AIProviderRequest request = new AIProviderRequest(
            null,
            prompt,
            "EssayScoringResult",
            scoringSchema(),
            null
        );
        return providerAdapterRegistry.get(providerType).generate(request, config);
    }

    private ScoringResult parseValidateOrRepair(String originalPrompt, String response, ApiConfig config) {
        try {
            return parseAndValidate(response);
        } catch (BusinessException firstFailure) {
            log.warn("评分结构化输出无效，尝试修复一次: {}", firstFailure.getMessage());
            String repairPrompt = buildRepairPrompt(originalPrompt, response, firstFailure.getMessage());
            AIProviderResult repaired = callProvider(repairPrompt, config);
            return parseAndValidate(repaired.text());
        }
    }

    private ScoringResult parseAndValidate(String response) {
        log.info("原始响应类型: {}", response != null ? response.getClass().getSimpleName() : "null");
        log.info("原始响应长度: {}", response != null ? response.length() : 0);
        log.info("原始响应前200字符: {}", response != null && response.length() > 200 ? response.substring(0, 200) : response);

        ScoringResult result = parseScoringResult(response);
        scoringResultValidator.validate(result);
        return result;
    }

    private String buildRepairPrompt(String originalPrompt, String invalidResponse, String failureReason) {
        return """
            你上一次返回的作文评分结果不是合法的结构化 JSON。

            请修复为合法 JSON，只返回 JSON，不要返回 Markdown，不要解释。

            失败原因：
            %s

            必须符合以下 JSON Schema：
            %s

            原始评分任务：
            %s

            上一次无效输出：
            %s
            """.formatted(failureReason, scoringSchema(), originalPrompt, invalidResponse);
    }

    static String extractResponseText(ObjectMapper objectMapper, String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            if (root.hasNonNull("output_text")) {
                return root.get("output_text").asText();
            }

            JsonNode output = root.path("output");
            if (output.isArray()) {
                StringBuilder text = new StringBuilder();
                for (JsonNode item : output) {
                    JsonNode content = item.path("content");
                    if (content.isArray()) {
                        for (JsonNode contentItem : content) {
                            if (contentItem.hasNonNull("text")) {
                                text.append(contentItem.get("text").asText());
                            }
                        }
                    }
                }
                if (text.length() > 0) {
                    return text.toString();
                }
            }

            return response;
        } catch (JsonProcessingException e) {
            return response;
        }
    }

    private String scoringSchema() {
        return """
            {
              "type": "object",
              "required": ["overallScore", "contentScore", "languageScore", "structureScore", "coherenceScore", "strengths", "suggestions", "errors", "detailedFeedback"],
              "properties": {
                "overallScore": {"type": "number"},
                "contentScore": {"type": "number"},
                "languageScore": {"type": "number"},
                "structureScore": {"type": "number"},
                "coherenceScore": {"type": "number"},
                "strengths": {"type": "array", "items": {"type": "string"}},
                "suggestions": {"type": "array", "items": {"type": "string"}},
                "errors": {"type": "array"},
                "detailedFeedback": {"type": "string"}
              }
            }
            """;
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
