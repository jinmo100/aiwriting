package com.jinmo.essayevaluator.job;

import com.jinmo.essayevaluator.common.response.ApiResponse;
import com.jinmo.essayevaluator.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员后台任务查询接口。
 *
 * <p>只返回状态、错误摘要和安全 result，绝不返回 payloadJson，避免暴露 handler 输入中的内部 ID 组合
 * 或未来可能出现的敏感上下文。</p>
 */
@Tag(name = "后台任务管理", description = "管理员查看 background_jobs 安全状态摘要")
@RestController
@RequestMapping("/api/admin/jobs")
@RequiredArgsConstructor
public class BackgroundJobController {

    private final BackgroundJobService backgroundJobService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "查询后台任务状态")
    @GetMapping
    public ApiResponse<List<AdminJobResponse>> listJobs(
        @RequestParam(required = false) BackgroundJobType jobType,
        @RequestParam(required = false) BackgroundJobStatus status
    ) {
        currentUserService.requireAdmin();
        List<AdminJobResponse> jobs = backgroundJobService.listForAdmin(jobType, status)
            .stream()
            .map(AdminJobResponse::from)
            .toList();
        return ApiResponse.success(jobs);
    }

    public record AdminJobResponse(
        Long id,
        BackgroundJobType jobType,
        Long ownerUserId,
        Long requestedByUserId,
        BackgroundJobStatus status,
        String resultJson,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        static AdminJobResponse from(BackgroundJob job) {
            return new AdminJobResponse(
                job.getId(),
                job.getJobType(),
                job.getOwnerUserId(),
                job.getRequestedByUserId(),
                job.getStatus(),
                job.getResultJson(),
                job.getErrorCode(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt()
            );
        }
    }
}
