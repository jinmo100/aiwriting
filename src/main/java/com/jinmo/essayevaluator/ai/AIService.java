package com.jinmo.essayevaluator.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.essayevaluator.ai.prompt.ScoringPrompt;
import com.jinmo.essayevaluator.ai.provider.AIProviderRequest;
import com.jinmo.essayevaluator.ai.provider.AIProviderResult;
import com.jinmo.essayevaluator.ai.provider.ProviderAdapterRegistry;
import com.jinmo.essayevaluator.ai.provider.ProviderType;
import com.jinmo.essayevaluator.common.exception.BusinessException;
import com.jinmo.essayevaluator.domain.dto.RubricDefinition;
import com.jinmo.essayevaluator.domain.dto.RubricScoringResult;
import com.jinmo.essayevaluator.domain.entity.ApiConfig;
import com.jinmo.essayevaluator.domain.enums.EssayType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI评分服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private final ProviderAdapterRegistry providerAdapterRegistry;
    private final ObjectMapper objectMapper;
    private final ScoringResultValidator scoringResultValidator;

    public ScoringOutcome scoreEssay(
        EssayType essayType,
        String taskPrompt,
        String essayContent,
        RubricDefinition rubric,
        ApiConfig config
    ) {
        try {
            log.info("开始评分作文，类型: {}, 英文词数估算: {}", essayType, essayContent.split("\\s+").length);

            String systemPrompt = ScoringPrompt.systemPrompt();
            String userPrompt = ScoringPrompt.buildUserPrompt(essayType, taskPrompt, essayContent, rubric);
            List<ProviderInvocation> invocations = new ArrayList<>();

            long startTime = System.currentTimeMillis();
            AIProviderResult providerResult = callProvider(systemPrompt, userPrompt, config, "SCORING", invocations);
            long duration = System.currentTimeMillis() - startTime;
            log.info("AI评分完成，耗时: {}ms", duration);

            ParsedScoring parsed = parseValidateOrRepair(systemPrompt, userPrompt, providerResult, config, rubric, invocations);
            AIProviderResult finalProviderResult = parsed.providerResult();
            return new ScoringOutcome(
                parsed.result(),
                finalProviderResult.modelName() != null ? finalProviderResult.modelName() : config.getModelName(),
                sumInputTokens(invocations),
                sumOutputTokens(invocations),
                sumTotalTokens(invocations),
                sumLatency(invocations) != null ? sumLatency(invocations) : duration,
                invocations
            );
        } catch (Exception e) {
            log.error("AI评分失败", e);
            throw new BusinessException("AI评分失败: " + e.getMessage(), e);
        }
    }

    private AIProviderResult callProvider(
        String systemPrompt,
        String userPrompt,
        ApiConfig config,
        String purpose,
        List<ProviderInvocation> invocations
    ) {
        ProviderType providerType = config.getProviderType() != null
            ? config.getProviderType()
            : ProviderType.OPENAI_CHAT_COMPLETIONS;
        AIProviderRequest request = new AIProviderRequest(
            systemPrompt,
            userPrompt,
            "RubricScoringResult",
            ScoringPrompt.scoringSchema(),
            null
        );
        AIProviderResult result = providerAdapterRegistry.get(providerType).generate(request, config);
        invocations.add(toInvocation(purpose, providerType, result, systemPrompt, userPrompt));
        return result;
    }

    private ParsedScoring parseValidateOrRepair(
        String systemPrompt,
        String originalPrompt,
        AIProviderResult providerResult,
        ApiConfig config,
        RubricDefinition rubric,
        List<ProviderInvocation> invocations
    ) {
        try {
            return new ParsedScoring(parseAndValidate(providerResult.text(), rubric), providerResult);
        } catch (BusinessException firstFailure) {
            log.warn("评分结构化输出无效，尝试修复一次: {}", firstFailure.getMessage());
            String repairPrompt = buildRepairPrompt(originalPrompt, providerResult.text(), firstFailure.getMessage());
            AIProviderResult repaired = callProvider(systemPrompt, repairPrompt, config, "JSON_REPAIR", invocations);
            return new ParsedScoring(parseAndValidate(repaired.text(), rubric), repaired);
        }
    }

    private RubricScoringResult parseAndValidate(String response, RubricDefinition rubric) {
        log.info("原始响应长度: {}", response != null ? response.length() : 0);
        log.info("原始响应前200字符: {}", response != null && response.length() > 200 ? response.substring(0, 200) : response);

        RubricScoringResult result = parseScoringResult(response);
        return scoringResultValidator.validateAndNormalize(result, rubric);
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
            """.formatted(failureReason, ScoringPrompt.scoringSchema(), originalPrompt, invalidResponse);
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

    private RubricScoringResult parseScoringResult(String response) {
        try {
            log.debug("原始响应内容: {}", response);
            String json = extractResponseText(objectMapper, response == null ? "" : response)
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*$", "")
                .trim();

            if (json.startsWith("data:")) {
                log.warn("检测到SSE流式响应格式，尝试提取JSON内容");
                String[] lines = json.split("\n");
                StringBuilder jsonContent = new StringBuilder();
                for (String line : lines) {
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        if (!data.equals("[DONE]")) {
                            try {
                                JsonNode node = objectMapper.readTree(data);
                                if (node.has("choices") && node.get("choices").isArray()) {
                                    JsonNode choices = node.get("choices");
                                    if (!choices.isEmpty()) {
                                        JsonNode choice = choices.get(0);
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
                if (!jsonContent.isEmpty()) {
                    json = jsonContent.toString();
                    log.info("从SSE响应中提取到JSON内容，长度: {}", json.length());
                }
            }

            log.debug("最终解析JSON: {}", json);
            return objectMapper.readValue(json, RubricScoringResult.class);
        } catch (JsonProcessingException e) {
            log.error("解析评分结果失败，原始响应: {}", response, e);
            throw new BusinessException("解析评分结果失败: " + e.getMessage(), e);
        }
    }

    public record ScoringOutcome(
        RubricScoringResult result,
        String modelName,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Long latencyMillis,
        List<ProviderInvocation> invocations
    ) {
    }

    public record ProviderInvocation(
        String purpose,
        ProviderType providerType,
        String modelName,
        String providerRequestId,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Long latencyMillis,
        String usageSource,
        String status,
        String failureCode,
        String failureMessage
    ) {
    }

    private record ParsedScoring(RubricScoringResult result, AIProviderResult providerResult) {
    }

    private static ProviderInvocation toInvocation(
        String purpose,
        ProviderType providerType,
        AIProviderResult result,
        String systemPrompt,
        String userPrompt
    ) {
        Integer inputTokens = result.inputTokens();
        Integer outputTokens = result.outputTokens();
        Integer totalTokens = result.totalTokens();
        String usageSource = "PROVIDER";
        if (inputTokens == null && outputTokens == null && totalTokens == null) {
            usageSource = "LOCAL_ESTIMATE";
            inputTokens = estimateTokens((systemPrompt == null ? "" : systemPrompt) + "\n" + (userPrompt == null ? "" : userPrompt));
            outputTokens = estimateTokens(result.text());
            totalTokens = inputTokens + outputTokens;
        } else if (totalTokens == null && inputTokens != null && outputTokens != null) {
            totalTokens = inputTokens + outputTokens;
        }
        return new ProviderInvocation(
            purpose,
            result.providerType() != null ? result.providerType() : providerType,
            result.modelName(),
            result.providerRequestId(),
            inputTokens,
            outputTokens,
            totalTokens,
            result.latencyMillis(),
            usageSource,
            "SUCCESS",
            null,
            null
        );
    }

    static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int codePoints = text.codePointCount(0, text.length());
        return Math.max(1, (int) Math.ceil(codePoints / 4.0));
    }

    private static Integer sumInputTokens(List<ProviderInvocation> invocations) {
        return sumInteger(invocations.stream().map(ProviderInvocation::inputTokens).toList());
    }

    private static Integer sumOutputTokens(List<ProviderInvocation> invocations) {
        return sumInteger(invocations.stream().map(ProviderInvocation::outputTokens).toList());
    }

    private static Integer sumTotalTokens(List<ProviderInvocation> invocations) {
        return sumInteger(invocations.stream().map(ProviderInvocation::totalTokens).toList());
    }

    private static Long sumLatency(List<ProviderInvocation> invocations) {
        long total = 0;
        boolean hasAny = false;
        for (ProviderInvocation invocation : invocations) {
            if (invocation.latencyMillis() != null) {
                total += invocation.latencyMillis();
                hasAny = true;
            }
        }
        return hasAny ? total : null;
    }

    private static Integer sumInteger(List<Integer> values) {
        int total = 0;
        boolean hasAny = false;
        for (Integer value : values) {
            if (value != null) {
                total += value;
                hasAny = true;
            }
        }
        return hasAny ? total : null;
    }
}
