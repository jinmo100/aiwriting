package com.jinmo.essayevaluator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jinmo.essayevaluator.common.exception.BusinessException;
import com.jinmo.essayevaluator.domain.dto.RubricDefinition;
import com.jinmo.essayevaluator.domain.entity.RubricDimension;
import com.jinmo.essayevaluator.domain.entity.RubricProfile;
import com.jinmo.essayevaluator.domain.entity.RubricVersion;
import com.jinmo.essayevaluator.domain.enums.EssayType;
import com.jinmo.essayevaluator.mapper.RubricDimensionMapper;
import com.jinmo.essayevaluator.mapper.RubricProfileMapper;
import com.jinmo.essayevaluator.mapper.RubricVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RubricService {

    private final RubricProfileMapper rubricProfileMapper;
    private final RubricVersionMapper rubricVersionMapper;
    private final RubricDimensionMapper rubricDimensionMapper;

    public RubricDefinition getActiveRubric(EssayType essayType) {
        RubricProfile profile = rubricProfileMapper.selectOne(
            new LambdaQueryWrapper<RubricProfile>()
                .eq(RubricProfile::getTypeCode, essayType.name())
        );
        if (profile == null) {
            throw new BusinessException("未找到作文类型对应的评分标准: " + essayType.name());
        }
        if (!Boolean.TRUE.equals(profile.getIsEnabled()) || !essayType.isEnabledInUi()) {
            throw new BusinessException("该作文类型暂未开放: " + profile.getDisplayName());
        }

        RubricVersion version = rubricVersionMapper.selectOne(
            new LambdaQueryWrapper<RubricVersion>()
                .eq(RubricVersion::getProfileId, profile.getId())
                .eq(RubricVersion::getStatus, "ACTIVE")
                .last("LIMIT 1")
        );
        if (version == null) {
            throw new BusinessException("作文类型缺少 ACTIVE 评分标准版本: " + essayType.name());
        }

        List<RubricDimension> dimensions = rubricDimensionMapper.selectList(
            new LambdaQueryWrapper<RubricDimension>()
                .eq(RubricDimension::getRubricVersionId, version.getId())
                .orderByAsc(RubricDimension::getSortOrder)
        );
        if (dimensions.isEmpty()) {
            throw new BusinessException("评分标准缺少维度配置: " + version.getVersion());
        }

        return new RubricDefinition(profile, version, dimensions);
    }
}
