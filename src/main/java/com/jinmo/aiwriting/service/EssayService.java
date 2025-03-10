package com.jinmo.aiwriting.service;

import java.util.List;
import java.util.stream.Collectors;
import com.jinmo.aiwriting.common.exception.AIServiceException;
import com.jinmo.aiwriting.common.exception.ResourceNotFoundException;
import com.jinmo.aiwriting.domain.dto.EssayRequest;
import com.jinmo.aiwriting.domain.dto.EssayResponse;
import com.jinmo.aiwriting.domain.entity.Essay;
import com.jinmo.aiwriting.repository.EssayRepository;
import com.jinmo.aiwriting.service.ai.EssayAIService;
import com.jinmo.aiwriting.service.ai.EssayAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EssayService {

    private final EssayRepository essayRepository;
    private final EssayAIService essayAIService;

    @Transactional
    public EssayResponse submitEssay(EssayRequest request) {
        try {
            // 分析作文
            EssayAnalysis analysis = essayAIService.analyzeEssay(request.content());

            // 创建新的Essay实体
            Essay essay = new Essay();
            essay.setContent(request.content());
            essay.setScore(analysis.score());
            essay.setStrengths(String.join("\n- ", analysis.strengths()));
            essay.setSuggestions(String.join("\n- ", analysis.suggestions()));

            // 保存到数据库
            essay = essayRepository.save(essay);

            return EssayResponse.fromEntity(essay);
        } catch (AIServiceException e) {
            // 直接抛出AI服务异常，让全局异常处理器处理
            throw e;
        } catch (Exception e) {
            throw new AIServiceException("""
                    {
                        "error": "PROCESSING_ERROR",
                        "message": "作文处理失败",
                        "details": {
                            "reason": "服务器处理错误",
                            "suggestion": "请稍后重试"
                        }
                    }""");
        }
    }

    public EssayResponse getEssay(Long id) {
        return essayRepository.findById(id).map(EssayResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("作文不存在"));
    }

    public List<EssayResponse> getAllEssays() {
        return essayRepository.findAll().stream().map(EssayResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 分页获取作文历史
     * 
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 分页结果
     */
    public Page<EssayResponse> getEssayHistory(int page, int size) {
        PageRequest pageRequest =
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return essayRepository.findAll(pageRequest).map(EssayResponse::fromEntity);
    }

    /**
     * 获取作文详情
     * 
     * @param id 作文ID
     * @return 作文详情
     * @throws ResourceNotFoundException 如果作文不存在
     */
    public EssayResponse getEssayDetail(Long id) {
        return essayRepository.findById(id).map(EssayResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("作文不存在: " + id));
    }
}
