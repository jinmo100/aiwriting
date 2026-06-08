package com.jinmo.essayevaluator.service;

import com.jinmo.essayevaluator.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private RedisOperations<String, String> redisOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void checkExpiresNewCounter() {
        when(redisOperations.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("auth:login:ip:127.0.0.1")).thenReturn(1L);
        RateLimitService service = new RateLimitService(redisOperations);

        service.check("auth:login:ip:127.0.0.1", 10, Duration.ofMinutes(15));

        verify(redisOperations).expire("auth:login:ip:127.0.0.1", Duration.ofMinutes(15));
    }

    @Test
    void checkRejectsWhenCounterExceedsLimit() {
        when(redisOperations.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("essay:submit:user:7")).thenReturn(6L);
        RateLimitService service = new RateLimitService(redisOperations);

        BusinessException error = assertThrows(BusinessException.class,
            () -> service.check("essay:submit:user:7", 5, Duration.ofMinutes(1)));

        assertEquals("操作过于频繁，请稍后再试", error.getMessage());
    }
}
