package com.jinmo.essayevaluator.service.idempotency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ScoringIdempotencyServiceUserScopeTest {

    @Test
    void idempotencyRedisKeyPrefixesWithUserId() {
        assertEquals(
            "essay-evaluator:user:7:idempotency:idem-1",
            ScoringIdempotencyService.idempotencyRedisKey(7L, " idem-1 ")
        );
    }

    @Test
    void contentRedisKeyPrefixesWithUserId() {
        assertEquals(
            "essay-evaluator:user:7:content-submission:hash-1",
            ScoringIdempotencyService.contentRedisKey(7L, " hash-1 ")
        );
    }

    @Test
    void scopedKeysAreMissingWhenUserIdOrValueIsMissing() {
        assertNull(ScoringIdempotencyService.idempotencyRedisKey(null, "idem-1"));
        assertNull(ScoringIdempotencyService.idempotencyRedisKey(7L, " "));
        assertNull(ScoringIdempotencyService.contentRedisKey(null, "hash-1"));
        assertNull(ScoringIdempotencyService.contentRedisKey(7L, null));
    }
}
