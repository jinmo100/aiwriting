package com.jinmo.essayevaluator.service.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.essayevaluator.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoringIdempotencyService {

    private static final String IDEMPOTENCY_PREFIX = "essay-evaluator:idempotency:";
    private static final String CONTENT_PREFIX = "essay-evaluator:content-submission:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${essay-evaluator.idempotency.redis-required:false}")
    private boolean redisRequired;

    public static String contentHash(String essayType, String taskPrompt, String content) {
        String canonical = normalize(essayType) + "\n" + normalize(taskPrompt) + "\n" + normalize(content);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public Optional<Long> findCachedEssayId(String idempotencyKey, String contentHash) {
        Optional<Long> byKey = readEssayId(idempotencyRedisKey(idempotencyKey));
        if (byKey.isPresent()) {
            return byKey;
        }
        return readEssayId(contentRedisKey(contentHash));
    }

    public void cacheScoring(String idempotencyKey, String contentHash, Long essayId) {
        cache(idempotencyKey, contentHash, essayId, "SCORING", Duration.ofMinutes(30), Duration.ofMinutes(10));
    }

    public void cacheCompleted(String idempotencyKey, String contentHash, Long essayId) {
        cache(idempotencyKey, contentHash, essayId, "COMPLETED", Duration.ofHours(24), Duration.ofHours(24));
    }

    public void cacheFailed(String idempotencyKey, String contentHash, Long essayId) {
        cache(idempotencyKey, contentHash, essayId, "FAILED", Duration.ofMinutes(30), Duration.ofMinutes(30));
    }

    private Optional<Long> readEssayId(String key) {
        if (!StringUtils.hasText(key)) {
            return Optional.empty();
        }
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(value)) {
                return Optional.empty();
            }
            IdempotencyCacheEntry entry = objectMapper.readValue(value, IdempotencyCacheEntry.class);
            return entry.essayId() == null ? Optional.empty() : Optional.of(entry.essayId());
        } catch (Exception e) {
            handleRedisFailure("读取幂等缓存失败", e);
            return Optional.empty();
        }
    }

    private void cache(
        String idempotencyKey,
        String contentHash,
        Long essayId,
        String status,
        Duration idempotencyTtl,
        Duration contentTtl
    ) {
        IdempotencyCacheEntry entry = new IdempotencyCacheEntry(status, essayId, contentHash, LocalDateTime.now());
        try {
            String json = objectMapper.writeValueAsString(entry);
            String key = idempotencyRedisKey(idempotencyKey);
            if (StringUtils.hasText(key)) {
                redisTemplate.opsForValue().set(key, json, idempotencyTtl);
            }
            String contentKey = contentRedisKey(contentHash);
            if (StringUtils.hasText(contentKey)) {
                redisTemplate.opsForValue().set(contentKey, json, contentTtl);
            }
        } catch (JsonProcessingException e) {
            log.warn("序列化幂等缓存失败: essayId={}, status={}, error={}", essayId, status, e.getMessage());
        } catch (Exception e) {
            handleRedisFailure("写入幂等缓存失败", e);
        }
    }

    private String idempotencyRedisKey(String idempotencyKey) {
        return StringUtils.hasText(idempotencyKey) ? IDEMPOTENCY_PREFIX + idempotencyKey.trim() : null;
    }

    private String contentRedisKey(String contentHash) {
        return StringUtils.hasText(contentHash) ? CONTENT_PREFIX + contentHash.trim() : null;
    }

    private void handleRedisFailure(String message, Exception e) {
        if (redisRequired) {
            throw new BusinessException("系统繁忙，请稍后再试", e);
        }
        log.warn("{}，已降级到 DB 幂等: {}", message, e.getMessage());
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }
}
