package com.jinmo.essayevaluator.ai.provider;

public class AIProviderException extends RuntimeException {

    private final ProviderType providerType;
    private final AIProviderErrorCode errorCode;
    private final String safeMessage;

    public AIProviderException(ProviderType providerType, AIProviderErrorCode errorCode, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.providerType = providerType;
        this.errorCode = errorCode;
        this.safeMessage = safeMessage;
    }

    public ProviderType getProviderType() {
        return providerType;
    }

    public AIProviderErrorCode getErrorCode() {
        return errorCode;
    }

    public String getSafeMessage() {
        return safeMessage;
    }
}
