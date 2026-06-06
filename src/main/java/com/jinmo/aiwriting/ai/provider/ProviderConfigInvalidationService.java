package com.jinmo.aiwriting.ai.provider;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Provider 配置缓存失效协调。
 *
 * <p>本地 JVM 立即按 configId 清理 Caffeine；同时向 Redis 发布失效消息。
 * 其他 JVM 通过后台订阅收到消息后清理自己的本地缓存。Redis 不可用时不阻塞主流程，
 * Caffeine TTL 仍作为兜底。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderConfigInvalidationService implements DisposableBean {

    private static final String CHANNEL = "provider-config-invalidated";
    private static final Duration RETRY_DELAY = Duration.ofSeconds(10);

    private final LangChainChatModelFactory chatModelFactory;
    private final StringRedisTemplate redisTemplate;

    private final ExecutorService subscriberExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "provider-config-invalidation-subscriber");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean running = true;

    @PostConstruct
    public void startSubscriber() {
        subscriberExecutor.submit(this::subscribeLoop);
    }

    public void invalidate(Long configId) {
        if (configId == null) {
            return;
        }
        chatModelFactory.invalidateByConfigId(configId);
        try {
            redisTemplate.convertAndSend(CHANNEL, String.valueOf(configId));
        } catch (Exception e) {
            log.warn("发布 Provider 配置缓存失效消息失败，已完成本地失效: configId={}, error={}",
                configId, e.getMessage());
        }
    }

    private void subscribeLoop() {
        byte[] channel = CHANNEL.getBytes(StandardCharsets.UTF_8);
        MessageListener listener = this::onMessage;

        while (running) {
            try (var connection = redisTemplate.getRequiredConnectionFactory().getConnection()) {
                log.info("开始订阅 Provider 配置缓存失效频道: {}", CHANNEL);
                connection.subscribe(listener, channel);
            } catch (Exception e) {
                if (running) {
                    log.warn("Provider 配置缓存失效订阅不可用，将稍后重试: {}", e.getMessage());
                    sleepBeforeRetry();
                }
            }
        }
    }

    private void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        if (!StringUtils.hasText(body)) {
            return;
        }
        try {
            Long configId = Long.parseLong(body.trim());
            chatModelFactory.invalidateByConfigId(configId);
            log.debug("已处理 Provider 配置缓存失效消息: configId={}", configId);
        } catch (NumberFormatException e) {
            log.warn("忽略非法 Provider 配置缓存失效消息: {}", body);
        }
    }

    private void sleepBeforeRetry() {
        try {
            TimeUnit.MILLISECONDS.sleep(RETRY_DELAY.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    @Override
    public void destroy() {
        running = false;
        subscriberExecutor.shutdownNow();
    }
}
