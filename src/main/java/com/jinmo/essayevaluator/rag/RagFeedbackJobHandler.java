package com.jinmo.essayevaluator.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.essayevaluator.ai.provider.AIProviderAdapter;
import com.jinmo.essayevaluator.ai.provider.AIProviderRequest;
import com.jinmo.essayevaluator.ai.provider.ProviderAdapterRegistry;
import com.jinmo.essayevaluator.common.exception.BusinessException;
import com.jinmo.essayevaluator.domain.dto.RubricScoringResult;
import com.jinmo.essayevaluator.domain.entity.ApiConfig;
import com.jinmo.essayevaluator.domain.entity.Essay;
import com.jinmo.essayevaluator.domain.entity.EssayScore;
import com.jinmo.essayevaluator.embedding.EmbeddingConfig;
import com.jinmo.essayevaluator.embedding.EmbeddingConfigService;
import com.jinmo.essayevaluator.job.BackgroundJob;
import com.jinmo.essayevaluator.job.BackgroundJobHandler;
import com.jinmo.essayevaluator.job.BackgroundJobType;
import com.jinmo.essayevaluator.mapper.EssayMapper;
import com.jinmo.essayevaluator.mapper.EssayScoreMapper;
import com.jinmo.essayevaluator.mapper.RagFeedbackCitationMapper;
import com.jinmo.essayevaluator.mapper.RagFeedbackMapper;
import com.jinmo.essayevaluator.service.ApiConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RAG Feedback 后台任务处理器。
 */
@Component
@RequiredArgsConstructor
public class RagFeedbackJobHandler implements BackgroundJobHandler {

    private final EssayMapper essayMapper;
    private final EssayScoreMapper essayScoreMapper;
    private final EmbeddingConfigService embeddingConfigService;
    private final ApiConfigService apiConfigService;
    private final ProviderAdapterRegistry providerAdapterRegistry;
    private final RagQueryBuilder ragQueryBuilder;
    private final RagRetrievalService ragRetrievalService;
    private final RagFeedbackPrompt ragFeedbackPrompt;
    private final RagFeedbackValidator ragFeedbackValidator;
    private final RagFeedbackMapper ragFeedbackMapper;
    private final RagFeedbackCitationMapper ragFeedbackCitationMapper;
    private final ObjectMapper objectMapper;

    @Override
    public BackgroundJobType jobType() {
        return BackgroundJobType.RAG_FEEDBACK;
    }

    @Override
    public JobResult handle(BackgroundJob job) throws Exception {
        RagFeedbackPayload payload = objectMapper.readValue(job.getPayloadJson(), RagFeedbackPayload.class);
        Essay essay = loadEssay(job.getOwnerUserId(), payload.essayId());
        EssayScore score = loadLatestScore(essay.getId());
        if (score == null || !"COMPLETED".equals(score.getScoringStatus()) || score.getResultJson() == null) {
            return JobResult.skipped(result("需等待评分完成", "WAIT_SCORING_COMPLETED"));
        }

        EmbeddingConfig embeddingConfig;
        try {
            embeddingConfig = payload.embeddingConfigId() == null
                ? embeddingConfigService.getDefaultConfigForUser(job.getOwnerUserId())
                : embeddingConfigService.loadOwnedConfigForUser(job.getOwnerUserId(), payload.embeddingConfigId());
        } catch (BusinessException error) {
            return JobResult.skipped(result("请先配置 Embedding", "OPEN_EMBEDDING_CONFIG"));
        }
        if (embeddingConfig == null) {
            return JobResult.skipped(result("请先配置 Embedding", "OPEN_EMBEDDING_CONFIG"));
        }

        RubricScoringResult scoringResult = objectMapper.readValue(score.getResultJson(), RubricScoringResult.class);
        String query = ragQueryBuilder.build(scoringResult, essay.getEssayType(), essay.getTaskPrompt());
        List<RagRetrievedChunk> citations = ragRetrievalService.retrieve(
            job.getOwnerUserId(),
            embeddingConfig,
            query,
            essay.getEssayType(),
            5
        );
        if (citations.isEmpty()) {
            return JobResult.skipped(result("请先构建知识索引", "BUILD_RAG_INDEX"));
        }

        ApiConfig chatConfig = apiConfigService.loadUsableConfigForUser(job.getOwnerUserId(), score.getApiConfigId());
        RagFeedbackPrompt.PromptBundle prompt = ragFeedbackPrompt.build(
            essay.getEssayType(),
            essay.getTaskPrompt(),
            essay.getContent(),
            score.getResultJson(),
            citations
        );
        RagFeedbackValidator.ValidatedFeedback validated = generateValidatedFeedback(chatConfig, prompt, citations);
        String feedbackJson = objectMapper.writeValueAsString(validated);
        RagFeedback feedback = saveFeedback(job, essay, score, embeddingConfig, query, citations, feedbackJson);
        saveCitations(feedback, citations);

        Map<String, Object> completed = result("RAG Feedback 已生成", "VIEW_RAG_FEEDBACK");
        completed.put("feedbackId", feedback.getId());
        completed.put("citationCount", citations.size());
        return JobResult.completed(completed);
    }

