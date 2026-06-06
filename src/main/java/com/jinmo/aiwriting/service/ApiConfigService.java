package com.jinmo.aiwriting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jinmo.aiwriting.ai.provider.ProviderConfigInvalidationService;
import com.jinmo.aiwriting.ai.provider.ProviderEndpointResolver;
import com.jinmo.aiwriting.ai.provider.ProviderType;
import com.jinmo.aiwriting.common.exception.BusinessException;
import com.jinmo.aiwriting.domain.dto.ApiConfigCreateRequest;
import com.jinmo.aiwriting.domain.dto.ApiConfigResponse;
import com.jinmo.aiwriting.domain.dto.ApiConfigUpdateRequest;
import com.jinmo.aiwriting.domain.entity.ApiConfig;
import com.jinmo.aiwriting.mapper.ApiConfigMapper;
import com.jinmo.aiwriting.security.ApiKeyEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * API配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiConfigService {

    private final ApiConfigMapper apiConfigMapper;
    private final ApiKeyEncryptionService apiKeyEncryptionService;
    private final ProviderEndpointResolver endpointResolver;
    private final ProviderConfigInvalidationService invalidationService;

    /**
     * 创建配置
     */
    @Transactional
    public ApiConfigResponse createConfig(ApiConfigCreateRequest request) {
        log.info("创建API配置: {}", request.configName());

        ApiConfig config = new ApiConfig();
        config.setConfigName(request.configName());
        config.setProviderType(request.providerType());
        config.setProviderLabel(resolveProviderLabel(request.providerLabel(), request.providerType()));
        config.setProvider(config.getProviderLabel());
        config.setBaseUrl(endpointResolver.normalizeBaseUrl(request.providerType(), request.baseUrl()));
        config.setApiKeyEncrypted(apiKeyEncryptionService.encrypt(request.apiKey().trim()));
        config.setApiKey(null);
        config.setModelName(request.modelName());
        config.setTemperature(defaultTemperature(request.temperature()));
        config.setMaxTokens(defaultMaxTokens(request.maxTokens()));
        config.setTimeoutSeconds(defaultTimeoutSeconds(request.timeoutSeconds()));
        config.setModelParametersJson(request.modelParametersJson());
        config.setIsDefault(request.isDefault() != null ? request.isDefault() : false);

        // 如果设置为默认，先重置其他配置
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            apiConfigMapper.resetAllDefault();
        }

        apiConfigMapper.insert(config);
        return toResponse(config);
    }

    /**
     * 获取所有配置
     */
    public List<ApiConfigResponse> getAllConfigs() {
        return apiConfigMapper.selectList(
            new LambdaQueryWrapper<ApiConfig>()
                .orderByDesc(ApiConfig::getCreatedAt)
        ).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /**
     * 获取配置详情
     */
    public ApiConfigResponse getConfig(Long id) {
        ApiConfig config = apiConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("配置不存在");
        }
        return toResponse(config);
    }

    /**
     * 获取默认配置
     */
    public ApiConfig getDefaultConfig() {
        return apiConfigMapper.selectOne(
            new LambdaQueryWrapper<ApiConfig>()
                .eq(ApiConfig::getIsDefault, true)
        );
    }

    /**
     * 更新配置
     */
    @Transactional
    public ApiConfigResponse updateConfig(Long id, ApiConfigUpdateRequest request) {
        log.info("更新API配置: {}", id);

        ApiConfig config = apiConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("配置不存在");
        }
        ApiConfig oldConfig = copyConfig(config);

        config.setConfigName(request.configName());
        config.setProviderType(request.providerType());
        config.setProviderLabel(resolveProviderLabel(request.providerLabel(), request.providerType()));
        config.setProvider(config.getProviderLabel());
        config.setBaseUrl(endpointResolver.normalizeBaseUrl(request.providerType(), request.baseUrl()));
        if (StringUtils.hasText(request.apiKey())) {
            config.setApiKeyEncrypted(apiKeyEncryptionService.encrypt(request.apiKey().trim()));
            config.setApiKey(null);
        }
        config.setModelName(request.modelName());
        config.setTemperature(defaultTemperature(request.temperature()));
        config.setMaxTokens(defaultMaxTokens(request.maxTokens()));
        config.setTimeoutSeconds(defaultTimeoutSeconds(request.timeoutSeconds()));
        config.setModelParametersJson(request.modelParametersJson());

        if (request.isDefault() != null) {
            if (Boolean.TRUE.equals(request.isDefault())) {
                apiConfigMapper.resetAllDefault();
            }
            config.setIsDefault(request.isDefault());
        }

        apiConfigMapper.updateById(config);
        invalidationService.invalidate(oldConfig.getId());
        return toResponse(config);
    }

    public String revealApiKey(Long id) {
        ApiConfig config = apiConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("配置不存在");
        }
        return resolvePlainApiKey(config);
    }

    public String resolvePlainApiKey(ApiConfig config) {
        if (StringUtils.hasText(config.getApiKeyEncrypted())) {
            return apiKeyEncryptionService.decrypt(config.getApiKeyEncrypted());
        }
        if (StringUtils.hasText(config.getApiKey())) {
            return config.getApiKey().trim();
        }
        throw new BusinessException("配置未保存 API Key");
    }

    private static String resolveProviderLabel(String providerLabel, ProviderType providerType) {
        return StringUtils.hasText(providerLabel) ? providerLabel.trim() : providerType.value();
    }

    private static Double defaultTemperature(Double temperature) {
        return temperature != null ? temperature : 0.3;
    }

    private static Integer defaultMaxTokens(Integer maxTokens) {
        return maxTokens != null ? maxTokens : 2048;
    }

    private static Integer defaultTimeoutSeconds(Integer timeoutSeconds) {
        return timeoutSeconds != null ? timeoutSeconds : 60;
    }

    private ApiConfigResponse toResponse(ApiConfig config) {
        String previewSource = null;
        try {
            previewSource = resolvePlainApiKey(config);
        } catch (Exception ignored) {
            // 无 Key 或旧数据异常时，仍返回配置主体；不展示 preview。
        }
        return ApiConfigResponse.fromEntity(config, previewSource);
    }

    /**
     * 删除配置
     */
    @Transactional
    public void deleteConfig(Long id) {
        log.info("删除API配置: {}", id);
        ApiConfig config = apiConfigMapper.selectById(id);
        apiConfigMapper.deleteById(id);
        if (config != null) {
            invalidationService.invalidate(config.getId());
        }
    }

    /**
     * 设置默认配置
     */
    @Transactional
    public void setDefault(Long id) {
        log.info("设置默认配置: {}", id);

        ApiConfig config = apiConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("配置不存在");
        }

        // 重置所有配置
        apiConfigMapper.resetAllDefault();

        // 设置新的默认配置
        config.setIsDefault(true);
        apiConfigMapper.updateById(config);
    }

    private static ApiConfig copyConfig(ApiConfig source) {
        ApiConfig copy = new ApiConfig();
        copy.setId(source.getId());
        copy.setProviderType(source.getProviderType());
        copy.setBaseUrl(source.getBaseUrl());
        copy.setApiKey(source.getApiKey());
        copy.setApiKeyEncrypted(source.getApiKeyEncrypted());
        copy.setModelName(source.getModelName());
        copy.setTemperature(source.getTemperature());
        copy.setMaxTokens(source.getMaxTokens());
        copy.setTimeoutSeconds(source.getTimeoutSeconds());
        copy.setModelParametersJson(source.getModelParametersJson());
        return copy;
    }
}
