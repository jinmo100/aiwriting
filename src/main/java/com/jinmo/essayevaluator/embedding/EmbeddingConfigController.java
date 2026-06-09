package com.jinmo.essayevaluator.embedding;

import com.jinmo.essayevaluator.common.response.ApiResponse;
import com.jinmo.essayevaluator.service.CurrentUserService;
import com.jinmo.essayevaluator.service.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

import static com.jinmo.essayevaluator.embedding.EmbeddingRequestDtos.EmbeddingConfigCreateRequest;
import static com.jinmo.essayevaluator.embedding.EmbeddingRequestDtos.EmbeddingConfigTestRequest;
import static com.jinmo.essayevaluator.embedding.EmbeddingRequestDtos.EmbeddingConfigUpdateRequest;
import static com.jinmo.essayevaluator.embedding.EmbeddingResponseDtos.EmbeddingConfigResponse;
import static com.jinmo.essayevaluator.embedding.EmbeddingResponseDtos.EmbeddingTestResponse;

/**
 * Embedding 配置管理接口。
 */
@Tag(name = "Embedding 配置管理", description = "管理用户自己的 Embedding Provider 配置")
@RestController
@RequestMapping("/api/embedding-configs")
@RequiredArgsConstructor
public class EmbeddingConfigController {

    private final EmbeddingConfigService embeddingConfigService;
    private final RateLimitService rateLimitService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "创建 Embedding 配置")
    @PostMapping
    public ApiResponse<EmbeddingConfigResponse> createConfig(@Valid @RequestBody EmbeddingConfigCreateRequest request) {
        return ApiResponse.success("创建成功", embeddingConfigService.createConfig(request));
    }

    @Operation(summary = "获取 Embedding 配置列表")
    @GetMapping
    public ApiResponse<List<EmbeddingConfigResponse>> listConfigs() {
        return ApiResponse.success(embeddingConfigService.getAllConfigs());
    }

    @Operation(summary = "获取 Embedding 配置详情")
    @GetMapping("/{id}")
    public ApiResponse<EmbeddingConfigResponse> getConfig(@PathVariable Long id) {
        return ApiResponse.success(embeddingConfigService.getConfig(id));
    }

    @Operation(summary = "更新 Embedding 配置")
    @PutMapping("/{id}")
    public ApiResponse<EmbeddingConfigResponse> updateConfig(
        @PathVariable Long id,
        @Valid @RequestBody EmbeddingConfigUpdateRequest request
    ) {
        return ApiResponse.success("更新成功", embeddingConfigService.updateConfig(id, request));
    }

    @Operation(summary = "删除 Embedding 配置")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteConfig(@PathVariable Long id) {
        embeddingConfigService.deleteConfig(id);
        return ApiResponse.success("删除成功", null);
    }

    @Operation(summary = "设置默认 Embedding 配置")
    @PutMapping("/{id}/default")
    public ApiResponse<Void> setDefault(@PathVariable Long id) {
        embeddingConfigService.setDefault(id);
        return ApiResponse.success("设置成功", null);
    }

    @Operation(summary = "使用未保存配置测试 Embedding 连接")
    @PostMapping("/test")
    public ApiResponse<EmbeddingTestResponse> testUnsavedConfig(@Valid @RequestBody EmbeddingConfigTestRequest request) {
        rateLimitService.check("embedding:test:user:" + currentUserService.requireUserId(), 10, Duration.ofMinutes(1));
        return ApiResponse.success(embeddingConfigService.testConnection(request));
    }

    @Operation(summary = "使用已保存配置测试 Embedding 连接")
    @PostMapping("/{id}/test")
    public ApiResponse<EmbeddingTestResponse> testSavedConfig(@PathVariable Long id) {
        rateLimitService.check("embedding:test:user:" + currentUserService.requireUserId(), 10, Duration.ofMinutes(1));
        return ApiResponse.success(embeddingConfigService.testConnection(id));
    }
}
