package com.jinmo.essayevaluator.embedding;

import com.jinmo.essayevaluator.ai.provider.AIProviderErrorCode;

/**
 * Embedding Provider 调用异常。
 *
 * <p>只携带标准化错误码和安全提示，避免将 Provider 原始响应中的敏感信息透传给前端。</p>
 */
public class EmbeddingClientException extends RuntimeException {

    private final AIProviderErrorCode errorCode;
    private final String safeMessage;

    public EmbeddingClientException(AIProviderErrorCode errorCode, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.errorCode = errorCode;
        this.safeMessage = safeMessage;
    }

    public AIProviderErrorCode getErrorCode() {
        return errorCode;
    }

    public String getSafeMessage() {
        return safeMessage;
    }
}
