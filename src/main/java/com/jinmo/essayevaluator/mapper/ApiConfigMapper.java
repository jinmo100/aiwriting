package com.jinmo.essayevaluator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinmo.essayevaluator.domain.entity.ApiConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * API配置Mapper
 */
@Mapper
public interface ApiConfigMapper extends BaseMapper<ApiConfig> {

    /**
     * 重置所有配置的默认状态
     */
    @Update("UPDATE api_configs SET is_default = false")
    void resetAllDefault();
}
