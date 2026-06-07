package com.jinmo.essayevaluator.controller;

import com.jinmo.essayevaluator.common.response.ApiResponse;
import com.jinmo.essayevaluator.common.exception.BusinessException;
import com.jinmo.essayevaluator.domain.dto.ApiConfigCreateRequest;
import com.jinmo.essayevaluator.domain.dto.ApiConfigResponse;
import com.jinmo.essayevaluator.domain.dto.ApiConfigUpdateRequest;
import com.jinmo.essayevaluator.domain.dto.ProviderModelsFetchRequest;
import com.jinmo.essayevaluator.domain.dto.ProviderModelsResponse;
import com.jinmo.essayevaluator.domain.dto.ProviderTestRequest;
import com.jinmo.essayevaluator.domain.dto.ProviderTestResponse;
import com.jinmo.essayevaluator.service.ApiConfigService;
import com.jinmo.essayevaluator.service.ProviderModelService;
import com.jinmo.essayevaluator.service.ProviderTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API配置控制器
 */
@Tag(name = "API配置管理", description = "管理AI模型的API配置")
@RestController
@RequestMapping("/api/configs")
@RequiredArgsConstructor
public class ApiConfigController {

    private final ApiConfigService apiConfigService;
    private final ProviderModelService providerModelService;
    private final ProviderTestService providerTestService;

    @Value("${essay-evaluator.security.allow-api-key-reveal:false}")
    private boolean allowApiKeyReveal;

    @Operation(summary = "创建API配置")
    @PostMapping
    public ApiResponse<ApiConfigResponse> createConfig(@Valid @RequestBody ApiConfigCreateRequest request) {
        return ApiResponse.success("创建成功", apiConfigService.createConfig(request));
    }

    @Operation(summary = "获取所有配置")
    @GetMapping
    public ApiResponse<List<ApiConfigResponse>> getAllConfigs() {
        return ApiResponse.success(apiConfigService.getAllConfigs());
    }

    @Operation(summary = "获取配置页安全策略")
    @GetMapping("/security-policy")
    public ApiResponse<Map<String, Boolean>> getSecurityPolicy() {
        return ApiResponse.success(Map.of("allowApiKeyReveal", allowApiKeyReveal));
    }

    @Operation(summary = "获取配置详情")
    @GetMapping("/{id}")
    public ApiResponse<ApiConfigResponse> getConfig(@PathVariable Long id) {
        return ApiResponse.success(apiConfigService.getConfig(id));
    }

    @Operation(summary = "更新配置")
    @PutMapping("/{id}")
    public ApiResponse<ApiConfigResponse> updateConfig(
        @PathVariable Long id,
        @Valid @RequestBody ApiConfigUpdateRequest request
    ) {
        return ApiResponse.success("更新成功", apiConfigService.updateConfig(id, request));
    }

    @Operation(summary = "显示完整 API Key（仅允许环境）")
    @PostMapping("/{id}/reveal-api-key")
    public ApiResponse<Map<String, String>> revealApiKey(@PathVariable Long id) {
        if (!allowApiKeyReveal) {
            throw new BusinessException("当前环境不允许查看完整 API Key");
        }
        return ApiResponse.success(Map.of("apiKey", apiConfigService.revealApiKey(id)));
    }

    @Operation(summary = "使用未保存配置获取模型列表")
    @PostMapping("/models/fetch")
    public ApiResponse<ProviderModelsResponse> fetchModels(@Valid @RequestBody ProviderModelsFetchRequest request) {
        return ApiResponse.success(providerModelService.fetchModels(request));
    }

    @Operation(summary = "使用已保存配置获取模型列表")
    @PostMapping("/{id}/models/fetch")
    public ApiResponse<ProviderModelsResponse> fetchModelsByConfig(
        @PathVariable Long id,
        @RequestParam(defaultValue = "false") boolean forceRefresh
    ) {
        return ApiResponse.success(providerModelService.fetchModels(id, forceRefresh));
    }

    @Operation(summary = "使用未保存配置测试连接")
    @PostMapping("/test-connection")
    public ApiResponse<ProviderTestResponse> testConnection(@Valid @RequestBody ProviderTestRequest request) {
        return ApiResponse.success(providerTestService.testConnection(request));
    }

    @Operation(summary = "使用已保存配置测试连接")
    @PostMapping("/{id}/test-connection")
    public ApiResponse<ProviderTestResponse> testConnectionByConfig(@PathVariable Long id) {
        return ApiResponse.success(providerTestService.testConnection(id));
    }

    @Operation(summary = "使用未保存配置测试结构化输出")
    @PostMapping("/test-structured-output")
    public ApiResponse<ProviderTestResponse> testStructuredOutput(@Valid @RequestBody ProviderTestRequest request) {
        return ApiResponse.success(providerTestService.testStructuredOutput(request));
    }

    @Operation(summary = "使用已保存配置测试结构化输出")
    @PostMapping("/{id}/test-structured-output")
    public ApiResponse<ProviderTestResponse> testStructuredOutputByConfig(@PathVariable Long id) {
        return ApiResponse.success(providerTestService.testStructuredOutput(id));
    }

    @Operation(summary = "删除配置")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteConfig(@PathVariable Long id) {
        apiConfigService.deleteConfig(id);
        return ApiResponse.success("删除成功", null);
    }

    @Operation(summary = "设置为默认配置")
    @PutMapping("/{id}/default")
    public ApiResponse<Void> setDefault(@PathVariable Long id) {
        apiConfigService.setDefault(id);
        return ApiResponse.success("设置成功", null);
    }
}
