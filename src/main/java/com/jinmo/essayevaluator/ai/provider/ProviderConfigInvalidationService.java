package com.jinmo.essayevaluator.ai.provider;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

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
    private static final ChannelTopic TOPIC = new ChannelTopic(CHANNEL);

    private final LangChainChatModelFactory chatModelFactory;
    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final MessageListener listener = this::onMessage;

    @PostConstruct
    public void startSubscriber() {
        try {
            redisMessageListenerContainer.addMessageListener(listener, TOPIC);
            log.info("已注册 Provider 配置缓存失效频道订阅: {}", CHANNEL);
        } catch (Exception e) {
            log.warn("注册 Provider 配置缓存失效频道订阅失败，本地缓存仍会按 TTL 兜底: {}", e.getMessage());
        }
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

    @Override
    public void destroy() {
        try {
            redisMessageListenerContainer.removeMessageListener(listener, TOPIC);
        } catch (Exception e) {
            log.debug("移除 Provider 配置缓存失效频道订阅时忽略异常: {}", e.getMessage());
        }
    }
}
