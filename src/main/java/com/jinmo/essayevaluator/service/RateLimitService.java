package com.jinmo.essayevaluator.service;

import com.jinmo.essayevaluator.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 基于 Redis 计数器的轻量限流。Redis 故障时默认 fail-open，避免认证链路被缓存故障拖死。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisOperations<String, String> redisOperations;

    public void check(String key, long maxRequests, Duration window) {
        try {
            Long count = redisOperations.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisOperations.expire(key, window);
            }
            if (count != null && count > maxRequests) {
                throw new BusinessException("操作过于频繁，请稍后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("限流检查失败，已放行: key={}, error={}", key, e.getMessage());
        }
    }
}
