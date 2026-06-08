package com.jinmo.essayevaluator.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.jinmo.essayevaluator.domain.entity.Essay;
import com.jinmo.essayevaluator.domain.entity.EssayScore;
import com.jinmo.essayevaluator.mapper.EssayMapper;
import com.jinmo.essayevaluator.mapper.EssayScoreMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private EssayMapper essayMapper;
    @Mock private EssayScoreMapper essayScoreMapper;
    @Mock private CurrentUserService currentUserService;

    @Test
    void summarizesCurrentUsersLearningProgress() {
        DashboardService service = new DashboardService(essayMapper, essayScoreMapper, currentUserService);
        LocalDateTime now = LocalDateTime.now();
        Essay e1 = essay(1L, "JUNIOR_ZHONGKAO", now.minusDays(2));
        Essay e2 = essay(2L, "SENIOR_GAOKAO", now.minusDays(10));
        Essay e3 = essay(3L, "SENIOR_GAOKAO", now.minusDays(40));

        when(currentUserService.requireUserId()).thenReturn(7L);
        when(essayMapper.selectList(any(Wrapper.class))).thenReturn(List.of(e1, e2, e3));
        when(essayScoreMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            score(1L, "COMPLETED", 80.0),
            score(2L, "COMPLETED", 90.0),
            score(3L, "FAILED", null)
        ));

        var summary = service.getSummary();

        assertThat(summary.totalEssays()).isEqualTo(3);
        assertThat(summary.completedEssays()).isEqualTo(2);
        assertThat(summary.failedEssays()).isEqualTo(1);
        assertThat(summary.averageNormalizedScore()).isEqualTo(85.0);
        assertThat(summary.bestNormalizedScore()).isEqualTo(90.0);
        assertThat(summary.submissionsLast7Days()).isEqualTo(1);
        assertThat(summary.submissionsLast30Days()).isEqualTo(2);
        assertThat(summary.submissionsLast90Days()).isEqualTo(3);
        assertThat(summary.typeDistribution())
            .extracting(item -> item.essayType() + ":" + item.count())
            .containsExactlyInAnyOrder("JUNIOR_ZHONGKAO:1", "SENIOR_GAOKAO:2");
    }

    private static Essay essay(Long id, String type, LocalDateTime createdAt) {
        Essay essay = new Essay();
        essay.setId(id);
        essay.setUserId(7L);
        essay.setEssayType(type);
        essay.setCreatedAt(createdAt);
        return essay;
    }

    private static EssayScore score(Long essayId, String status, Double normalizedScore) {
        EssayScore score = new EssayScore();
        score.setEssayId(essayId);
        score.setScoringStatus(status);
        score.setNormalizedScore(normalizedScore);
        score.setCreatedAt(LocalDateTime.now());
        return score;
    }
}