    private RagFeedbackValidator.ValidatedFeedback generateValidatedFeedback(
        ApiConfig chatConfig,
        RagFeedbackPrompt.PromptBundle prompt,
        List<RagRetrievedChunk> citations
    ) {
        AIProviderAdapter adapter = providerAdapterRegistry.get(chatConfig.getProviderType());
        BusinessException firstValidationError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            String userPrompt = attempt == 1
                ? prompt.userPrompt()
                : repairPrompt(prompt.userPrompt(), firstValidationError, citations);
            String text = adapter.generate(
                new AIProviderRequest(prompt.systemPrompt(), userPrompt, prompt.schemaName(), prompt.schemaJson(), null),
                chatConfig
            ).text();
            try {
                RagFeedbackValidator.ValidatedFeedback validated = ragFeedbackValidator.validate(text);
                validateCitationIds(validated, citations);
                return validated;
            } catch (BusinessException validationError) {
                if (attempt == 2) {
                    throw validationError;
                }
                firstValidationError = validationError;
            }
        }
        throw new BusinessException("RAG Feedback JSON 校验失败");
    }

    private Essay loadEssay(Long userId, Long essayId) {
        Essay essay = essayMapper.selectOne(
            new LambdaQueryWrapper<Essay>()
                .eq(Essay::getId, essayId)
                .eq(Essay::getUserId, userId)
                .last("LIMIT 1")
        );
        if (essay == null) {
            throw new BusinessException("作文不存在");
        }
        return essay;
    }

    private EssayScore loadLatestScore(Long essayId) {
        return essayScoreMapper.selectOne(
            new LambdaQueryWrapper<EssayScore>()
                .eq(EssayScore::getEssayId, essayId)
                .orderByDesc(EssayScore::getCreatedAt)
                .last("LIMIT 1")
        );
    }

    private RagFeedback saveFeedback(
        BackgroundJob job,
        Essay essay,
        EssayScore score,
        EmbeddingConfig embeddingConfig,
        String query,
        List<RagRetrievedChunk> citations,
        String feedbackJson
    ) throws Exception {
        ragFeedbackMapper.deleteByScoreAndConfig(job.getOwnerUserId(), score.getId(), embeddingConfig.getId());
        RagFeedback feedback = new RagFeedback();
        feedback.setUserId(job.getOwnerUserId());
        feedback.setEssayId(essay.getId());
        feedback.setScoreId(score.getId());
        feedback.setApiConfigId(score.getApiConfigId());
        feedback.setEmbeddingConfigId(embeddingConfig.getId());
        feedback.setJobId(job.getId());
        feedback.setQueryText(query);
        feedback.setRetrievedChunkIds(objectMapper.writeValueAsString(citations.stream().map(RagRetrievedChunk::getChunkId).toList()));
        feedback.setFeedbackJson(feedbackJson);
        ragFeedbackMapper.insert(feedback);
        return feedback;
    }

    private void saveCitations(RagFeedback feedback, List<RagRetrievedChunk> chunks) {
        for (RagRetrievedChunk chunk : chunks) {
            RagFeedbackCitation citation = new RagFeedbackCitation();
            citation.setFeedbackId(feedback.getId());
            citation.setChunkId(chunk.getChunkId());
            citation.setSourceTitle(chunk.getSourceTitle());
            citation.setSourceType(chunk.getSourceType());
            citation.setSnippet(chunk.getSnippet());
            citation.setRelevanceScore(chunk.getDistance() == null ? null : 1.0d - chunk.getDistance());
            citation.setRankNo(chunk.getRankNo());
            citation.setReason("用于支撑知识点增强反馈");
            ragFeedbackCitationMapper.insert(citation);
        }
    }

    private void validateCitationIds(RagFeedbackValidator.ValidatedFeedback feedback, List<RagRetrievedChunk> citations) {
        Set<Long> allowedCitationIds = citations.stream()
            .map(RagRetrievedChunk::getRankNo)
            .filter(rankNo -> rankNo != null)
            .map(Integer::longValue)
            .collect(Collectors.toSet());
        boolean hasInvalidCitation = feedback.items().stream()
            .flatMap(item -> item.citationIds().stream())
            .anyMatch(citationId -> !allowedCitationIds.contains(citationId));
        if (hasInvalidCitation) {
            throw new BusinessException("RAG Feedback 引用了不存在的 citation");
        }
    }

    private String repairPrompt(
        String originalPrompt,
        BusinessException validationError,
        List<RagRetrievedChunk> citations
    ) {
        String allowedIds = citations.stream()
            .map(RagRetrievedChunk::getRankNo)
            .filter(rankNo -> rankNo != null)
            .map(String::valueOf)
            .collect(Collectors.joining(", "));
        String reason = validationError == null ? "未知校验失败" : validationError.getMessage();
        return originalPrompt + """

            [系统校验反馈]
            上一次输出未通过后端校验：%s
            请基于同一批不可信上下文重新生成严格 JSON。必须满足：
            1. overall 为非空字符串；
            2. items 为 1 到 5 条，每条必须有 title/problem/whyItMatters/howToImprove/citationIds；
            3. citationIds 只能使用这些编号：%s；
            4. nextPractice 必须是 1 到 3 条非空字符串；
            5. 只输出 JSON 对象，不输出 Markdown 或解释文字。
            """.formatted(reason, allowedIds);
    }

    private Map<String, Object> result(String message, String action) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", message);
        result.put("action", action);
        return result;
    }

    private record RagFeedbackPayload(Long essayId, Long embeddingConfigId) {
    }
}
