package com.jinmo.essayevaluator.domain.dto;

import java.util.List;

public record DashboardSummaryResponse(
    long totalEssays,
    long completedEssays,
    long failedEssays,
    long scoringEssays,
    Double averageNormalizedScore,
    Double bestNormalizedScore,
    long submissionsLast7Days,
    long submissionsLast30Days,
    long submissionsLast90Days,
    List<TypeCount> typeDistribution
) {
    public DashboardSummaryResponse {
        typeDistribution = typeDistribution == null ? List.of() : List.copyOf(typeDistribution);
    }

    public record TypeCount(
        String essayType,
        String essayTypeDisplayName,
        long count
    ) {
    }
}
