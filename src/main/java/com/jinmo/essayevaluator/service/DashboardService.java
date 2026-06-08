package com.jinmo.essayevaluator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jinmo.essayevaluator.domain.dto.DashboardSummaryResponse;
import com.jinmo.essayevaluator.domain.entity.Essay;
import com.jinmo.essayevaluator.domain.entity.EssayScore;
import com.jinmo.essayevaluator.domain.enums.EssayType;
import com.jinmo.essayevaluator.mapper.EssayMapper;
import com.jinmo.essayevaluator.mapper.EssayScoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final EssayMapper essayMapper;
    private final EssayScoreMapper essayScoreMapper;
    private final CurrentUserService currentUserService;

    public DashboardSummaryResponse getSummary() {
        Long userId = currentUserService.requireUserId();
        List<Essay> essays = essayMapper.selectList(
            new LambdaQueryWrapper<Essay>()
                .eq(Essay::getUserId, userId)
                .orderByDesc(Essay::getCreatedAt)
        );
        if (essays == null || essays.isEmpty()) {
            return new DashboardSummaryResponse(0, 0, 0, 0, null, null, 0, 0, 0, List.of());
        }

        List<Long> essayIds = essays.stream().map(Essay::getId).toList();
        List<EssayScore> scores = essayScoreMapper.selectList(
            new LambdaQueryWrapper<EssayScore>()
                .in(EssayScore::getEssayId, essayIds)
                .orderByDesc(EssayScore::getCreatedAt)
        );
        Map<Long, EssayScore> latestScoreByEssay = latestScoreByEssay(scores);
        List<EssayScore> latestScores = essays.stream()
            .map(essay -> latestScoreByEssay.get(essay.getId()))
            .filter(score -> score != null)
            .toList();

        long completed = latestScores.stream().filter(score -> "COMPLETED".equals(score.getScoringStatus())).count();
        long failed = latestScores.stream().filter(score -> "FAILED".equals(score.getScoringStatus())).count();
        long scoring = latestScores.stream().filter(score -> "SCORING".equals(score.getScoringStatus()) || "PENDING".equals(score.getScoringStatus())).count();

        List<Double> normalizedScores = latestScores.stream()
            .filter(score -> "COMPLETED".equals(score.getScoringStatus()))
            .map(EssayScore::getNormalizedScore)
            .filter(score -> score != null)
            .toList();
        Double average = normalizedScores.isEmpty()
            ? null
            : roundToOneDecimal(normalizedScores.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        Double best = normalizedScores.stream().max(Double::compareTo).orElse(null);

        LocalDateTime now = LocalDateTime.now();
        return new DashboardSummaryResponse(
            essays.size(),
            completed,
            failed,
            scoring,
            average,
            best,
            countSince(essays, now.minusDays(7)),
            countSince(essays, now.minusDays(30)),
            countSince(essays, now.minusDays(90)),
            typeDistribution(essays)
        );
    }

    private static Map<Long, EssayScore> latestScoreByEssay(List<EssayScore> scores) {
        if (scores == null || scores.isEmpty()) {
            return Map.of();
        }
        return scores.stream()
            .collect(Collectors.toMap(
                EssayScore::getEssayId,
                Function.identity(),
                (a, b) -> compareScoreTime(a, b) >= 0 ? a : b
            ));
    }

    private static int compareScoreTime(EssayScore a, EssayScore b) {
        LocalDateTime left = a.getCreatedAt();
        LocalDateTime right = b.getCreatedAt();
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private static long countSince(List<Essay> essays, LocalDateTime since) {
        return essays.stream()
            .filter(essay -> essay.getCreatedAt() != null && !essay.getCreatedAt().isBefore(since))
            .count();
    }

    private static List<DashboardSummaryResponse.TypeCount> typeDistribution(List<Essay> essays) {
        return essays.stream()
            .collect(Collectors.groupingBy(Essay::getEssayType, Collectors.counting()))
            .entrySet()
            .stream()
            .map(entry -> new DashboardSummaryResponse.TypeCount(
                entry.getKey(),
                EssayType.fromCode(entry.getKey()).getDisplayName(),
                entry.getValue()
            ))
            .sorted(Comparator.comparing(DashboardSummaryResponse.TypeCount::count).reversed())
            .toList();
    }

    private static double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
