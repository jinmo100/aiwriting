package com.jinmo.essayevaluator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinmo.essayevaluator.embedding.EmbeddingConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * Embedding 配置 Mapper。
 */
@Mapper
public interface EmbeddingConfigMapper extends BaseMapper<EmbeddingConfig> {

    /**
     * 只重置当前用户自己的默认配置，避免跨用户影响。
     */
    @Update("UPDATE embedding_configs SET is_default = false WHERE owner_user_id = #{ownerUserId}")
    void resetDefaultForOwner(@Param("ownerUserId") Long ownerUserId);
}
