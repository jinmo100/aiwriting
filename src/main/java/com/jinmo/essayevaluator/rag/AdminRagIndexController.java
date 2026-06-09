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

import static com.jinmo.essayevaluator.rag.RagIndexDtos.AdminRagIndexRebuildRequest;
import static com.jinmo.essayevaluator.rag.RagIndexDtos.RagIndexStatusResponse;

/**
 * 管理员 RAG 索引运维接口。
 */
@Tag(name = "管理员 RAG 索引运维", description = "管理员查看或代用户触发 RAG 知识索引")
@RestController
@RequestMapping("/api/admin/rag/index")
@RequiredArgsConstructor
public class AdminRagIndexController {

    private final RagIndexService ragIndexService;

    @Operation(summary = "管理员查询指定用户 RAG 索引状态")
    @GetMapping("/status")
    public ApiResponse<RagIndexStatusResponse> status(
        @RequestParam Long userId,
        @RequestParam(required = false) Long embeddingConfigId
    ) {
        return ApiResponse.success(ragIndexService.adminStatus(userId, embeddingConfigId));
    }

    @Operation(summary = "管理员代用户构建 RAG 知识索引")
    @PostMapping("/rebuild")
    public ApiResponse<RagIndexStatusResponse> rebuild(@RequestBody AdminRagIndexRebuildRequest request) {
        return ApiResponse.success("索引任务已提交", ragIndexService.rebuildForUser(request));
    }
}
