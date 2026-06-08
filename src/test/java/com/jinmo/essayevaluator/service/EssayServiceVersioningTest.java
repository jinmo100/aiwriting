package com.jinmo.essayevaluator.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.essayevaluator.ai.AIService;
import com.jinmo.essayevaluator.ai.RubricTestFixtures;
import com.jinmo.essayevaluator.ai.provider.ProviderType;
import com.jinmo.essayevaluator.domain.dto.EssaySubmitRequest;
import com.jinmo.essayevaluator.domain.dto.RubricScoringResult;
import com.jinmo.essayevaluator.domain.entity.ApiConfig;
import com.jinmo.essayevaluator.domain.entity.Essay;
import com.jinmo.essayevaluator.mapper.ApiConfigMapper;
import com.jinmo.essayevaluator.mapper.EssayMapper;
import com.jinmo.essayevaluator.mapper.EssayScoreMapper;
import com.jinmo.essayevaluator.service.analysis.EssayInputAnalyzer;
import com.jinmo.essayevaluator.service.analysis.InputInspection;
import com.jinmo.essayevaluator.service.analysis.SafetyAnalysis;
import com.jinmo.essayevaluator.service.idempotency.ScoringIdempotencyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EssayServiceVersioningTest {

    @Mock private EssayMapper essayMapper;
    @Mock private EssayScoreMapper essayScoreMapper;
    @Mock private ApiConfigMapper apiConfigMapper;
    @Mock private RubricService rubricService;
    @Mock private CurrentUserService currentUserService;
    @Mock private AiInvocationLogService aiInvocationLogService;
    @Mock private EssayInputAnalyzer essayInputAnalyzer;
    @Mock private ScoringIdempotencyService idempotencyService;
    @Mock private AIService aiService;

    @Test
    void submittingRevisionCreatesNextVersionWithoutContentHashDeduplication() {
        EssayService service = new EssayService(
            essayMapper,
            essayScoreMapper,
            apiConfigMapper,
            rubricService,
            currentUserService,
            aiInvocationLogService,
            essayInputAnalyzer,
            idempotencyService,
            aiService,
            new ObjectMapper(),
            noOpTaskExecutor()
        );
        Essay parent = new Essay();
        parent.setId(11L);
        parent.setUserId(7L);
        parent.setEssayType("GENERAL");
        parent.setTaskPrompt("");
        parent.setContent("Old version.");
        parent.setEssayGroupId(11L);
        parent.setVersionNo(1);
        parent.setCreatedAt(LocalDateTime.now());
        ApiConfig config = new ApiConfig();
        config.setId(3L);
        config.setProviderType(ProviderType.OPENAI_CHAT_COMPLETIONS);
        config.setModelName("gpt-test");
        String content = "Online learning helps students plan their time and review difficult lessons after class.";

        when(currentUserService.requireUserId()).thenReturn(7L);
        when(essayInputAnalyzer.analyze(any(), eq(""), eq(content), any(Integer.class), any(Integer.class))).thenReturn(passInspection(content));
        when(essayMapper.selectOne(any(Wrapper.class))).thenReturn(parent, parent, null);
        when(idempotencyService.findCachedEssayId(eq(7L), eq("idem-revision"), isNull())).thenReturn(Optional.empty());
        when(apiConfigMapper.selectOne(any(Wrapper.class))).thenReturn(config);
        when(rubricService.getActiveRubric(any())).thenReturn(RubricTestFixtures.generalRubric());
        when(essayMapper.insert(any(Essay.class))).thenAnswer(invocation -> {
            Essay essay = invocation.getArgument(0);
            essay.setId(12L);
            return 1;
        });

        var response = service.submitAndScore(new EssaySubmitRequest(
            content,
            "GENERAL",
            "",
            3L,
            "idem-revision",
            11L
        ));

        ArgumentCaptor<Essay> essayCaptor = ArgumentCaptor.forClass(Essay.class);
        verify(essayMapper).insert(essayCaptor.capture());
        Essay inserted = essayCaptor.getValue();
        assertThat(inserted.getParentEssayId()).isEqualTo(11L);
        assertThat(inserted.getEssayGroupId()).isEqualTo(11L);
        assertThat(inserted.getVersionNo()).isEqualTo(2);
        verify(idempotencyService).findCachedEssayId(7L, "idem-revision", null);
        verify(idempotencyService).cacheScoring(7L, "idem-revision", null, 12L);
        assertThat(response.essay().parentEssayId()).isEqualTo(11L);
        assertThat(response.essay().essayGroupId()).isEqualTo(11L);
        assertThat(response.essay().versionNo()).isEqualTo(2);
    }

    private static InputInspection passInspection(String content) {
        return new InputInspection(
            new RubricScoringResult.InputAnalysis("PASS", content.split("\\s+").length, content.length(), List.of(), List.of()),
            new SafetyAnalysis("PASS", List.of(), "", List.of())
        );
    }

    private static TaskExecutor noOpTaskExecutor() {
        return task -> { };
    }
}
