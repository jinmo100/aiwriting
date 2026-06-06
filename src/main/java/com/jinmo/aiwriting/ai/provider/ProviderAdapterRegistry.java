package com.jinmo.aiwriting.ai.provider;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ProviderAdapterRegistry {

    private final Map<ProviderType, AIProviderAdapter> adapters;

    public ProviderAdapterRegistry(List<AIProviderAdapter> adapters) {
        this.adapters = new EnumMap<>(ProviderType.class);
        for (AIProviderAdapter adapter : adapters) {
            this.adapters.put(adapter.providerType(), adapter);
        }
    }

    public AIProviderAdapter get(ProviderType providerType) {
        AIProviderAdapter adapter = adapters.get(providerType);
        if (adapter == null) {
            throw new AIProviderException(providerType, AIProviderErrorCode.INVALID_PROVIDER_CONFIG,
                "不支持的 Provider 类型: " + providerType, null);
        }
        return adapter;
    }
}
