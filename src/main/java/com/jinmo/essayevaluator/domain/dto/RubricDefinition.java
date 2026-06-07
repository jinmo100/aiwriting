package com.jinmo.essayevaluator.domain.dto;

import com.jinmo.essayevaluator.domain.entity.RubricDimension;
import com.jinmo.essayevaluator.domain.entity.RubricProfile;
import com.jinmo.essayevaluator.domain.entity.RubricVersion;

import java.util.Comparator;
import java.util.List;

public record RubricDefinition(
    RubricProfile profile,
    RubricVersion version,
    List<RubricDimension> dimensions
) {
    public RubricDefinition {
        dimensions = dimensions == null
            ? List.of()
            : dimensions.stream()
                .sorted(Comparator.comparing(RubricDimension::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }
}
