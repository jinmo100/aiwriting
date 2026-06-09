package com.jinmo.essayevaluator.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jinmo.essayevaluator.common.exception.BusinessException;
import com.jinmo.essayevaluator.domain.entity.Essay;
import com.jinmo.essayevaluator.domain.entity.EssayScore;
import com.jinmo.essayevaluator.embedding.EmbeddingConfig;
import com.jinmo.essayevaluator.embedding.EmbeddingConfigService;
import com.jinmo.essayevaluator.job.BackgroundJob;
import com.jinmo.essayevaluator.job.BackgroundJobDispatcher;
import com.jinmo.essayevaluator.job.BackgroundJobService;
import com.jinmo.essayevaluator.job.BackgroundJobStatus;
import com.jinmo.essayevaluator.job.BackgroundJobType;
import com.jinmo.essayevaluator.mapper.EssayMapper;
import com.jinmo.essayevaluator.mapper.EssayScoreMapper;
import com.jinmo.essayevaluator.mapper.RagFeedbackCitationMapper;
import com.jinmo.essayevaluator.mapper.RagFeedbackMapper;
import com.jinmo.essayevaluator.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.jinmo.essayevaluator.rag.RagFeedbackDtos.RagFeedbackGenerateRequest;
import static com.jinmo.essayevaluator.rag.RagFeedbackDtos.RagFeedbackResponse;

/**
 * RAG Feedback API 编排服务。
 */
@Service
@RequiredArgsConstructor
public class RagFeedbackService {

    private final CurrentUserService currentUserService;
    private final EssayMapper essayMapper;
    private final EssayScoreMapper essayScoreMapper;
    private final EmbeddingConfigService embeddingConfigService;
    private final BackgroundJobService backgroundJobService;
    private final BackgroundJobDispatcher backgroundJobDispatcher;
    private final RagFeedbackMapper ragFeedbackMapper;
    private final RagFeedbackCitationMapper ragFeedbackCitationMapper;

    @Transactional(readOnly = true)
    public RagFeedbackResponse getFeedback(Long essayId) {
        Long userId = currentUserService.requireUserId();
        loadEssay(userId, essayId);
        RagFeedback feedback = ragFeedbackMapper.findLatestForUserEssay(userId, essayId);
        if (feedback == null) {
            return new RagFeedbackResponse(
                essayId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                "暂无知识点增强反馈"
            );
        }
        return toResponse(essayId, feedback, null);
    }

    @Transactional
    public RagFeedbackResponse generate(Long essayId, RagFeedbackGenerateRequest request) {
        Long userId = currentUserService.requireUserId();
        Essay essay = loadEssay(userId, essayId);
        EssayScore score = loadCompletedScore(essay.getId());
        EmbeddingConfig embeddingConfig = resolveEmbeddingConfig(userId, request == null ? null : request.embeddingConfigId());
        if (embeddingConfig == null) {
            return skippedResponse(essayId, "请先配置 Embedding");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("essayId", essay.getId());
        payload.put("embeddingConfigId", embeddingConfig.getId());
        BackgroundJob job = backgroundJobService.createOrReuse(
            BackgroundJobType.RAG_FEEDBACK,
            userId,
            userId,
            businessKey(score.getId(), embeddingConfig.getId()),
            payload
        );
        if (job.getStatus() == BackgroundJobStatus.PENDING) {
            scheduleDispatchAfterCommit(job.getId());
        }
        RagFeedback feedback = ragFeedbackMapper.findLatestForUserEssay(userId, essayId);
        return toResponse(essayId, feedback, job);
    }

    @Transactional
    public RagFeedbackResponse retry(Long essayId, RagFeedbackGenerateRequest request) {
        return generate(essayId, request);
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

    private EssayScore loadCompletedScore(Long essayId) {
        EssayScore score = essayScoreMapper.selectOne(
            new LambdaQueryWrapper<EssayScore>()
                .eq(EssayScore::getEssayId, essayId)
                .orderByDesc(EssayScore::getCreatedAt)
                .last("LIMIT 1")
        );
        if (score == null || !"COMPLETED".equals(score.getScoringStatus())) {
            throw new BusinessException("请等待评分完成后再生成知识点增强反馈");
        }
        return score;
    }

    private EmbeddingConfig resolveEmbeddingConfig(Long userId, Long embeddingConfigId) {
        if (embeddingConfigId != null) {
            return embeddingConfigService.loadOwnedConfigForUser(userId, embeddingConfigId);
        }
        return embeddingConfigService.getDefaultConfigForUser(userId);
    }

    private RagFeedbackResponse toResponse(Long essayId, RagFeedback feedback, BackgroundJob job) {
        if (job == null && feedback != null && feedback.getJobId() != null) {
            job = backgroundJobService.getById(feedback.getJobId());
        }
        List<RagFeedbackDtos.RagFeedbackCitationResponse> citations = feedback == null
            ? List.of()
            : ragFeedbackCitationMapper.findByFeedbackId(feedback.getId()).stream()
                .map(RagFeedbackDtos.RagFeedbackCitationResponse::from)
                .toList();
        return new RagFeedbackResponse(
            essayId,
            feedback == null ? null : feedback.getId(),
            job == null ? (feedback == null ? null : feedback.getJobId()) : job.getId(),
            job == null ? null : job.getStatus(),
            job == null ? null : job.getResultJson(),
            job == null ? null : job.getErrorCode(),
            job == null ? null : job.getErrorMessage(),
            feedback == null ? null : feedback.getFeedbackJson(),
            citations,
            feedback == null ? null : feedback.getUpdatedAt(),
            null
        );
    }

    private RagFeedbackResponse skippedResponse(Long essayId, String message) {
        return new RagFeedbackResponse(
            essayId,
            null,
            null,
            BackgroundJobStatus.SKIPPED,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            message
        );
    }

    private String businessKey(Long scoreId, Long embeddingConfigId) {
        return "rag-feedback:score:" + scoreId + ":embedding:" + embeddingConfigId;
    }

    private void scheduleDispatchAfterCommit(Long jobId) {
        Runnable task = () -> backgroundJobDispatcher.dispatchAsync(jobId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }
}
