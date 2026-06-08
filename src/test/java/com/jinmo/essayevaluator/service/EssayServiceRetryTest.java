package com.jinmo.essayevaluator.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.essayevaluator.ai.AIService;
import com.jinmo.essayevaluator.ai.provider.ProviderType;
import com.jinmo.essayevaluator.domain.dto.RubricDefinition;
import com.jinmo.essayevaluator.domain.dto.RubricScoringResult;
import com.jinmo.essayevaluator.domain.entity.ApiConfig;
import com.jinmo.essayevaluator.domain.entity.Essay;
import com.jinmo.essayevaluator.domain.entity.EssayScore;
import com.jinmo.essayevaluator.domain.entity.RubricProfile;
import com.jinmo.essayevaluator.domain.entity.RubricVersion;
import com.jinmo.essayevaluator.mapper.ApiConfigMapper;
import com.jinmo.essayevaluator.mapper.EssayMapper;
import com.jinmo.essayevaluator.mapper.EssayScoreMapper;
import com.jinmo.essayevaluator.service.analysis.InputInspection;
import com.jinmo.essayevaluator.service.analysis.SafetyAnalysis;
import com.jinmo.essayevaluator.service.analysis.EssayInputAnalyzer;
import com.jinmo.essayevaluator.service.idempotency.ScoringIdempotencyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EssayServiceRetryTest {

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
    void retryFailedScoreMovesItBackToScoringAndIncrementsAttemptCount() {
        EssayService service = newService(noOpTaskExecutor());
        Essay essay = new Essay();
        essay.setId(11L);
        essay.setUserId(7L);
        essay.setEssayType("GENERAL");
        essay.setContent("Online learning is useful.");
        essay.setWordCount(4);
        essay.setCharCount(26);
        essay.setIdempotencyKey("idem-1");
        essay.setContentHash("hash-1");
        EssayScore score = new EssayScore();
        score.setId(13L);
        score.setEssayId(11L);
        score.setApiConfigId(3L);
        score.setScoringStatus("FAILED");
        score.setAttemptCount(1);
        score.setErrorCode("PROVIDER_TIMEOUT");
        score.setErrorMessage("AI 分析时间过长，请稍后重试");

        when(currentUserService.requireUserId()).thenReturn(7L);
        when(essayMapper.selectOne(any(Wrapper.class))).thenReturn(essay);
        when(essayScoreMapper.selectOne(any(Wrapper.class))).thenReturn(score);

        var response = service.retryScoring(11L);

        ArgumentCaptor<EssayScore> scoreCaptor = ArgumentCaptor.forClass(EssayScore.class);
        verify(essayScoreMapper).updateById(scoreCaptor.capture());
        EssayScore updated = scoreCaptor.getValue();
        assertThat(updated.getScoringStatus()).isEqualTo("SCORING");
        assertThat(updated.getAttemptCount()).isEqualTo(2);
        assertThat(updated.getErrorCode()).isNull();
        assertThat(updated.getErrorMessage()).isNull();
        verify(idempotencyService).cacheScoring(7L, "idem-1", "hash-1", 11L);
        assertThat(response.scoringStatus()).isEqualTo("SCORING");
    }

    @Test
    void scoringJobRecordsAiInvocationWithCurrentAttemptNumber() {
        EssayService service = newService(noOpTaskExecutor());
        Essay essay = new Essay();
        essay.setId(11L);
        essay.setUserId(7L);
        essay.setEssayType("GENERAL");
        essay.setTaskPrompt(null);
        essay.setContent("Online learning is useful.");
        essay.setWordCount(4);
        essay.setCharCount(26);
        essay.setIdempotencyKey("idem-1");
        essay.setContentHash("hash-1");
        EssayScore score = new EssayScore();
        score.setId(13L);
        score.setEssayId(11L);
        score.setApiConfigId(3L);
        score.setScoringStatus("SCORING");
        score.setAttemptCount(2);
        ApiConfig config = new ApiConfig();
        config.setId(3L);
        config.setProviderType(ProviderType.OPENAI_CHAT_COMPLETIONS);
        config.setModelName("gpt-test");
        RubricDefinition rubric = rubricDefinition();
        InputInspection inspection = passInspection();
        AIService.ProviderInvocation invocation = new AIService.ProviderInvocation(
            "SCORING",
            ProviderType.OPENAI_CHAT_COMPLETIONS,
            "gpt-test",
            "req-2",
            10,
            20,
            30,
            100L,
            "PROVIDER",
            "SUCCESS",
            null,
            null
        );

        when(essayMapper.selectById(11L)).thenReturn(essay);
        when(essayScoreMapper.selectById(13L)).thenReturn(score);
        when(apiConfigMapper.selectById(3L)).thenReturn(config);
        when(rubricService.getActiveRubric(com.jinmo.essayevaluator.domain.enums.EssayType.GENERAL)).thenReturn(rubric);
        when(essayInputAnalyzer.analyze(com.jinmo.essayevaluator.domain.enums.EssayType.GENERAL, null, essay.getContent(), 4, 26))
            .thenReturn(inspection);
        when(aiService.scoreEssay(
            com.jinmo.essayevaluator.domain.enums.EssayType.GENERAL,
            null,
            essay.getContent(),
            rubric,
            config
        )).thenReturn(new AIService.ScoringOutcome(scoringResult(), "gpt-test", 10, 20, 30, 100L, List.of(invocation)));

        ReflectionTestUtils.invokeMethod(service, "runScoringJob", 11L, 13L);

        verify(aiInvocationLogService).recordScoringInvocations(
            eq(7L),
            eq(11L),
            eq(13L),
            same(config),
            eq(2),
            anyList()
        );
    }

    private EssayService newService(TaskExecutor taskExecutor) {
        return new EssayService(
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
            taskExecutor
        );
    }

    private static TaskExecutor noOpTaskExecutor() {
        return task -> { };
    }

    private static RubricDefinition rubricDefinition() {
        RubricProfile profile = new RubricProfile();
        profile.setTypeCode("GENERAL");
        profile.setDisplayName("通用英语作文");
        RubricVersion version = new RubricVersion();
        version.setVersion("v1");
        version.setNativeScale("0-100");
        return new RubricDefinition(profile, version, List.of());
    }

    private static InputInspection passInspection() {
        return new InputInspection(
            new RubricScoringResult.InputAnalysis("PASS", 4, 26, List.of(), List.of()),
            new SafetyAnalysis("PASS", List.of(), "", List.of())
        );
    }

    private static RubricScoringResult scoringResult() {
        return new RubricScoringResult(
            new RubricScoringResult.ScoreValue("0-100", 82.0, 100.0, "82/100"),
            new RubricScoringResult.ScoreValue("0-100", 82.0, 100.0, "82/100"),
            new RubricScoringResult.RubricInfo("GENERAL", "v1", "通用英语作文"),
            "良好",
            new RubricScoringResult.Confidence("HIGH", 0.9, List.of(), List.of()),
            List.of(),
            List.of(),
            new RubricScoringResult.Summary(List.of("clear"), List.of("more detail"), "Good."),
            "",
            new RubricScoringResult.InputAnalysis("PASS", 4, 26, List.of(), List.of()),
            null
        );
    }
}
