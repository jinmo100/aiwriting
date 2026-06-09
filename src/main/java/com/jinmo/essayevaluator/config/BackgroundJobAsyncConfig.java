package com.jinmo.essayevaluator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * background_jobs 专用线程池。
 *
 * <p>RAG 索引和 RAG Feedback 生成可能包含外部 Embedding/Chat Provider 调用，必须与主评分
 * scoringTaskExecutor 隔离，避免后置增强任务拖慢作文评分主链路。</p>
 */
@Configuration
public class BackgroundJobAsyncConfig {

    @Bean("backgroundJobTaskExecutor")
    public TaskExecutor backgroundJobTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("background-job-");
        executor.initialize();
        return executor;
    }
}
