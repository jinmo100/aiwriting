package com.jinmo.aiwriting.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.aiwriting.common.response.ApiResponse;
import com.jinmo.aiwriting.domain.dto.EssayScoreResponse;
import com.jinmo.aiwriting.domain.dto.EssaySubmitRequest;
import com.jinmo.aiwriting.domain.entity.Essay;
import com.jinmo.aiwriting.domain.entity.EssayScore;
import com.jinmo.aiwriting.service.EssayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 作文控制器
 */
@Tag(name = "作文评分", description = "作文提交和评分相关接口")
@RestController
@RequestMapping("/api/essays")
@RequiredArgsConstructor
public class EssayController {

    private final EssayService essayService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "提交作文并评分")
    @PostMapping("/submit")
    public ApiResponse<EssayScoreResponse> submitEssay(@Valid @RequestBody EssaySubmitRequest request) {
        return ApiResponse.success("评分成功", essayService.submitAndScore(request));
    }

    @Operation(summary = "获取历史记录")
    @GetMapping("/history")
    public ApiResponse<Map<String, Object>> getHistory(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        Page<Essay> pageResult = essayService.getHistory(page, size);

        Map<String, Object> result = new HashMap<>();
        result.put("content", pageResult.getRecords());
        result.put("totalElements", pageResult.getTotal());
        result.put("totalPages", pageResult.getPages());
        result.put("currentPage", pageResult.getCurrent());

        return ApiResponse.success(result);
    }

    @Operation(summary = "获取作文详情和评分")
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getEssayDetail(@PathVariable Long id) {
        Essay essay = essayService.getEssay(id);
        EssayScore score = essayService.getScore(id);

        Map<String, Object> result = new HashMap<>();
        result.put("essay", essay);
        result.put("score", buildScoreDetail(score));

        return ApiResponse.success(result);
    }

    /**
     * 构建评分详情
     */
    private Map<String, Object> buildScoreDetail(EssayScore score) {
        if (score == null) {
            return null;
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("overallScore", score.getOverallScore());
        detail.put("contentScore", score.getContentScore());
        detail.put("languageScore", score.getLanguageScore());
        detail.put("structureScore", score.getStructureScore());
        detail.put("coherenceScore", score.getCoherenceScore());
        detail.put("detailedFeedback", score.getDetailedFeedback());
        detail.put("aiModel", score.getAiModel());
        detail.put("processingTime", score.getProcessingTime());

        try {
            detail.put("strengths", objectMapper.readValue(
                score.getStrengths(),
                new TypeReference<List<String>>() {}
            ));
            detail.put("suggestions", objectMapper.readValue(
                score.getSuggestions(),
                new TypeReference<List<String>>() {}
            ));
            detail.put("errors", objectMapper.readValue(
                score.getErrors(),
                new TypeReference<List<Map<String, String>>>() {}
            ));
        } catch (JsonProcessingException e) {
            detail.put("strengths", List.of());
            detail.put("suggestions", List.of());
            detail.put("errors", List.of());
        }

        return detail;
    }
}
