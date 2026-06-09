package com.jinmo.essayevaluator.rag;

import com.jinmo.essayevaluator.embedding.EmbeddingClient;
import com.jinmo.essayevaluator.embedding.EmbeddingConfig;
import com.jinmo.essayevaluator.embedding.EmbeddingConfigService;
import com.jinmo.essayevaluator.mapper.RagChunkEmbeddingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * RAG 检索服务。
 *
 * <p>检索强制使用 userId + embeddingConfigId + embeddingVersion 过滤，避免跨用户或跨配置混用向量。</p>
 */
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private static final int MIN_TOP_K = 3;
    private static final int MAX_TOP_K = 5;

    private final EmbeddingConfigService embeddingConfigService;
    private final EmbeddingClient embeddingClient;
    private final RagChunkEmbeddingMapper ragChunkEmbeddingMapper;

    public List<RagRetrievedChunk> retrieve(
        Long userId,
        EmbeddingConfig config,
        String query,
        String essayType,
        int topK
    ) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String apiKey = embeddingConfigService.resolvePlainApiKey(config);
        EmbeddingClient.EmbeddingResult result = embeddingClient.embed(config, apiKey, List.of(query));
        String vectorLiteral = RagIndexJobHandler.toPgVectorLiteral(result.embeddings().getFirst());
        List<RagRetrievedChunk> chunks = ragChunkEmbeddingMapper.searchTopK(
            userId,
            config.getId(),
            RagIndexJobHandler.embeddingVersion(config),
            essayType,
            vectorLiteral,
            normalizeTopK(topK)
        );
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setRankNo(i + 1);
        }
        return chunks;
    }

    private int normalizeTopK(int topK) {
        return Math.max(MIN_TOP_K, Math.min(MAX_TOP_K, topK));
    }
}
