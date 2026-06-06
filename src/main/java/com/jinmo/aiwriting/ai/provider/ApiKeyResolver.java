package com.jinmo.aiwriting.ai.provider;

import com.jinmo.aiwriting.common.exception.BusinessException;
import com.jinmo.aiwriting.domain.entity.ApiConfig;
import com.jinmo.aiwriting.security.ApiKeyEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ApiKeyResolver {

    private final ApiKeyEncryptionService encryptionService;

    public String resolve(ApiConfig config) {
        if (StringUtils.hasText(config.getApiKeyEncrypted())) {
            return encryptionService.decrypt(config.getApiKeyEncrypted());
        }
        if (StringUtils.hasText(config.getApiKey())) {
            return config.getApiKey().trim();
        }
        throw new BusinessException("配置未保存 API Key");
    }
}
