package com.jinmo.essayevaluator.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jinmo.essayevaluator.common.response.ApiResponse;
import com.jinmo.essayevaluator.domain.dto.EssayHistoryItem;
import com.jinmo.essayevaluator.domain.dto.EssayScoreResponse;
import com.jinmo.essayevaluator.domain.dto.EssaySubmitRequest;
import com.jinmo.essayevaluator.service.CurrentUserService;
import com.jinmo.essayevaluator.service.EssayService;
import com.jinmo.essayevaluator.service.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.time.Duration;

@Tag(name = "作文评分", description = "作文提交和评分相关接口")
@RestController
@RequestMapping("/api/essays")
@RequiredArgsConstructor
public class EssayController {

    private final EssayService essayService;
    private final RateLimitService rateLimitService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "提交作文并异步评分")
    @PostMapping("/submit")
    public ApiResponse<EssayScoreResponse> submitEssay(@Valid @RequestBody EssaySubmitRequest request) {
        rateLimitService.check("essay:submit:user:" + currentUserService.requireUserId(), 5, Duration.ofMinutes(1));
        return ApiResponse.success("评分任务已提交", essayService.submitAndScore(request));
    }

    @Operation(summary = "重试失败的评分任务")
    @PostMapping("/{id}/retry")
    public ApiResponse<EssayScoreResponse> retryEssay(@PathVariable Long id) {
        rateLimitService.check("essay:retry:user:" + currentUserService.requireUserId(), 5, Duration.ofMinutes(1));
        return ApiResponse.success("评分任务已重新提交", essayService.retryScoring(id));
    }

    @Operation(summary = "获取历史记录")
    @GetMapping("/history")
    public ApiResponse<Map<String, Object>> getHistory(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        Page<EssayHistoryItem> pageResult = essayService.getHistory(page, size);

        Map<String, Object> result = new HashMap<>();
        result.put("content", pageResult.getRecords());
        result.put("totalElements", pageResult.getTotal());
        result.put("totalPages", pageResult.getPages());
        result.put("currentPage", pageResult.getCurrent());

        return ApiResponse.success(result);
    }

    @Operation(summary = "获取作文详情和评分")
    @GetMapping("/{id}")
    public ApiResponse<EssayScoreResponse> getEssayDetail(@PathVariable Long id) {
        return ApiResponse.success(essayService.getEssayScoreResponse(id));
    }
}
