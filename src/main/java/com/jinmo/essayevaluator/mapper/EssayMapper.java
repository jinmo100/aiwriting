package com.jinmo.essayevaluator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinmo.essayevaluator.domain.entity.Essay;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作文Mapper
 */
@Mapper
public interface EssayMapper extends BaseMapper<Essay> {
}
