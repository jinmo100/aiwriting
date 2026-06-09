package com.jinmo.essayevaluator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinmo.essayevaluator.rag.RagDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG 知识卡文档 Mapper。
 */
@Mapper
public interface RagDocumentMapper extends BaseMapper<RagDocument> {
}
