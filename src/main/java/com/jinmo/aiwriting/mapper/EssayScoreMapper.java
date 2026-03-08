package com.jinmo.aiwriting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinmo.aiwriting.domain.entity.EssayScore;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作文评分Mapper
 */
@Mapper
public interface EssayScoreMapper extends BaseMapper<EssayScore> {
}
