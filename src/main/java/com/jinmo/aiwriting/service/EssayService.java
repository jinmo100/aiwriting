package com.jinmo.aiwriting.service;

import com.jinmo.aiwriting.common.exception.ResourceNotFoundException;
import com.jinmo.aiwriting.domain.entity.Essay;
import com.jinmo.aiwriting.domain.dto.EssayRequest;
import com.jinmo.aiwriting.domain.dto.EssayResponse;
import com.jinmo.aiwriting.repository.EssayRepository;
import com.jinmo.aiwriting.service.ai.EssayAIService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EssayService {
    
    private final EssayRepository essayRepository;
    private final EssayAIService essayAIService;
    
    @Transactional
    public EssayResponse submitEssay(EssayRequest request) {
        var analysis = essayAIService.analyzeEssay(request.content());
        
        var essay = new Essay();
        essay.setContent(request.content());
        essay.setScore(analysis.score());
        essay.setStrengths(analysis.strengths());
        essay.setSuggestions(analysis.suggestions());
        
        essay = essayRepository.save(essay);
        return EssayResponse.fromEntity(essay);
    }
    
    public EssayResponse getEssay(Long id) {
        return essayRepository.findById(id)
            .map(EssayResponse::fromEntity)
            .orElseThrow(() -> new ResourceNotFoundException("作文不存在"));
    }

    public List<EssayResponse> getAllEssays() {
        return essayRepository.findAll().stream()
            .map(EssayResponse::fromEntity)
            .collect(Collectors.toList());
    }
} 