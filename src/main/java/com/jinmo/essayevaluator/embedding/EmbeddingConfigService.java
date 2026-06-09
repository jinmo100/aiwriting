package com.jinmo.essayevaluator.embedding;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jinmo.essayevaluator.ai.provider.AIProviderErrorCode;
import com.jinmo.essayevaluator.common.exception.BusinessException;
import com.jinmo.essayevaluator.mapper.EmbeddingConfigMapper;
import com.jinmo.essayevaluator.security.ApiKeyEncryptionService;
import com.jinmo.essayevaluator.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

import static com.jinmo.essayevaluator.embedding.EmbeddingRequestDtos.EmbeddingConfigCreateRequest;
import static com.jinmo.essayevaluator.embedding.EmbeddingRequestDtos.EmbeddingConfigTestRequest;
import static com.jinmo.essayevaluator.embedding.EmbeddingRequestDtos.EmbeddingConfigUpdateRequest;
import static com.jinmo.essayevaluator.embedding.EmbeddingResponseDtos.EmbeddingConfigResponse;
import static com.jinmo.essayevaluator.embedding.EmbeddingResponseDtos.EmbeddingTestResponse;

/**
 * Embedding 配置服务。
 *
 * <p>所有配置严格按当前用户隔离；API Key 使用既有 {@link ApiKeyEncryptionService} 加密保存，
 * 响应只返回 Key 状态和脱敏 preview。</p>
 */
@Service
@RequiredArgsConstructor
public class EmbeddingConfigService {

    private static final int SUPPORTED_DIMENSIONS = 1536;
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private final EmbeddingConfigMapper embeddingConfigMapper;
    private final CurrentUserService currentUserService;
    private final ApiKeyEncryptionService apiKeyEncryptionService;
    private final EmbeddingClient embeddingClient;

    @Transactional
    public EmbeddingConfigResponse createConfig(EmbeddingConfigCreateRequest request) {
        Long userId = currentUserService.requireUserId();
        EmbeddingProviderType providerType = parseProviderType(request.providerType());
        validateDimensions(request.dimensions());

        EmbeddingConfig config = new EmbeddingConfig();
        config.setOwnerUserId(userId);
        config.setConfigName(requireText(request.configName(), "配置名称不能为空"));
        config.setProviderType(providerType);
        config.setProviderLabel(providerType.value());
        config.setBaseUrl(OpenAiCompatibleEmbeddingClient.normalizeBaseUrl(request.baseUrl()));
        String plainApiKey = requireText(request.apiKey(), "Embedding API Key 不能为空");
        config.setApiKeyEncrypted(apiKeyEncryptionService.encrypt(plainApiKey));
        config.setModelName(requireText(request.modelName(), "Embedding 模型名称不能为空"));
        config.setDimensions(SUPPORTED_DIMENSIONS);
        config.setTimeoutSeconds(defaultTimeoutSeconds(request.timeoutSeconds()));
        config.setIsDefault(Boolean.TRUE.equals(request.isDefault()));

        if (Boolean.TRUE.equals(config.getIsDefault())) {
            embeddingConfigMapper.resetDefaultForOwner(userId);
        }

        embeddingConfigMapper.insert(config);
        return EmbeddingConfigResponse.fromEntity(config, plainApiKey);
    }

