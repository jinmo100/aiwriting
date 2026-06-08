package com.jinmo.essayevaluator.service;

import com.jinmo.essayevaluator.ai.AIService;
import com.jinmo.essayevaluator.ai.provider.ProviderType;
import com.jinmo.essayevaluator.domain.entity.AiInvocationLog;
import com.jinmo.essayevaluator.domain.entity.ApiConfig;
import com.jinmo.essayevaluator.mapper.AiInvocationLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiInvocationLogServiceTest {

    @Mock
    private AiInvocationLogMapper aiInvocationLogMapper;

    @Test
    void recordScoringInvocationsStoresProviderUsageAndEstimatedCost() {
        AiInvocationLogService service = new AiInvocationLogService(aiInvocationLogMapper);
        ApiConfig config = new ApiConfig();
        config.setId(3L);
        config.setProviderType(ProviderType.OPENAI_CHAT_COMPLETIONS);
        config.setModelName("gpt-test");
        config.setInputTokenPricePerMillion(2.0);
        config.setOutputTokenPricePerMillion(8.0);
        config.setCurrency("USD");

        service.recordScoringInvocations(
            7L,
            11L,
            13L,
            config,
            1,
            List.of(new AIService.ProviderInvocation(
                "SCORING",
                ProviderType.OPENAI_CHAT_COMPLETIONS,
                "gpt-test",
                "req-123",
                1000,
                2000,
                3000,
                1500L,
                "PROVIDER",
                "SUCCESS",
                null,
                null
            ))
        );

        ArgumentCaptor<AiInvocationLog> captor = ArgumentCaptor.forClass(AiInvocationLog.class);
        verify(aiInvocationLogMapper).insert(captor.capture());
        AiInvocationLog log = captor.getValue();
        assertThat(log.getUserId()).isEqualTo(7L);
        assertThat(log.getEssayId()).isEqualTo(11L);
        assertThat(log.getScoreId()).isEqualTo(13L);
        assertThat(log.getApiConfigId()).isEqualTo(3L);
        assertThat(log.getPurpose()).isEqualTo("SCORING");
        assertThat(log.getProvider()).isEqualTo("OPENAI_CHAT_COMPLETIONS");
        assertThat(log.getEndpointType()).isEqualTo("OPENAI_CHAT_COMPLETIONS");
        assertThat(log.getModel()).isEqualTo("gpt-test");
        assertThat(log.getProviderRequestId()).isEqualTo("req-123");
        assertThat(log.getInputTokens()).isEqualTo(1000);
        assertThat(log.getOutputTokens()).isEqualTo(2000);
        assertThat(log.getTotalTokens()).isEqualTo(3000);
        assertThat(log.getUsageSource()).isEqualTo("PROVIDER");
        assertThat(log.getEstimatedCost()).isEqualByComparingTo(new BigDecimal("0.018000"));
        assertThat(log.getCurrency()).isEqualTo("USD");
    }
}
