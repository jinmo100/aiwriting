package com.jinmo.essayevaluator.rag;

import com.jinmo.essayevaluator.common.exception.BusinessException;
import com.jinmo.essayevaluator.embedding.EmbeddingConfig;
import com.jinmo.essayevaluator.embedding.EmbeddingConfigService;
import com.jinmo.essayevaluator.job.BackgroundJob;
import com.jinmo.essayevaluator.job.BackgroundJobDispatcher;
import com.jinmo.essayevaluator.job.BackgroundJobService;
import com.jinmo.essayevaluator.job.BackgroundJobStatus;
import com.jinmo.essayevaluator.job.BackgroundJobType;
import com.jinmo.essayevaluator.mapper.RagChunkEmbeddingMapper;
import com.jinmo.essayevaluator.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.jinmo.essayevaluator.rag.RagIndexDtos.AdminRagIndexRebuildRequest;
import static com.jinmo.essayevaluator.rag.RagIndexDtos.RagIndexRebuildRequest;
import static com.jinmo.essayevaluator.rag.RagIndexDtos.RagIndexStatusResponse;

/**
 * RAG 知识索引编排服务。
 *
 * <p>用户和管理员接口只创建/复用 background_jobs 并立即返回状态，实际 Embedding 消耗发生在后台 handler，
 * 满足“用户显式触发后才消耗 Embedding Key”的边界。</p>
 */
@Service
@RequiredArgsConstructor
public class RagIndexService {

    private final CurrentUserService currentUserService;
    private final EmbeddingConfigService embeddingConfigService;
    private final BackgroundJobService backgroundJobService;
    private final BackgroundJobDispatcher backgroundJobDispatcher;
    private final RagChunkEmbeddingMapper ragChunkEmbeddingMapper;

    @Transactional(readOnly = true)
    public RagIndexStatusResponse myStatus(Long embeddingConfigId) {
        Long userId = currentUserService.requireUserId();
        return statusForUser(userId, embeddingConfigId);
    }

    @Transactional
    public RagIndexStatusResponse rebuildMy(RagIndexRebuildRequest request) {
        Long userId = currentUserService.requireUserId();
        EmbeddingConfig config = resolveConfigForUser(userId, request == null ? null : request.embeddingConfigId());
        return createOrReuseIndexJob(userId, userId, config, request != null && Boolean.TRUE.equals(request.force()));
    }

    @Transactional(readOnly = true)
    public RagIndexStatusResponse adminStatus(Long userId, Long embeddingConfigId) {
        currentUserService.requireAdmin();
        if (userId == null) {
            throw new BusinessException("目标用户 ID 不能为空");
        }
        return statusForUser(userId, embeddingConfigId);
    }

    @Transactional
    public RagIndexStatusResponse rebuildForUser(AdminRagIndexRebuildRequest request) {
        currentUserService.requireAdmin();
        Long adminId = currentUserService.requireUserId();
        if (request == null || request.userId() == null) {
            throw new BusinessException("目标用户 ID 不能为空");
        }
        EmbeddingConfig config = resolveConfigForUser(request.userId(), request.embeddingConfigId());
        return createOrReuseIndexJob(
            request.userId(),
            adminId,
            config,
            Boolean.TRUE.equals(request.force())
        );
    }

    private RagIndexStatusResponse statusForUser(Long userId, Long embeddingConfigId) {
        EmbeddingConfig config;
        try {
            config = resolveConfigForUser(userId, embeddingConfigId);
        } catch (BusinessException noConfig) {
            BackgroundJob latest = backgroundJobService.findLatestForOwner(BackgroundJobType.RAG_INDEX, userId);
            return new RagIndexStatusResponse(
                false,
                userId,
                embeddingConfigId,
                null,
                0,
                latest == null ? null : latest.getId(),
                latest == null ? null : latest.getStatus(),
                latest == null ? null : latest.getResultJson(),
                latest == null ? null : latest.getErrorCode(),
                latest == null ? null : latest.getErrorMessage(),
                latest == null ? null : latest.getUpdatedAt(),
                "请先配置 Embedding"
            );
        }
        return toStatus(userId, config, backgroundJobService.findLatestForOwner(BackgroundJobType.RAG_INDEX, userId));
    }

    private RagIndexStatusResponse createOrReuseIndexJob(
        Long ownerUserId,
        Long requestedByUserId,
        EmbeddingConfig config,
        boolean force
    ) {
        String embeddingVersion = RagIndexJobHandler.embeddingVersion(config);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("embeddingConfigId", config.getId());
        payload.put("force", force);

        BackgroundJob job = backgroundJobService.createOrReuse(
            BackgroundJobType.RAG_INDEX,
            ownerUserId,
            requestedByUserId,
            businessKey(config.getId(), embeddingVersion),
            payload
        );
        if (job.getStatus() == BackgroundJobStatus.PENDING) {
            scheduleDispatchAfterCommit(job.getId());
        }
        return toStatus(ownerUserId, config, job);
    }

    private RagIndexStatusResponse toStatus(Long ownerUserId, EmbeddingConfig config, BackgroundJob job) {
        String embeddingVersion = RagIndexJobHandler.embeddingVersion(config);
        int indexedChunks = ragChunkEmbeddingMapper.countByUserConfigVersion(
            ownerUserId,
            config.getId(),
            embeddingVersion
        );
        return new RagIndexStatusResponse(
            true,
            ownerUserId,
            config.getId(),
            embeddingVersion,
            indexedChunks,
            job == null ? null : job.getId(),
            job == null ? null : job.getStatus(),
            job == null ? null : job.getResultJson(),
            job == null ? null : job.getErrorCode(),
            job == null ? null : job.getErrorMessage(),
            job == null ? null : job.getUpdatedAt(),
            null
        );
    }

    private EmbeddingConfig resolveConfigForUser(Long userId, Long embeddingConfigId) {
        if (embeddingConfigId != null) {
            return embeddingConfigService.loadOwnedConfigForUser(userId, embeddingConfigId);
        }
        EmbeddingConfig defaultConfig = embeddingConfigService.getDefaultConfigForUser(userId);
        if (defaultConfig == null) {
            throw new BusinessException("请先配置 Embedding");
        }
        return defaultConfig;
    }

    private String businessKey(Long embeddingConfigId, String embeddingVersion) {
        return "rag-index:config:" + embeddingConfigId + ":version:" + embeddingVersion;
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
