package com.jinmo.essayevaluator.rag;

import com.jinmo.essayevaluator.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.jinmo.essayevaluator.rag.RagIndexDtos.RagIndexRebuildRequest;
import static com.jinmo.essayevaluator.rag.RagIndexDtos.RagIndexStatusResponse;

/**
 * 用户 RAG 索引接口。
 */
@Tag(name = "RAG 知识索引", description = "用户触发和查看自己的知识索引任务")
@RestController
@RequestMapping("/api/rag/index")
@RequiredArgsConstructor
public class RagIndexController {

    private final RagIndexService ragIndexService;

    @Operation(summary = "查询我的 RAG 知识索引状态")
    @GetMapping("/my-status")
    public ApiResponse<RagIndexStatusResponse> myStatus(@RequestParam(required = false) Long embeddingConfigId) {
        return ApiResponse.success(ragIndexService.myStatus(embeddingConfigId));
    }

    @Operation(summary = "构建我的 RAG 知识索引")
    @PostMapping("/rebuild-my")
    public ApiResponse<RagIndexStatusResponse> rebuildMy(@RequestBody(required = false) RagIndexRebuildRequest request) {
        return ApiResponse.success("索引任务已提交", ragIndexService.rebuildMy(request));
    }
}
