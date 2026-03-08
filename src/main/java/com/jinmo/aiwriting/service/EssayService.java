package com.jinmo.aiwriting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.aiwriting.ai.AIService;
import com.jinmo.aiwriting.common.exception.BusinessException;
import com.jinmo.aiwriting.domain.dto.EssayScoreResponse;
import com.jinmo.aiwriting.domain.dto.EssaySubmitRequest;
import com.jinmo.aiwriting.domain.dto.ScoringResult;
import com.jinmo.aiwriting.domain.entity.ApiConfig;
import com.jinmo.aiwriting.domain.entity.Essay;
import com.jinmo.aiwriting.domain.entity.EssayScore;
import com.jinmo.aiwriting.mapper.ApiConfigMapper;
import com.jinmo.aiwriting.mapper.EssayMapper;
import com.jinmo.aiwriting.mapper.EssayScoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 作文服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EssayService {

    private final EssayMapper essayMapper;
    private final EssayScoreMapper essayScoreMapper;
    private final ApiConfigMapper apiConfigMapper;
    private final AIService aiService;
    private final ObjectMapper objectMapper;

    /**
     * 提交作文并评分
     */
    @Transactional
    public EssayScoreResponse submitAndScore(EssaySubmitRequest request) {
        log.info("提交作文，字数: {}", request.content().split("\\s+").length);

        // 获取配置
        ApiConfig config;
        if (request.configId() != null) {
            config = apiConfigMapper.selectById(request.configId());
            if (config == null) {
                throw new BusinessException("指定的配置不存在");
            }
        } else {
            // 使用默认配置
            config = apiConfigMapper.selectOne(
                new LambdaQueryWrapper<ApiConfig>()
                    .eq(ApiConfig::getIsDefault, true)
            );
            if (config == null) {
                throw new BusinessException("请先配置API并设置为默认");
            }
        }

        // 保存作文
        Essay essay = new Essay();
        essay.setContent(request.content());
        essay.setEssayType(request.essayType());
        essayMapper.insert(essay);

        // AI评分
        long startTime = System.currentTimeMillis();
        ScoringResult result = aiService.scoreEssay(request.content(), config);
        long duration = System.currentTimeMillis() - startTime;

        // 保存评分结果
        EssayScore score = new EssayScore();
        score.setEssayId(essay.getId());
        score.setApiConfigId(config.getId());
        score.setOverallScore(result.overallScore());
        score.setContentScore(result.contentScore());
        score.setLanguageScore(result.languageScore());
        score.setStructureScore(result.structureScore());
        score.setCoherenceScore(result.coherenceScore());
        score.setAiModel(config.getModelName());
        score.setProcessingTime((int) duration);

        try {
            score.setStrengths(objectMapper.writeValueAsString(result.strengths()));
            score.setSuggestions(objectMapper.writeValueAsString(result.suggestions()));
            score.setErrors(objectMapper.writeValueAsString(result.errors()));
            score.setDetailedFeedback(result.detailedFeedback());
        } catch (JsonProcessingException e) {
            log.error("序列化评分详情失败", e);
        }

        essayScoreMapper.insert(score);

        // 返回结果
        return new EssayScoreResponse(
            essay.getId(),
            new EssayScoreResponse.ScoreDetail(
                score.getOverallScore(),
                score.getContentScore(),
                score.getLanguageScore(),
                score.getStructureScore(),
                score.getCoherenceScore(),
                result.strengths(),
                result.suggestions(),
                result.errors().stream()
                    .map(error -> new EssayScoreResponse.ErrorDetail(
                        error.sentence(),
                        error.type(),
                        error.description(),
                        error.correction()
                    ))
                    .toList(),
                score.getDetailedFeedback()
            ),
            score.getProcessingTime()
        );
    }

    /**
     * 分页查询历史
     */
    public Page<Essay> getHistory(int page, int size) {
        return essayMapper.selectPage(
            new Page<>(page, size),
            new LambdaQueryWrapper<Essay>()
                .orderByDesc(Essay::getCreatedAt)
        );
    }

    /**
     * 获取作文详情
     */
    public Essay getEssay(Long id) {
        Essay essay = essayMapper.selectById(id);
        if (essay == null) {
            throw new BusinessException("作文不存在");
        }
        return essay;
    }

    /**
     * 获取评分详情
     */
    public EssayScore getScore(Long essayId) {
        return essayScoreMapper.selectOne(
            new LambdaQueryWrapper<EssayScore>()
                .eq(EssayScore::getEssayId, essayId)
        );
    }
}
