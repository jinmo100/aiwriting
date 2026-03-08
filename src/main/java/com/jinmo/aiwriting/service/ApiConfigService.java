package com.jinmo.aiwriting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jinmo.aiwriting.common.exception.BusinessException;
import com.jinmo.aiwriting.domain.dto.ApiConfigRequest;
import com.jinmo.aiwriting.domain.dto.ApiConfigResponse;
import com.jinmo.aiwriting.domain.entity.ApiConfig;
import com.jinmo.aiwriting.mapper.ApiConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 创建配置
     */
    @Transactional
    public ApiConfigResponse createConfig(ApiConfigRequest request) {
        log.info("创建API配置: {}", request.configName());

        ApiConfig config = new ApiConfig();
        config.setConfigName(request.configName());
        config.setProvider(request.provider());
        config.setBaseUrl(request.baseUrl());
        config.setApiKey(request.apiKey());
        config.setModelName(request.modelName());
        config.setIsDefault(request.isDefault() != null ? request.isDefault() : false);

        // 如果设置为默认，先重置其他配置
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            apiConfigMapper.resetAllDefault();
        }

        apiConfigMapper.insert(config);
        return ApiConfigResponse.fromEntity(config);
    }

    /**
     * 获取所有配置
     */
    public List<ApiConfigResponse> getAllConfigs() {
        return apiConfigMapper.selectList(
            new LambdaQueryWrapper<ApiConfig>()
                .orderByDesc(ApiConfig::getCreatedAt)
        ).stream()
            .map(ApiConfigResponse::fromEntity)
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
        return ApiConfigResponse.fromEntity(config);
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
    public ApiConfigResponse updateConfig(Long id, ApiConfigRequest request) {
        log.info("更新API配置: {}", id);

        ApiConfig config = apiConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("配置不存在");
        }

        config.setConfigName(request.configName());
        config.setProvider(request.provider());
        config.setBaseUrl(request.baseUrl());
        config.setApiKey(request.apiKey());
        config.setModelName(request.modelName());

        if (request.isDefault() != null) {
            if (Boolean.TRUE.equals(request.isDefault())) {
                apiConfigMapper.resetAllDefault();
            }
            config.setIsDefault(request.isDefault());
        }

        apiConfigMapper.updateById(config);
        return ApiConfigResponse.fromEntity(config);
    }

    /**
     * 删除配置
     */
    @Transactional
    public void deleteConfig(Long id) {
        log.info("删除API配置: {}", id);
        apiConfigMapper.deleteById(id);
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
}
