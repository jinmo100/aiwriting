package com.jinmo.essayevaluator.job;

/**
 * 后台任务处理器 SPI。
 *
 * <p>RAG 索引、RAG Feedback 等业务只需要实现自己的 handler，通用状态流转统一交给
 * {@link BackgroundJobDispatcher} 和 {@link BackgroundJobService}，避免各业务重复写失败/跳过逻辑。</p>
 */
public interface BackgroundJobHandler {

    BackgroundJobType jobType();

    JobResult handle(BackgroundJob job) throws Exception;

    record JobResult(BackgroundJobStatus status, Object result) {
        public static JobResult completed(Object result) {
            return new JobResult(BackgroundJobStatus.COMPLETED, result);
        }

        public static JobResult skipped(Object result) {
            return new JobResult(BackgroundJobStatus.SKIPPED, result);
        }
    }
}
