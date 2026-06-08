package com.jinmo.essayevaluator.ai;

import com.jinmo.essayevaluator.domain.dto.RubricDefinition;
import com.jinmo.essayevaluator.domain.entity.RubricDimension;
import com.jinmo.essayevaluator.domain.entity.RubricProfile;
import com.jinmo.essayevaluator.domain.entity.RubricVersion;

import java.util.List;

public final class RubricTestFixtures {
    private RubricTestFixtures() {
    }

    public static RubricDefinition generalRubric() {
        RubricProfile profile = new RubricProfile();
        profile.setId(1L);
        profile.setTypeCode("GENERAL");
        profile.setDisplayName("通用英语作文");
        profile.setIsEnabled(true);

        RubricVersion version = new RubricVersion();
        version.setId(1L);
        version.setProfileId(1L);
        version.setVersion("GENERAL_V1");
        version.setStatus("ACTIVE");
        version.setNativeScale("PERCENT_100");
        version.setMaxNativeScore(100.0);
        version.setPromptInstructions("按通用英语写作质量评分。");
        version.setResultSchemaVersion("RUBRIC_RESULT_V1");

        return new RubricDefinition(profile, version, List.of(
            dimension(1L, "content_quality", "内容质量", 30, 1),
            dimension(2L, "organization", "结构组织", 25, 2),
            dimension(3L, "language_accuracy", "语言准确性", 25, 3),
            dimension(4L, "expression", "表达丰富度", 20, 4)
        ));
    }

    public static RubricDefinition ieltsRubric() {
        RubricProfile profile = new RubricProfile();
        profile.setId(2L);
        profile.setTypeCode("IELTS_TASK_2");
        profile.setDisplayName("雅思 Task 2 议论文");
        profile.setIsEnabled(true);

        RubricVersion version = new RubricVersion();
        version.setId(2L);
        version.setProfileId(2L);
        version.setVersion("IELTS_TASK_2_V1");
        version.setStatus("ACTIVE");
        version.setNativeScale("IELTS_BAND_0_9");
        version.setMaxNativeScore(9.0);
        version.setPromptInstructions("按 IELTS Task 2 评分。");
        version.setResultSchemaVersion("RUBRIC_RESULT_V1");

        return new RubricDefinition(profile, version, List.of(
            dimension(11L, "task_response", "Task Response", 9, 1),
            dimension(12L, "coherence_cohesion", "Coherence and Cohesion", 9, 2),
            dimension(13L, "lexical_resource", "Lexical Resource", 9, 3),
            dimension(14L, "grammar_range_accuracy", "Grammatical Range and Accuracy", 9, 4)
        ));
    }

    private static RubricDimension dimension(Long id, String key, String label, double maxScore, int sortOrder) {
        RubricDimension dimension = new RubricDimension();
        dimension.setId(id);
        dimension.setRubricVersionId(1L);
        dimension.setDimensionKey(key);
        dimension.setLabel(label);
        dimension.setDescription(label + " description");
        dimension.setMaxScore(maxScore);
        dimension.setWeight(maxScore);
        dimension.setSortOrder(sortOrder);
        dimension.setLevelDescriptorsJson("[]");
        return dimension;
    }
}
