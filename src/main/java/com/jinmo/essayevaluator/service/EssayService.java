package com.jinmo.essayevaluator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.essayevaluator.ai.AIService;
import com.jinmo.essayevaluator.ai.provider.AIProviderErrorCode;
import com.jinmo.essayevaluator.ai.provider.AIProviderException;
import com.jinmo.essayevaluator.common.exception.BusinessException;
import com.jinmo.essayevaluator.domain.dto.AiUsageSummary;
import com.jinmo.essayevaluator.domain.dto.EssayHistoryItem;
import com.jinmo.essayevaluator.domain.dto.EssayResponse;
import com.jinmo.essayevaluator.domain.dto.EssayScoreResponse;
import com.jinmo.essayevaluator.domain.dto.EssaySubmitRequest;
import com.jinmo.essayevaluator.domain.dto.RubricDefinition;
import com.jinmo.essayevaluator.domain.dto.RubricScoringResult;
import com.jinmo.essayevaluator.domain.entity.ApiConfig;
import com.jinmo.essayevaluator.domain.entity.Essay;
import com.jinmo.essayevaluator.domain.entity.EssayScore;
import com.jinmo.essayevaluator.domain.enums.EssayType;
import com.jinmo.essayevaluator.mapper.ApiConfigMapper;
import com.jinmo.essayevaluator.mapper.EssayMapper;
import com.jinmo.essayevaluator.mapper.EssayScoreMapper;
import com.jinmo.essayevaluator.service.analysis.AnalysisStatus;
import com.jinmo.essayevaluator.service.analysis.EssayInputAnalyzer;
import com.jinmo.essayevaluator.service.analysis.InputInspection;
import com.jinmo.essayevaluator.service.idempotency.ScoringIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 作文服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EssayService {
    private static final Pattern ENGLISH_WORD_PATTERN = Pattern.compile("[A-Za-z]+(?:[-'][A-Za-z]+)?");
    private static final int RECENT_CONTENT_DUPLICATE_MINUTES = 10;

    private final EssayMapper essayMapper;
    private final EssayScoreMapper essayScoreMapper;
    private final ApiConfigMapper apiConfigMapper;
    private final RubricService rubricService;
    private final CurrentUserService currentUserService;
    private final AiInvocationLogService aiInvocationLogService;
    private final EssayInputAnalyzer essayInputAnalyzer;
    private final ScoringIdempotencyService idempotencyService;
    private final AIService aiService;
    private final ObjectMapper objectMapper;
    @Qualifier("scoringTaskExecutor")
    private final TaskExecutor scoringTaskExecutor;

    /**
     * 提交作文并异步评分。返回时通常为 SCORING，前端通过详情接口轮询完成状态。
     */
    @Transactional
    public EssayScoreResponse submitAndScore(EssaySubmitRequest request) {
        Long userId = currentUserService.requireUserId();
        EssayType essayType = EssayType.fromCode(request.essayType());
        int wordCount = countWords(request.content());
        int charCount = countChars(request.content());
        InputInspection inspection = essayInputAnalyzer.analyze(essayType, request.taskPrompt(), request.content(), wordCount, charCount);
        rejectIfNeeded(inspection);

        String idempotencyKey = normalizeNullable(request.idempotencyKey());
        String contentHash = ScoringIdempotencyService.contentHash(essayType.name(), request.taskPrompt(), request.content());
        EssayVersionContext versionContext = resolveVersionContext(userId, request.parentEssayId());
        String cacheContentHash = versionContext.parentEssayId() == null ? contentHash : null;

        Optional<Long> cachedEssayId = idempotencyService.findCachedEssayId(userId, idempotencyKey, cacheContentHash);
        if (cachedEssayId.isPresent()) {
            log.info("命中 Redis 幂等缓存: essayId={}", cachedEssayId.get());
            return getEssayScoreResponse(cachedEssayId.get());
        }

        EssayScoreResponse existing = findExistingSubmission(userId, idempotencyKey, cacheContentHash);
        if (existing != null) {
            cacheExistingSubmission(userId, existing, cacheContentHash);
            return existing;
        }

        log.info("提交作文，类型: {}, 英文词数: {}, 字符数: {}, contentHash={}", essayType, wordCount, charCount, contentHash);

        ApiConfig config = resolveConfig(userId, request.configId());
        RubricDefinition rubric = rubricService.getActiveRubric(essayType);

        Essay essay = new Essay();
        essay.setUserId(userId);
        essay.setEssayType(essayType.name());
        essay.setEssayGroupId(versionContext.essayGroupId());
        essay.setVersionNo(versionContext.versionNo());
        essay.setParentEssayId(versionContext.parentEssayId());
        essay.setTaskPrompt(normalizeNullable(request.taskPrompt()));
        essay.setContent(request.content());
        essay.setWordCount(wordCount);
        essay.setCharCount(charCount);
        essay.setInputAnalysisJson(writeJson(inspection.inputAnalysis()));
        essay.setSafetyAnalysisJson(writeJson(inspection.safetyAnalysis()));
        essay.setIdempotencyKey(idempotencyKey);
        essay.setContentHash(contentHash);

        try {
            essayMapper.insert(essay);
            if (essay.getEssayGroupId() == null) {
                essay.setEssayGroupId(essay.getId());
                essayMapper.updateById(essay);
            }
        } catch (DuplicateKeyException e) {
            log.info("DB 幂等唯一索引命中: idempotencyKey={}", idempotencyKey);
            EssayScoreResponse duplicate = findExistingSubmission(userId, idempotencyKey, cacheContentHash);
            if (duplicate != null) {
                return duplicate;
            }
            throw e;
        }

        EssayScore score = new EssayScore();
        score.setEssayId(essay.getId());
        score.setApiConfigId(config.getId());
        score.setScoringStatus("SCORING");
        score.setRubricType(rubric.profile().getTypeCode());
        score.setRubricVersion(rubric.version().getVersion());
        score.setAiModel(config.getModelName());
        score.setAttemptCount(1);
        essayScoreMapper.insert(score);

        idempotencyService.cacheScoring(userId, idempotencyKey, cacheContentHash, essay.getId());
        scheduleScoringAfterCommit(essay.getId(), score.getId());

        return buildScoreResponse(essay, score, null);
    }

    public Page<EssayHistoryItem> getHistory(int page, int size) {
        Long userId = currentUserService.requireUserId();
        Page<Essay> essayPage = essayMapper.selectPage(
            new Page<>(page, size),
            new LambdaQueryWrapper<Essay>()
                .eq(Essay::getUserId, userId)
                .orderByDesc(Essay::getCreatedAt)
        );

        Page<EssayHistoryItem> result = new Page<>(essayPage.getCurrent(), essayPage.getSize(), essayPage.getTotal());
        result.setRecords(essayPage.getRecords().stream()
            .map(this::toHistoryItem)
            .toList());
        return result;
    }

    public Essay getEssay(Long id) {
        Long userId = currentUserService.requireUserId();
        Essay essay = essayMapper.selectOne(
            new LambdaQueryWrapper<Essay>()
                .eq(Essay::getId, id)
                .eq(Essay::getUserId, userId)
                .last("LIMIT 1")
        );
        if (essay == null) {
            throw new BusinessException("作文不存在");
        }
        fillCountsIfMissing(essay);
        return essay;
    }

    public EssayScoreResponse getEssayScoreResponse(Long essayId) {
        Essay essay = getEssay(essayId);
        EssayScore score = getScore(essayId);
        if (score == null) {
            return new EssayScoreResponse(
                essayId,
                null,
                EssayResponse.fromEntity(essay),
                null,
                "MISSING",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                essay.getIdempotencyKey(),
                essay.getContentHash(),
                null,
                null,
                null,
                false,
                null
            );
        }
        RubricScoringResult result = StringUtils.hasText(score.getResultJson()) ? readResult(score.getResultJson()) : null;
        return buildScoreResponse(essay, score, result);
    }

    public EssayScore getScore(Long essayId) {
        return essayScoreMapper.selectOne(
            new LambdaQueryWrapper<EssayScore>()
                .eq(EssayScore::getEssayId, essayId)
                .orderByDesc(EssayScore::getCreatedAt)
                .last("LIMIT 1")
        );
    }

    static int countWords(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        var matcher = ENGLISH_WORD_PATTERN.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    static int countChars(String content) {
        return content == null ? 0 : content.codePointCount(0, content.length());
    }

    private ApiConfig resolveConfig(Long userId, Long configId) {
        if (configId != null) {
            ApiConfig config = apiConfigMapper.selectOne(
                new LambdaQueryWrapper<ApiConfig>()
                    .eq(ApiConfig::getId, configId)
                    .and(wrapper -> wrapper
                        .eq(ApiConfig::getOwnerUserId, userId)
                        .eq(ApiConfig::getVisibility, "PRIVATE")
                        .or()
                        .eq(ApiConfig::getVisibility, "PUBLIC")
                        .eq(ApiConfig::getAllowPublicUse, true)
                    )
                    .last("LIMIT 1")
            );
            if (config == null) {
                throw new BusinessException("指定的配置不存在");
            }
            return config;
        }

        ApiConfig config = apiConfigMapper.selectOne(
            new LambdaQueryWrapper<ApiConfig>()
                .eq(ApiConfig::getOwnerUserId, userId)
                .eq(ApiConfig::getVisibility, "PRIVATE")
                .eq(ApiConfig::getIsDefault, true)
                .last("LIMIT 1")
        );
        if (config == null) {
            throw new BusinessException("请先配置API并设置为默认");
        }
        return config;
    }

    private EssayVersionContext resolveVersionContext(Long userId, Long parentEssayId) {
        if (parentEssayId == null) {
            return new EssayVersionContext(null, 1, null);
        }
        Essay parent = essayMapper.selectOne(
            new LambdaQueryWrapper<Essay>()
                .eq(Essay::getId, parentEssayId)
                .eq(Essay::getUserId, userId)
                .last("LIMIT 1")
        );
        if (parent == null) {
            throw new BusinessException("原作文不存在，无法创建修改版");
        }
        Long groupId = parent.getEssayGroupId() == null ? parent.getId() : parent.getEssayGroupId();
        Essay latest = essayMapper.selectOne(
            new LambdaQueryWrapper<Essay>()
                .eq(Essay::getUserId, userId)
                .eq(Essay::getEssayGroupId, groupId)
                .orderByDesc(Essay::getVersionNo)
                .last("LIMIT 1")
        );
        int latestVersion = latest != null && latest.getVersionNo() != null
            ? latest.getVersionNo()
            : (parent.getVersionNo() == null ? 1 : parent.getVersionNo());
        return new EssayVersionContext(groupId, latestVersion + 1, parent.getId());
    }

    private EssayScoreResponse findExistingSubmission(Long userId, String idempotencyKey, String contentHash) {
        Essay byKey = null;
        if (StringUtils.hasText(idempotencyKey)) {
            byKey = essayMapper.selectOne(
                new LambdaQueryWrapper<Essay>()
                    .eq(Essay::getUserId, userId)
                    .eq(Essay::getIdempotencyKey, idempotencyKey)
                    .last("LIMIT 1")
            );
        }
        if (byKey != null) {
            return getEssayScoreResponse(byKey.getId());
        }

        if (!StringUtils.hasText(contentHash)) {
            return null;
        }
        Essay recentSameContent = essayMapper.selectOne(
            new LambdaQueryWrapper<Essay>()
                .eq(Essay::getUserId, userId)
                .eq(Essay::getContentHash, contentHash)
                .ge(Essay::getCreatedAt, LocalDateTime.now().minusMinutes(RECENT_CONTENT_DUPLICATE_MINUTES))
                .orderByDesc(Essay::getCreatedAt)
                .last("LIMIT 1")
        );
        if (recentSameContent == null) {
            return null;
        }
        EssayScore score = getScore(recentSameContent.getId());
        if (score == null || "FAILED".equals(score.getScoringStatus())) {
            return null;
        }
        return buildScoreResponse(
            recentSameContent,
            score,
            StringUtils.hasText(score.getResultJson()) ? readResult(score.getResultJson()) : null
        );
    }

    private void cacheExistingSubmission(Long userId, EssayScoreResponse response, String cacheContentHash) {
        if (response == null) {
            return;
        }
        String status = response.scoringStatus();
        if ("COMPLETED".equals(status)) {
            idempotencyService.cacheCompleted(userId, response.idempotencyKey(), cacheContentHash, response.essayId());
        } else if ("FAILED".equals(status)) {
            idempotencyService.cacheFailed(userId, response.idempotencyKey(), cacheContentHash, response.essayId());
        } else {
            idempotencyService.cacheScoring(userId, response.idempotencyKey(), cacheContentHash, response.essayId());
        }
    }

    private EssayHistoryItem toHistoryItem(Essay essay) {
        fillCountsIfMissing(essay);
        EssayScore score = getScore(essay.getId());
        EssayType type = EssayType.fromCode(essay.getEssayType());
        return new EssayHistoryItem(
            essay.getId(),
            essay.getEssayType(),
            type.getDisplayName(),
            EssayResponse.summarize(essay.getTaskPrompt()),
            EssayResponse.summarize(essay.getContent()),
            essay.getWordCount(),
            essay.getEssayGroupId(),
            essay.getVersionNo(),
            essay.getParentEssayId(),
            score == null ? null : score.getNativeScoreDisplay(),
            score == null ? null : score.getNormalizedScore(),
            score == null ? null : score.getGradeLabel(),
            score == null ? null : score.getConfidenceLevel(),
            score == null ? "MISSING" : score.getScoringStatus(),
            score == null ? null : score.getAiModel(),
            essay.getCreatedAt()
        );
    }

    private EssayScoreResponse buildScoreResponse(Essay essay, EssayScore score, RubricScoringResult result) {
        AiUsageSummary aiUsage = aiInvocationLogService.summarize(essay.getUserId(), essay.getId(), score.getId());
        return new EssayScoreResponse(
            essay.getId(),
            score.getId(),
            EssayResponse.fromEntity(essay),
            result,
            score.getScoringStatus(),
            score.getAiModel(),
            score.getTokensUsed(),
            score.getProcessingTime(),
            aiUsage,
            score.getRubricType(),
            score.getRubricVersion(),
            score.getNativeScore(),
            score.getNativeScoreDisplay(),
            score.getNormalizedScore(),
            score.getGradeLabel(),
            score.getConfidenceLevel(),
            essay.getIdempotencyKey(),
            essay.getContentHash(),
            score.getErrorCode(),
            score.getErrorMessage(),
            score.getAttemptCount(),
            isRetryableFailure(score.getErrorCode()) && "FAILED".equals(score.getScoringStatus())
                && (score.getAttemptCount() == null || score.getAttemptCount() < 3),
            score.getCreatedAt()
        );
    }

    @Transactional
    public EssayScoreResponse retryScoring(Long essayId) {
        Essay essay = getEssay(essayId);
        EssayScore score = getScore(essayId);
        if (score == null) {
            throw new BusinessException("评分记录不存在");
        }
        if (!"FAILED".equals(score.getScoringStatus())) {
            throw new BusinessException("只有失败的评分任务可以重试");
        }
        int attemptCount = score.getAttemptCount() == null ? 1 : score.getAttemptCount();
        if (attemptCount >= 3) {
            throw new BusinessException("评分重试次数已达上限");
        }
        if (!isRetryableFailure(score.getErrorCode())) {
            throw new BusinessException("当前失败类型不可重试，请调整后重新提交");
        }

        score.setScoringStatus("SCORING");
        score.setAttemptCount(attemptCount + 1);
        score.setErrorCode(null);
        score.setErrorMessage(null);
        score.setFailureDetailJson(null);
        essayScoreMapper.updateById(score);
        idempotencyService.cacheScoring(essay.getUserId(), essay.getIdempotencyKey(), cacheableContentHash(essay), essay.getId());
        scheduleScoringAfterCommit(essay.getId(), score.getId());
        return buildScoreResponse(essay, score, null);
    }

    private void scheduleScoringAfterCommit(Long essayId, Long scoreId) {
        Runnable task = () -> scoringTaskExecutor.execute(() -> runScoringJob(essayId, scoreId));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private void runScoringJob(Long essayId, Long scoreId) {
        long startTime = System.currentTimeMillis();
        Essay essay = essayMapper.selectById(essayId);
        EssayScore score = essayScoreMapper.selectById(scoreId);
        if (essay == null || score == null) {
            log.warn("异步评分任务缺少记录: essayId={}, scoreId={}", essayId, scoreId);
            return;
        }
        if ("COMPLETED".equals(score.getScoringStatus())) {
            return;
        }

        try {
            EssayType essayType = EssayType.fromCode(essay.getEssayType());
            ApiConfig config = apiConfigMapper.selectById(score.getApiConfigId());
            if (config == null) {
                throw new BusinessException("评分配置不存在或已删除");
            }
            RubricDefinition rubric = rubricService.getActiveRubric(essayType);
            InputInspection inspection = essayInputAnalyzer.analyze(
                essayType,
                essay.getTaskPrompt(),
                essay.getContent(),
                essay.getWordCount(),
                essay.getCharCount()
            );
            rejectIfNeeded(inspection);

            AIService.ScoringOutcome outcome = aiService.scoreEssay(
                essayType,
                essay.getTaskPrompt(),
                essay.getContent(),
                rubric,
                config
            );
            long duration = System.currentTimeMillis() - startTime;
            RubricScoringResult result = applyInspection(outcome.result(), inspection);
            aiInvocationLogService.recordScoringInvocations(
                essay.getUserId(),
                essay.getId(),
                score.getId(),
                config,
                score.getAttemptCount() == null ? 1 : score.getAttemptCount(),
                outcome.invocations()
            );

            score.setScoringStatus("COMPLETED");
            score.setRubricType(result.rubric().type());
            score.setRubricVersion(result.rubric().version());
            score.setNativeScore(result.nativeScore().value());
            score.setNativeScoreDisplay(result.nativeScore().display());
            score.setNormalizedScore(result.normalizedScore().value());
            score.setGradeLabel(result.gradeLabel());
            score.setConfidenceLevel(result.confidence().level());
            score.setResultJson(writeJson(result));
            score.setAiModel(outcome.modelName());
            score.setTokensUsed(outcome.totalTokens());
            score.setProcessingTime((int) duration);
            score.setErrorCode(null);
            score.setErrorMessage(null);
            score.setFailureDetailJson(null);
            essayScoreMapper.updateById(score);

            idempotencyService.cacheCompleted(essay.getUserId(), essay.getIdempotencyKey(), cacheableContentHash(essay), essay.getId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("异步评分失败: essayId={}, scoreId={}", essayId, scoreId, e);
            ScoringFailure failure = classifyFailure(e);
            score.setScoringStatus("FAILED");
            score.setProcessingTime((int) duration);
            score.setErrorCode(failure.code());
            score.setErrorMessage(failure.userMessage());
            score.setFailureDetailJson(writeJson(Map.of(
                "code", failure.code(),
                "stage", failure.stage(),
                "retryable", failure.retryable(),
                "technicalMessage", truncate(e.getMessage(), 500)
            )));
            essayScoreMapper.updateById(score);
            idempotencyService.cacheFailed(essay.getUserId(), essay.getIdempotencyKey(), cacheableContentHash(essay), essay.getId());
        }
    }

    private RubricScoringResult applyInspection(RubricScoringResult result, InputInspection inspection) {
        RubricScoringResult.Confidence adjustedConfidence = adjustConfidence(result.confidence(), inspection);
        String safetyNotice = inspection.safetyAnalysis().notice().isBlank()
            ? result.safetyNotice()
            : inspection.safetyAnalysis().notice();
        return new RubricScoringResult(
            result.nativeScore(),
            result.normalizedScore(),
            result.rubric(),
            result.gradeLabel(),
            adjustedConfidence,
            result.dimensions(),
            result.annotations(),
            result.summary(),
            safetyNotice,
            inspection.inputAnalysis(),
            result.referenceEssay()
        );
    }

    private RubricScoringResult.Confidence adjustConfidence(
        RubricScoringResult.Confidence confidence,
        InputInspection inspection
    ) {
        List<String> warnings = new ArrayList<>(confidence.warnings());
        warnings.addAll(inspection.inputAnalysis().warnings());
        warnings.addAll(inspection.safetyAnalysis().confidenceWarnings());

        double score = confidence.score() == null ? 0.70 : confidence.score();
        if (AnalysisStatus.WARN.name().equals(inspection.inputAnalysis().status())) {
            score -= 0.08;
        }
        if (inspection.safetyAnalysis().warned()) {
            score -= 0.12;
        }
        score = Math.max(0.35, Math.min(1.0, score));
        String level = score >= 0.80 ? "HIGH" : score >= 0.55 ? "MEDIUM" : "LOW";
        return new RubricScoringResult.Confidence(level, score, confidence.reasons(), warnings);
    }

    private void rejectIfNeeded(InputInspection inspection) {
        if (AnalysisStatus.REJECT.name().equals(inspection.inputAnalysis().status())) {
            throw new BusinessException("作文输入不符合要求: " + String.join("；", inspection.inputAnalysis().rejections()));
        }
    }

    private ScoringFailure classifyFailure(Exception e) {
        AIProviderException providerException = findCause(e, AIProviderException.class);
        if (providerException != null) {
            AIProviderErrorCode code = providerException.getErrorCode();
            return switch (code) {
                case AUTH_ERROR, MODEL_NOT_FOUND, INVALID_BASE_URL, INVALID_PROVIDER_CONFIG ->
                    new ScoringFailure("CONFIG_ERROR", "AI 配置不可用，请检查模型配置。", "PROVIDER_CALL", false);
                case RATE_LIMIT ->
                    new ScoringFailure("PROVIDER_RATE_LIMITED", "当前 Provider 请求较多，请稍后重试。", "PROVIDER_CALL", true);
                case NETWORK_TIMEOUT ->
                    new ScoringFailure("PROVIDER_TIMEOUT", "AI 分析时间过长，请稍后重试。", "PROVIDER_CALL", true);
                case CONTENT_POLICY_BLOCKED ->
                    new ScoringFailure("PROVIDER_REJECTED", "作文内容未能通过模型安全策略，请调整后重试。", "PROVIDER_CALL", false);
                case STRUCTURED_OUTPUT_FAILED ->
                    new ScoringFailure("INVALID_AI_RESPONSE", "AI 返回格式异常，请稍后重试。", "AI_RESPONSE_VALIDATE", true);
                case NETWORK_ERROR, PROVIDER_5XX, UNKNOWN_ERROR ->
                    new ScoringFailure("PROVIDER_ERROR", "AI Provider 暂时不可用，请稍后重试。", "PROVIDER_CALL", true);
            };
        }

        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("作文输入不符合要求")) {
            return new ScoringFailure("CONTENT_REJECTED", "作文内容不符合提交要求，请修改后重新提交。", "SUBMIT_VALIDATION", false);
        }
        if (message.contains("解析评分结果失败") || message.contains("读取评分结果失败")) {
            return new ScoringFailure("INVALID_AI_RESPONSE", "AI 返回格式异常，请稍后重试。", "AI_RESPONSE_PARSE", true);
        }
        if (message.contains("评分配置不存在")) {
            return new ScoringFailure("CONFIG_ERROR", "AI 配置不可用，请检查模型配置。", "RUBRIC_LOAD", false);
        }
        return new ScoringFailure("SCORING_FAILED", "评分失败，请稍后重试。", "UNKNOWN", true);
    }

    private static boolean isRetryableFailure(String errorCode) {
        return errorCode == null
            || "PROVIDER_TIMEOUT".equals(errorCode)
            || "PROVIDER_RATE_LIMITED".equals(errorCode)
            || "PROVIDER_ERROR".equals(errorCode)
            || "INVALID_AI_RESPONSE".equals(errorCode)
            || "SCORING_FAILED".equals(errorCode);
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static String cacheableContentHash(Essay essay) {
        if (essay == null || essay.getParentEssayId() != null) {
            return null;
        }
        return essay.getContentHash();
    }

    private RubricScoringResult readResult(String resultJson) {
        try {
            return objectMapper.readValue(resultJson, RubricScoringResult.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException("读取评分结果失败: " + e.getMessage(), e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("序列化评分结果失败: " + e.getMessage(), e);
        }
    }

    private void fillCountsIfMissing(Essay essay) {
        if (essay == null) {
            return;
        }
        if (essay.getWordCount() == null) {
            essay.setWordCount(countWords(essay.getContent()));
        }
        if (essay.getCharCount() == null) {
            essay.setCharCount(countChars(essay.getContent()));
        }
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record ScoringFailure(String code, String userMessage, String stage, boolean retryable) {
    }

    private record EssayVersionContext(Long essayGroupId, Integer versionNo, Long parentEssayId) {
    }
}