    @Transactional(readOnly = true)
    public List<EmbeddingConfigResponse> getAllConfigs() {
        Long userId = currentUserService.requireUserId();
        return embeddingConfigMapper.selectList(
            new LambdaQueryWrapper<EmbeddingConfig>()
                .eq(EmbeddingConfig::getOwnerUserId, userId)
                .orderByDesc(EmbeddingConfig::getCreatedAt)
        ).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EmbeddingConfigResponse getConfig(Long id) {
        return toResponse(loadOwnedConfig(id));
    }

    @Transactional
    public EmbeddingConfigResponse updateConfig(Long id, EmbeddingConfigUpdateRequest request) {
        EmbeddingConfig config = loadOwnedConfig(id);
        EmbeddingProviderType providerType = parseProviderType(request.providerType());
        validateDimensions(request.dimensions());

        config.setConfigName(requireText(request.configName(), "配置名称不能为空"));
        config.setProviderType(providerType);
        config.setProviderLabel(providerType.value());
        config.setBaseUrl(OpenAiCompatibleEmbeddingClient.normalizeBaseUrl(request.baseUrl()));
        if (StringUtils.hasText(request.apiKey())) {
            config.setApiKeyEncrypted(apiKeyEncryptionService.encrypt(request.apiKey().trim()));
        }
        config.setModelName(requireText(request.modelName(), "Embedding 模型名称不能为空"));
        config.setDimensions(SUPPORTED_DIMENSIONS);
        config.setTimeoutSeconds(defaultTimeoutSeconds(request.timeoutSeconds()));
        if (request.isDefault() != null) {
            if (Boolean.TRUE.equals(request.isDefault())) {
                embeddingConfigMapper.resetDefaultForOwner(config.getOwnerUserId());
            }
            config.setIsDefault(request.isDefault());
        }

        embeddingConfigMapper.updateById(config);
        return toResponse(config);
    }

    @Transactional
    public void deleteConfig(Long id) {
        EmbeddingConfig config = loadOwnedConfig(id);
        embeddingConfigMapper.deleteById(config.getId());
    }

    @Transactional
    public void setDefault(Long id) {
        EmbeddingConfig config = loadOwnedConfig(id);
        embeddingConfigMapper.resetDefaultForOwner(config.getOwnerUserId());
        config.setIsDefault(true);
        embeddingConfigMapper.updateById(config);
    }

    @Transactional(readOnly = true)
    public EmbeddingConfig getDefaultConfig() {
        Long userId = currentUserService.requireUserId();
        return embeddingConfigMapper.selectOne(
            new LambdaQueryWrapper<EmbeddingConfig>()
                .eq(EmbeddingConfig::getOwnerUserId, userId)
                .eq(EmbeddingConfig::getIsDefault, true)
                .last("LIMIT 1")
        );
    }

    @Transactional(readOnly = true)
    public EmbeddingConfig loadOwnedConfig(Long id) {
        Long userId = currentUserService.requireUserId();
        EmbeddingConfig config = embeddingConfigMapper.selectOne(
            new LambdaQueryWrapper<EmbeddingConfig>()
                .eq(EmbeddingConfig::getId, id)
                .eq(EmbeddingConfig::getOwnerUserId, userId)
                .last("LIMIT 1")
        );
        if (config == null) {
            throw new BusinessException("Embedding 配置不存在");
        }
        return config;
    }

    @Transactional(readOnly = true)
    public String resolvePlainApiKey(EmbeddingConfig config) {
        if (config == null || !StringUtils.hasText(config.getApiKeyEncrypted())) {
            throw new BusinessException("Embedding 配置未保存 API Key");
        }
        return apiKeyEncryptionService.decrypt(config.getApiKeyEncrypted());
    }

    public EmbeddingTestResponse testConnection(EmbeddingConfigTestRequest request) {
        validateDimensions(request.dimensions());
        EmbeddingConfig config = temporaryConfig(request);
        return doTestConnection(config, requireText(request.apiKey(), "Embedding API Key 不能为空"));
    }

    @Transactional
    public EmbeddingTestResponse testConnection(Long id) {
        EmbeddingConfig config = loadOwnedConfig(id);
        EmbeddingTestResponse response = doTestConnection(config, resolvePlainApiKey(config));
        saveTestSummary(config, response);
        return response;
    }

    private EmbeddingTestResponse doTestConnection(EmbeddingConfig config, String apiKey) {
        long start = System.currentTimeMillis();
        try {
            EmbeddingClient.EmbeddingResult result = embeddingClient.embed(config, apiKey, List.of("Embedding connection test"));
            return EmbeddingTestResponse.success(config, result.latencyMillis());
        } catch (EmbeddingClientException error) {
            return EmbeddingTestResponse.failure(
                config,
                System.currentTimeMillis() - start,
                error.getSafeMessage(),
                error.getErrorCode()
            );
        } catch (Exception error) {
            return EmbeddingTestResponse.failure(
                config,
                System.currentTimeMillis() - start,
                "Embedding 连接测试失败，请稍后重试",
                AIProviderErrorCode.UNKNOWN_ERROR
            );
        }
    }

    private void saveTestSummary(EmbeddingConfig config, EmbeddingTestResponse response) {
        EmbeddingConfig update = new EmbeddingConfig();
        update.setId(config.getId());
        update.setLastTestStatus(Boolean.TRUE.equals(response.success()) ? "SUCCESS" : "FAILED");
        update.setLastTestErrorCode(response.errorCode() != null ? response.errorCode().name() : null);
        update.setLastTestMessage(response.message());
        update.setLastTestLatencyMs(response.latencyMillis() != null ? response.latencyMillis().intValue() : null);
        update.setLastTestedAt(LocalDateTime.now());
        embeddingConfigMapper.updateById(update);
    }

    private EmbeddingConfig temporaryConfig(EmbeddingConfigTestRequest request) {
        EmbeddingProviderType providerType = parseProviderType(request.providerType());
        EmbeddingConfig config = new EmbeddingConfig();
        config.setProviderType(providerType);
        config.setProviderLabel(providerType.value());
        config.setBaseUrl(OpenAiCompatibleEmbeddingClient.normalizeBaseUrl(request.baseUrl()));
        config.setModelName(requireText(request.modelName(), "Embedding 模型名称不能为空"));
        config.setDimensions(SUPPORTED_DIMENSIONS);
        config.setTimeoutSeconds(defaultTimeoutSeconds(request.timeoutSeconds()));
        config.setIsDefault(false);
        return config;
    }

    private EmbeddingConfigResponse toResponse(EmbeddingConfig config) {
        String previewSource = null;
        try {
            previewSource = resolvePlainApiKey(config);
        } catch (Exception ignored) {
            // 旧数据或密钥解密失败时仍可返回配置主体，但不展示 preview。
        }
        return EmbeddingConfigResponse.fromEntity(config, previewSource);
    }

    private static EmbeddingProviderType parseProviderType(String providerType) {
        try {
            return EmbeddingProviderType.from(providerType);
        } catch (IllegalArgumentException error) {
            throw new BusinessException(error.getMessage());
        }
    }

    private static void validateDimensions(Integer dimensions) {
        if (dimensions == null || dimensions != SUPPORTED_DIMENSIONS) {
            throw new BusinessException("V1 仅支持 1536 维 Embedding");
        }
    }

    private static int defaultTimeoutSeconds(Integer timeoutSeconds) {
        return timeoutSeconds != null && timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }
}
