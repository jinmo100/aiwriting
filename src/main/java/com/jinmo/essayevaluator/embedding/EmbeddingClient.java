package com.jinmo.essayevaluator.embedding;

import java.util.List;

/**
 * Embedding 客户端抽象。
 *
 * <p>业务层负责解密 API Key，客户端只接收本次调用所需明文 Key，不保存、不返回。</p>
 */
public interface EmbeddingClient {

    EmbeddingResult embed(EmbeddingConfig config, String apiKey, List<String> input);

    record EmbeddingResult(
        List<List<Double>> embeddings,
        long latencyMillis,
        String model,
        int dimensions
    ) {
    }
}
