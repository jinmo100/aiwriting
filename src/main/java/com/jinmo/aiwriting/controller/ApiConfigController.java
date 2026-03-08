package com.jinmo.aiwriting.controller;

import com.jinmo.aiwriting.common.response.ApiResponse;
import com.jinmo.aiwriting.domain.dto.ApiConfigRequest;
import com.jinmo.aiwriting.domain.dto.ApiConfigResponse;
import com.jinmo.aiwriting.service.ApiConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API配置控制器
 */
@Tag(name = "API配置管理", description = "管理AI模型的API配置")
@RestController
@RequestMapping("/api/configs")
@RequiredArgsConstructor
public class ApiConfigController {

    private final ApiConfigService apiConfigService;

    @Operation(summary = "创建API配置")
    @PostMapping
    public ApiResponse<ApiConfigResponse> createConfig(@Valid @RequestBody ApiConfigRequest request) {
        return ApiResponse.success("创建成功", apiConfigService.createConfig(request));
    }

    @Operation(summary = "获取所有配置")
    @GetMapping
    public ApiResponse<List<ApiConfigResponse>> getAllConfigs() {
        return ApiResponse.success(apiConfigService.getAllConfigs());
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
        @Valid @RequestBody ApiConfigRequest request
    ) {
        return ApiResponse.success("更新成功", apiConfigService.updateConfig(id, request));
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
