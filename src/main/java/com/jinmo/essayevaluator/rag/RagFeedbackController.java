package com.jinmo.essayevaluator.rag;

import com.jinmo.essayevaluator.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.jinmo.essayevaluator.rag.RagFeedbackDtos.RagFeedbackGenerateRequest;
import static com.jinmo.essayevaluator.rag.RagFeedbackDtos.RagFeedbackResponse;

/**
 * RAG Feedback 用户接口。
 */
@Tag(name = "RAG 知识点增强反馈", description = "查询、生成和重试作文的 RAG 增强反馈")
@RestController
@RequestMapping("/api/rag/feedbacks")
@RequiredArgsConstructor
public class RagFeedbackController {

    private final RagFeedbackService ragFeedbackService;

    @Operation(summary = "查询作文 RAG Feedback")
    @GetMapping("/{essayId}")
    public ApiResponse<RagFeedbackResponse> getFeedback(@PathVariable Long essayId) {
        return ApiResponse.success(ragFeedbackService.getFeedback(essayId));
    }

    @Operation(summary = "生成作文 RAG Feedback")
    @PostMapping("/{essayId}/generate")
    public ApiResponse<RagFeedbackResponse> generate(
        @PathVariable Long essayId,
        @RequestBody(required = false) RagFeedbackGenerateRequest request
    ) {
        return ApiResponse.success("知识点增强反馈任务已提交", ragFeedbackService.generate(essayId, request));
    }

    @Operation(summary = "重试作文 RAG Feedback")
    @PostMapping("/{essayId}/retry")
    public ApiResponse<RagFeedbackResponse> retry(
        @PathVariable Long essayId,
        @RequestBody(required = false) RagFeedbackGenerateRequest request
    ) {
        return ApiResponse.success("知识点增强反馈任务已重试", ragFeedbackService.retry(essayId, request));
    }
}
