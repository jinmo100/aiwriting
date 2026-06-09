package com.jinmo.essayevaluator.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.essayevaluator.common.exception.BusinessException;
import com.jinmo.essayevaluator.embedding.EmbeddingClient;
import com.jinmo.essayevaluator.embedding.EmbeddingConfig;
import com.jinmo.essayevaluator.embedding.EmbeddingConfigService;
import com.jinmo.essayevaluator.job.BackgroundJob;
import com.jinmo.essayevaluator.job.BackgroundJobHandler;
import com.jinmo.essayevaluator.job.BackgroundJobStatus;
import com.jinmo.essayevaluator.job.BackgroundJobType;
import com.jinmo.essayevaluator.mapper.RagChunkEmbeddingMapper;
import com.jinmo.essayevaluator.mapper.RagChunkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 知识库索引任务处理器。
 *
 * <p>任务 payload 只保存 embeddingConfigId/force 等安全业务参数，实际 API Key 在后台执行时按
 * ownerUserId 重新加载并解密，避免把明文 Key 或密文写入 background_jobs。</p>
 */
@Component
@RequiredArgsConstructor
public class RagIndexJobHandler implements BackgroundJobHandler {

    public static final String KNOWLEDGE_VERSION = "RAG_KB_V1";
    private static final int SUPPORTED_DIMENSIONS = 1536;

    private final EmbeddingConfigService embeddingConfigService;
    private final EmbeddingClient embeddingClient;
    private final RagChunkMapper ragChunkMapper;
    private final RagChunkEmbeddingMapper ragChunkEmbeddingMapper;
    private final ObjectMapper objectMapper;

    @Override
    public BackgroundJobType jobType() {
        return BackgroundJobType.RAG_INDEX;
    }

    @Override
    public JobResult handle(BackgroundJob job) throws Exception {
        RagIndexPayload payload = parsePayload(job);
        if (payload.embeddingConfigId() == null) {
            return JobResult.skipped(skipResult("请先配置 Embedding", "OPEN_EMBEDDING_CONFIG"));
        }

        EmbeddingConfig config;
        String apiKey;
        try {
            config = embeddingConfigService.loadOwnedConfigForUser(job.getOwnerUserId(), payload.embeddingConfigId());
            apiKey = embeddingConfigService.resolvePlainApiKey(config);
        } catch (BusinessException error) {
            return JobResult.skipped(skipResult(error.getMessage(), "OPEN_EMBEDDING_CONFIG"));
        }

        String embeddingVersion = embeddingVersion(config);
        if (payload.force()) {
            ragChunkEmbeddingMapper.deleteByUserConfigVersion(job.getOwnerUserId(), config.getId(), embeddingVersion);
        }

        List<RagChunk> chunks = ragChunkMapper.findActiveChunks();
        if (chunks.isEmpty()) {
            return JobResult.completed(progressResult(0, 0, 0, embeddingVersion));
        }

        List<String> input = chunks.stream().map(RagChunk::getContent).toList();
        EmbeddingClient.EmbeddingResult embeddingResult = embeddingClient.embed(config, apiKey, input);
        validateEmbeddingResult(chunks, embeddingResult);

        int processed = 0;
        for (int i = 0; i < chunks.size(); i++) {
            RagChunk chunk = chunks.get(i);
            List<Double> vector = embeddingResult.embeddings().get(i);
            ragChunkEmbeddingMapper.upsertEmbedding(
                job.getOwnerUserId(),
                config.getId(),
                chunk.getId(),
                embeddingResult.model(),
                embeddingResult.dimensions(),
                embeddingVersion,
                chunk.getContentHash(),
                toPgVectorLiteral(vector)
            );
            processed++;
        }

        return JobResult.completed(progressResult(chunks.size(), processed, 0, embeddingVersion));
    }

    public static String embeddingVersion(EmbeddingConfig config) {
        return config.getModelName() + ":" + config.getDimensions() + ":" + KNOWLEDGE_VERSION;
    }

    static String toPgVectorLiteral(List<Double> vector) {
        if (vector == null || vector.size() != SUPPORTED_DIMENSIONS) {
            throw new BusinessException("Embedding 向量维度必须为 1536");
        }
        List<String> values = new ArrayList<>(vector.size());
        for (Double value : vector) {
            if (value == null || value.isNaN() || value.isInfinite()) {
                throw new BusinessException("Embedding 向量包含非法数值");
            }
            values.add(Double.toString(value));
        }
        return "[" + String.join(",", values) + "]";
    }

    private RagIndexPayload parsePayload(BackgroundJob job) throws Exception {
        if (job.getPayloadJson() == null || job.getPayloadJson().isBlank()) {
            return new RagIndexPayload(null, false);
        }
        return objectMapper.readValue(job.getPayloadJson(), RagIndexPayload.class);
    }

    private void validateEmbeddingResult(List<RagChunk> chunks, EmbeddingClient.EmbeddingResult embeddingResult) {
        if (embeddingResult == null || embeddingResult.embeddings() == null
            || embeddingResult.embeddings().size() != chunks.size()
            || embeddingResult.dimensions() != SUPPORTED_DIMENSIONS) {
            throw new BusinessException("Embedding Provider 返回结果数量或维度不符合预期");
        }
    }

    private Map<String, Object> progressResult(int totalChunks, int processedChunks, int failedChunks, String embeddingVersion) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalChunks", totalChunks);
        result.put("processedChunks", processedChunks);
        result.put("failedChunks", failedChunks);
        result.put("embeddingVersion", embeddingVersion);
        return result;
    }

    private Map<String, Object> skipResult(String reason, String action) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reason", reason);
        result.put("action", action);
        return result;
    }

    private record RagIndexPayload(Long embeddingConfigId, boolean force) {
    }
}
