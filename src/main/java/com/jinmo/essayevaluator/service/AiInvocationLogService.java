package com.jinmo.essayevaluator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jinmo.essayevaluator.ai.AIService;
import com.jinmo.essayevaluator.domain.dto.AiInvocationLogResponse;
import com.jinmo.essayevaluator.domain.dto.AiUsageSummary;
import com.jinmo.essayevaluator.domain.entity.AiInvocationLog;
import com.jinmo.essayevaluator.domain.entity.ApiConfig;
import com.jinmo.essayevaluator.mapper.AiInvocationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiInvocationLogService {

    private static final BigDecimal MILLION = new BigDecimal("1000000");

    private final AiInvocationLogMapper aiInvocationLogMapper;

    public void recordScoringInvocations(
        Long userId,
        Long essayId,
        Long scoreId,
        ApiConfig config,
        int attemptNo,
        List<AIService.ProviderInvocation> invocations
    ) {
        if (invocations == null || invocations.isEmpty()) {
            return;
        }
        for (AIService.ProviderInvocation invocation : invocations) {
            AiInvocationLog log = new AiInvocationLog();
            log.setUserId(userId);
            log.setEssayId(essayId);
            log.setScoreId(scoreId);
            log.setApiConfigId(config.getId());
            log.setAttemptNo(attemptNo);
            log.setPurpose(invocation.purpose());
            log.setProvider(invocation.providerType() != null ? invocation.providerType().value() : null);
            log.setEndpointType(config.getProviderType() != null ? config.getProviderType().value() : log.getProvider());
            log.setModel(invocation.modelName() != null ? invocation.modelName() : config.getModelName());
            log.setProviderRequestId(invocation.providerRequestId());
            log.setStatus(invocation.status());
            log.setLatencyMs(toInteger(invocation.latencyMillis()));
            log.setInputTokens(invocation.inputTokens());
            log.setOutputTokens(invocation.outputTokens());
            log.setTotalTokens(invocation.totalTokens());
            log.setUsageSource(invocation.usageSource());
            log.setEstimatedCost(calculateCost(config, invocation.inputTokens(), invocation.outputTokens()));
            log.setCurrency(log.getEstimatedCost() != null ? config.getCurrency() : null);
            log.setFailureCode(invocation.failureCode());
            log.setFailureMessage(invocation.failureMessage());
            aiInvocationLogMapper.insert(log);
        }
    }

    public AiUsageSummary summarize(Long userId, Long essayId, Long scoreId) {
        List<AiInvocationLog> logs = aiInvocationLogMapper.selectList(
            new LambdaQueryWrapper<AiInvocationLog>()
                .eq(AiInvocationLog::getUserId, userId)
                .eq(AiInvocationLog::getEssayId, essayId)
                .eq(AiInvocationLog::getScoreId, scoreId)
                .orderByAsc(AiInvocationLog::getCreatedAt)
                .orderByAsc(AiInvocationLog::getId)
        );
        if (logs == null || logs.isEmpty()) {
            return null;
        }

        Integer inputTokens = sum(logs.stream().map(AiInvocationLog::getInputTokens).toList());
        Integer outputTokens = sum(logs.stream().map(AiInvocationLog::getOutputTokens).toList());
        Integer totalTokens = sum(logs.stream().map(AiInvocationLog::getTotalTokens).toList());
        Integer latencyMs = sum(logs.stream().map(AiInvocationLog::getLatencyMs).toList());
        BigDecimal estimatedCost = logs.stream()
            .map(AiInvocationLog::getEstimatedCost)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (BigDecimal.ZERO.compareTo(estimatedCost) == 0) {
            estimatedCost = null;
        }
        AiInvocationLog first = logs.get(0);
        AiInvocationLog last = logs.get(logs.size() - 1);

        return new AiUsageSummary(
            last.getProvider(),
            last.getEndpointType(),
            last.getModel(),
            inputTokens,
            outputTokens,
            totalTokens,
            latencyMs,
            resolveSummaryUsageSource(logs),
            estimatedCost,
            last.getCurrency() != null ? last.getCurrency() : first.getCurrency(),
            logs.size(),
            logs.stream().map(AiInvocationLogResponse::fromEntity).toList()
        );
    }

    private static BigDecimal calculateCost(ApiConfig config, Integer inputTokens, Integer outputTokens) {
        if (config.getInputTokenPricePerMillion() == null || config.getOutputTokenPricePerMillion() == null
            || inputTokens == null || outputTokens == null) {
            return null;
        }
        BigDecimal input = BigDecimal.valueOf(inputTokens)
            .multiply(BigDecimal.valueOf(config.getInputTokenPricePerMillion()))
            .divide(MILLION, 12, RoundingMode.HALF_UP);
        BigDecimal output = BigDecimal.valueOf(outputTokens)
            .multiply(BigDecimal.valueOf(config.getOutputTokenPricePerMillion()))
            .divide(MILLION, 12, RoundingMode.HALF_UP);
        return input.add(output).setScale(6, RoundingMode.HALF_UP);
    }

    private static Integer toInteger(Long value) {
        if (value == null) {
            return null;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : value.intValue();
    }

    private static Integer sum(List<Integer> values) {
        int total = 0;
        boolean hasAny = false;
        for (Integer value : values) {
            if (value != null) {
                total += value;
                hasAny = true;
            }
        }
        return hasAny ? total : null;
    }

    private static String resolveSummaryUsageSource(List<AiInvocationLog> logs) {
        boolean hasEstimate = logs.stream().anyMatch(log -> "LOCAL_ESTIMATE".equals(log.getUsageSource()));
        boolean hasProvider = logs.stream().anyMatch(log -> "PROVIDER".equals(log.getUsageSource()));
        if (hasEstimate && hasProvider) {
            return "MIXED";
        }
        if (hasProvider) {
            return "PROVIDER";
        }
        if (hasEstimate) {
            return "LOCAL_ESTIMATE";
        }
        return "UNAVAILABLE";
    }
}
