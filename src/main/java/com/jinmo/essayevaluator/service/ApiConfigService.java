package com.jinmo.essayevaluator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jinmo.essayevaluator.ai.provider.ProviderConfigInvalidationService;
import com.jinmo.essayevaluator.ai.provider.ProviderEndpointResolver;
import com.jinmo.essayevaluator.ai.provider.ProviderType;
import com.jinmo.essayevaluator.common.exception.BusinessException;
import com.jinmo.essayevaluator.domain.dto.ApiConfigCreateRequest;
import com.jinmo.essayevaluator.domain.dto.ApiConfigResponse;
import com.jinmo.essayevaluator.domain.dto.ApiConfigUpdateRequest;
import com.jinmo.essayevaluator.domain.entity.ApiConfig;
import com.jinmo.essayevaluator.mapper.ApiConfigMapper;
import com.jinmo.essayevaluator.security.ApiKeyEncryptionService;
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
    private final CurrentUserService currentUserService;

    /**
     * 创建配置
     */
    @Transactional
    public ApiConfigResponse createConfig(ApiConfigCreateRequest request) {
        log.info("创建API配置: {}", request.configName());
        Long userId = currentUserService.requireUserId();

        ApiConfig config = new ApiConfig();
        config.setOwnerUserId(userId);
        config.setVisibility("PRIVATE");
        config.setAllowPublicUse(false);
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
        config.setInputTokenPricePerMillion(request.inputTokenPricePerMillion());
        config.setOutputTokenPricePerMillion(request.outputTokenPricePerMillion());
        config.setCurrency(normalizeCurrency(request.currency()));
        config.setIsDefault(request.isDefault() != null ? request.isDefault() : false);

        // 如果设置为默认，先重置其他配置
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            apiConfigMapper.resetDefaultForOwner(userId);
        }

        apiConfigMapper.insert(config);
        return toResponse(config);
    }

    /**
     * 获取所有配置
     */
    public List<ApiConfigResponse> getAllConfigs() {
        Long userId = currentUserService.requireUserId();
        return apiConfigMapper.selectList(
            new LambdaQueryWrapper<ApiConfig>()
                .and(wrapper -> wrapper
                    .eq(ApiConfig::getOwnerUserId, userId)
                    .eq(ApiConfig::getVisibility, "PRIVATE")
                    .or()
                    .eq(ApiConfig::getVisibility, "PUBLIC")
                    .eq(ApiConfig::getAllowPublicUse, true)
                )
                .orderByDesc(ApiConfig::getCreatedAt)
        ).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /**
     * 获取配置详情
     */
    public ApiConfigResponse getConfig(Long id) {
        ApiConfig config = loadVisibleConfig(id);
        if (config == null) {
            throw new BusinessException("配置不存在");
        }
        return toResponse(config);
    }

    /**
     * 获取默认配置
     */
    public ApiConfig getDefaultConfig() {
        Long userId = currentUserService.requireUserId();
        return apiConfigMapper.selectOne(
            new LambdaQueryWrapper<ApiConfig>()
                .eq(ApiConfig::getOwnerUserId, userId)
                .eq(ApiConfig::getVisibility, "PRIVATE")
                .eq(ApiConfig::getIsDefault, true)
                .last("LIMIT 1")
        );
    }

    /**
     * 更新配置
     */
    @Transactional
    public ApiConfigResponse updateConfig(Long id, ApiConfigUpdateRequest request) {
        log.info("更新API配置: {}", id);

        ApiConfig config = loadOwnedConfig(id);
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
        config.setInputTokenPricePerMillion(request.inputTokenPricePerMillion());
        config.setOutputTokenPricePerMillion(request.outputTokenPricePerMillion());
        config.setCurrency(normalizeCurrency(request.currency()));

        if (request.isDefault() != null) {
            if (Boolean.TRUE.equals(request.isDefault())) {
                apiConfigMapper.resetDefaultForOwner(config.getOwnerUserId());
            }
            config.setIsDefault(request.isDefault());
        }

        apiConfigMapper.updateById(config);
        invalidationService.invalidate(oldConfig.getId());
        return toResponse(config);
    }

    public String revealApiKey(Long id) {
        ApiConfig config = loadOwnedConfig(id);
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

    private static String normalizeCurrency(String currency) {
        return StringUtils.hasText(currency) ? currency.trim().toUpperCase() : null;
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
        ApiConfig config = loadOwnedConfig(id);
        apiConfigMapper.deleteById(id);
        invalidationService.invalidate(config.getId());
    }

    /**
     * 设置默认配置
     */
    @Transactional
    public void setDefault(Long id) {
        log.info("设置默认配置: {}", id);

        ApiConfig config = loadOwnedConfig(id);

        // 只重置当前用户自己的默认配置。
        apiConfigMapper.resetDefaultForOwner(config.getOwnerUserId());

        // 设置新的默认配置
        config.setIsDefault(true);
        apiConfigMapper.updateById(config);
    }

    private static ApiConfig copyConfig(ApiConfig source) {
        ApiConfig copy = new ApiConfig();
        copy.setId(source.getId());
        copy.setOwnerUserId(source.getOwnerUserId());
        copy.setVisibility(source.getVisibility());
        copy.setAllowPublicUse(source.getAllowPublicUse());
        copy.setProviderType(source.getProviderType());
        copy.setBaseUrl(source.getBaseUrl());
        copy.setApiKey(source.getApiKey());
        copy.setApiKeyEncrypted(source.getApiKeyEncrypted());
        copy.setModelName(source.getModelName());
        copy.setTemperature(source.getTemperature());
        copy.setMaxTokens(source.getMaxTokens());
        copy.setTimeoutSeconds(source.getTimeoutSeconds());
        copy.setModelParametersJson(source.getModelParametersJson());
        copy.setInputTokenPricePerMillion(source.getInputTokenPricePerMillion());
        copy.setOutputTokenPricePerMillion(source.getOutputTokenPricePerMillion());
        copy.setCurrency(source.getCurrency());
        return copy;
    }

    public ApiConfig loadUsableConfig(Long configId) {
        ApiConfig config = loadVisibleConfig(configId);
        if (config == null) {
            throw new BusinessException("指定的配置不存在");
        }
        return config;
    }

    private ApiConfig loadOwnedConfig(Long id) {
        Long userId = currentUserService.requireUserId();
        ApiConfig config = apiConfigMapper.selectOne(
            new LambdaQueryWrapper<ApiConfig>()
                .eq(ApiConfig::getId, id)
                .eq(ApiConfig::getOwnerUserId, userId)
                .eq(ApiConfig::getVisibility, "PRIVATE")
                .last("LIMIT 1")
        );
        if (config == null) {
            throw new BusinessException("配置不存在");
        }
        return config;
    }

    private ApiConfig loadVisibleConfig(Long id) {
        Long userId = currentUserService.requireUserId();
        return apiConfigMapper.selectOne(
            new LambdaQueryWrapper<ApiConfig>()
                .eq(ApiConfig::getId, id)
                .and(wrapper -> wrapper
                    .eq(ApiConfig::getOwnerUserId, userId)
                    .eq(ApiConfig::getVisibility, "PRIVATE")
                    .or()
                    .eq(ApiConfig::getVisibility, "PUBLIC")
                    .eq(ApiConfig::getAllowPublicUse, true)
                )
                .last("LIMIT 1")
        );
    }
}
