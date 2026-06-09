package com.jinmo.essayevaluator.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 后台任务分发器。
 *
 * <p>分发前必须先 claim 数据库任务，claim 成功后才允许执行业务 handler。这样即使未来部署多实例，
 * 也能依赖数据库锁字段避免同一个 PENDING 任务被并发执行。</p>
 */
@Slf4j
@Service
public class BackgroundJobDispatcher {

    private static final Duration DEFAULT_LOCK_TTL = Duration.ofMinutes(10);

    private final BackgroundJobService backgroundJobService;
    private final TaskExecutor taskExecutor;
    private final Map<BackgroundJobType, BackgroundJobHandler> handlers;
    private final String workerId;

    public BackgroundJobDispatcher(
        BackgroundJobService backgroundJobService,
        List<BackgroundJobHandler> handlerList,
        @Qualifier("backgroundJobTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.backgroundJobService = backgroundJobService;
        this.taskExecutor = taskExecutor;
        this.handlers = buildHandlerMap(handlerList);
        this.workerId = ManagementFactory.getRuntimeMXBean().getName() + ":" + UUID.randomUUID();
    }

    public void dispatchAsync(Long jobId) {
        taskExecutor.execute(() -> dispatch(jobId));
    }

    public void dispatch(Long jobId) {
        Optional<BackgroundJob> claimed = backgroundJobService.claimRunnableJob(jobId, workerId, DEFAULT_LOCK_TTL);
        if (claimed.isEmpty()) {
            log.debug("后台任务未被领取，可能尚未到期或已被其他执行器处理: jobId={}", jobId);
            return;
        }

        BackgroundJob job = claimed.get();
        BackgroundJobHandler handler = handlers.get(job.getJobType());
        if (handler == null) {
            backgroundJobService.markFailed(job.getId(), "JOB_HANDLER_NOT_FOUND", "后台任务类型暂未启用，请稍后重试");
            return;
        }

        try {
            BackgroundJobHandler.JobResult result = handler.handle(job);
            if (result == null || result.status() == null || result.status() == BackgroundJobStatus.COMPLETED) {
                backgroundJobService.markCompleted(job.getId(), result == null ? null : result.result());
                return;
            }
            if (result.status() == BackgroundJobStatus.SKIPPED) {
                backgroundJobService.markSkipped(job.getId(), result.result());
                return;
            }
            backgroundJobService.markFailed(job.getId(), "JOB_HANDLER_INVALID_RESULT", "后台任务处理结果无效，请稍后重试");
        } catch (Exception error) {
            log.warn("后台任务执行失败: jobId={}, jobType={}", job.getId(), job.getJobType(), error);
            backgroundJobService.markFailed(job.getId(), "JOB_HANDLER_FAILED", "后台任务执行失败，请稍后重试");
        }
    }

    private Map<BackgroundJobType, BackgroundJobHandler> buildHandlerMap(List<BackgroundJobHandler> handlerList) {
        Map<BackgroundJobType, BackgroundJobHandler> result = new EnumMap<>(BackgroundJobType.class);
        for (BackgroundJobHandler handler : handlerList) {
            BackgroundJobHandler previous = result.put(handler.jobType(), handler);
            if (previous != null) {
                throw new IllegalStateException("重复的后台任务处理器: " + handler.jobType());
            }
        }
        return Map.copyOf(result);
    }
}
