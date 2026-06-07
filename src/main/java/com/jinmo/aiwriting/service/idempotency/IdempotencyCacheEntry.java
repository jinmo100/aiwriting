package com.jinmo.aiwriting.service.idempotency;

import java.time.LocalDateTime;

public record IdempotencyCacheEntry(
    String status,
    Long essayId,
    String contentHash,
    LocalDateTime createdAt
) {
}
