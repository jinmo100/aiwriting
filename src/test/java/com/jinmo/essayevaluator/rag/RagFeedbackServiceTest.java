package com.jinmo.essayevaluator.rag;

import com.jinmo.essayevaluator.domain.entity.Essay;
import com.jinmo.essayevaluator.domain.entity.EssayScore;
import com.jinmo.essayevaluator.embedding.EmbeddingConfig;
import com.jinmo.essayevaluator.embedding.EmbeddingConfigService;
import com.jinmo.essayevaluator.job.BackgroundJob;
import com.jinmo.essayevaluator.job.BackgroundJobDispatcher;
import com.jinmo.essayevaluator.job.BackgroundJobService;
import com.jinmo.essayevaluator.job.BackgroundJobStatus;
import com.jinmo.essayevaluator.job.BackgroundJobType;
import com.jinmo.essayevaluator.mapper.EssayMapper;
import com.jinmo.essayevaluator.mapper.EssayScoreMapper;
import com.jinmo.essayevaluator.mapper.RagFeedbackCitationMapper;
import com.jinmo.essayevaluator.mapper.RagFeedbackMapper;
import com.jinmo.essayevaluator.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static com.jinmo.essayevaluator.rag.RagFeedbackDtos.RagFeedbackGenerateRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagFeedbackServiceTest {

    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private EssayMapper essayMapper;
    @Mock
    private EssayScoreMapper essayScoreMapper;
    @Mock
    private EmbeddingConfigService embeddingConfigService;
    @Mock
    private BackgroundJobService backgroundJobService;
    @Mock
    private BackgroundJobDispatcher backgroundJobDispatcher;
    @Mock
    private RagFeedbackMapper ragFeedbackMapper;
    @Mock
    private RagFeedbackCitationMapper ragFeedbackCitationMapper;

    @Test
    void generateCreatesFeedbackPlaceholderSoPollingCanSeeRunningJob() {
        Essay essay = essay();
        EssayScore score = completedScore();
        EmbeddingConfig embeddingConfig = embeddingConfig();
        BackgroundJob job = backgroundJob(77L, BackgroundJobStatus.PENDING);
        mockGenerateInputs(essay, score, embeddingConfig, job);
        when(ragFeedbackMapper.findByScoreAndConfig(7L, 123L, 5L)).thenReturn(null);

        RagFeedbackDtos.RagFeedbackResponse response = newService().generate(
            99L,
            new RagFeedbackGenerateRequest(null)
        );

        ArgumentCaptor<RagFeedback> placeholderCaptor = ArgumentCaptor.forClass(RagFeedback.class);
        verify(ragFeedbackMapper).insert(placeholderCaptor.capture());
        RagFeedback placeholder = placeholderCaptor.getValue();
        assertThat(placeholder.getUserId()).isEqualTo(7L);
        assertThat(placeholder.getEssayId()).isEqualTo(99L);
        assertThat(placeholder.getScoreId()).isEqualTo(123L);
        assertThat(placeholder.getApiConfigId()).isEqualTo(22L);
        assertThat(placeholder.getEmbeddingConfigId()).isEqualTo(5L);
        assertThat(placeholder.getJobId()).isEqualTo(77L);
        assertThat(placeholder.getFeedbackJson()).isNull();
        assertThat(response.status()).isEqualTo(BackgroundJobStatus.PENDING);
        verify(backgroundJobDispatcher).dispatchAsync(77L);
    }

    @Test
    void regenerateClearsOldFeedbackBeforeReturningActiveJobStatus() {
        Essay essay = essay();
        EssayScore score = completedScore();
        EmbeddingConfig embeddingConfig = embeddingConfig();
        BackgroundJob job = backgroundJob(88L, BackgroundJobStatus.PENDING);
        RagFeedback existing = new RagFeedback();
        existing.setId(66L);
        existing.setFeedbackJson("{\"overall\":\"旧反馈\"}");
        existing.setRetrievedChunkIds("[1,2,3]");
        existing.setQueryText("old query");
        mockGenerateInputs(essay, score, embeddingConfig, job);
        when(ragFeedbackMapper.findByScoreAndConfig(7L, 123L, 5L)).thenReturn(existing);

        RagFeedbackDtos.RagFeedbackResponse response = newService().generate(
            99L,
            new RagFeedbackGenerateRequest(null)
        );

        ArgumentCaptor<RagFeedback> updateCaptor = ArgumentCaptor.forClass(RagFeedback.class);
        verify(ragFeedbackMapper).updateById(updateCaptor.capture());
        RagFeedback update = updateCaptor.getValue();
        assertThat(update.getId()).isEqualTo(66L);
        assertThat(update.getJobId()).isEqualTo(88L);
        assertThat(update.getFeedbackJson()).isNull();
        assertThat(update.getRetrievedChunkIds()).isNull();
        assertThat(update.getQueryText()).isNull();
        verify(ragFeedbackCitationMapper).delete(any());
        assertThat(response.status()).isEqualTo(BackgroundJobStatus.PENDING);
        assertThat(response.feedbackJson()).isNull();
    }

    private void mockGenerateInputs(Essay essay, EssayScore score, EmbeddingConfig embeddingConfig, BackgroundJob job) {
        when(currentUserService.requireUserId()).thenReturn(7L);
        when(essayMapper.selectOne(any())).thenReturn(essay);
        when(essayScoreMapper.selectOne(any())).thenReturn(score);
        when(embeddingConfigService.getDefaultConfigForUser(7L)).thenReturn(embeddingConfig);
        when(backgroundJobService.createOrReuse(
            eq(BackgroundJobType.RAG_FEEDBACK),
            eq(7L),
            eq(7L),
            eq("rag-feedback:score:123:embedding:5"),
            any(Map.class)
        )).thenReturn(job);
        when(ragFeedbackCitationMapper.findByFeedbackId(any())).thenReturn(List.of());
    }

    private Essay essay() {
        Essay essay = new Essay();
        essay.setId(99L);
        essay.setUserId(7L);
        essay.setEssayType("SENIOR_GAOKAO");
        essay.setContent("This is a completed essay.");
        return essay;
    }

    private EssayScore completedScore() {
        EssayScore score = new EssayScore();
        score.setId(123L);
        score.setEssayId(99L);
        score.setApiConfigId(22L);
        score.setScoringStatus("COMPLETED");
        score.setResultJson("{}");
        return score;
    }

    private EmbeddingConfig embeddingConfig() {
        EmbeddingConfig config = new EmbeddingConfig();
        config.setId(5L);
        config.setOwnerUserId(7L);
        return config;
    }

    private BackgroundJob backgroundJob(Long id, BackgroundJobStatus status) {
        BackgroundJob job = new BackgroundJob();
        job.setId(id);
        job.setStatus(status);
        job.setJobType(BackgroundJobType.RAG_FEEDBACK);
        return job;
    }

    private RagFeedbackService newService() {
        return new RagFeedbackService(
            currentUserService,
            essayMapper,
            essayScoreMapper,
            embeddingConfigService,
            backgroundJobService,
            backgroundJobDispatcher,
            ragFeedbackMapper,
            ragFeedbackCitationMapper
        );
    }
}
