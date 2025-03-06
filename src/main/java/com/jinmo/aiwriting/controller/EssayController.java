package com.jinmo.aiwriting.controller;

import com.jinmo.aiwriting.domain.dto.EssayRequest;
import com.jinmo.aiwriting.domain.dto.EssayResponse;
import com.jinmo.aiwriting.service.EssayService;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.List;

@RestController
@RequestMapping("/api/essays")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class EssayController {

    private final EssayService essayService;
    
    @PostMapping
    public EssayResponse submitEssay(@Valid @RequestBody EssayRequest request) {
        return essayService.submitEssay(request);
    }
    
    @GetMapping("/{id}")
    public EssayResponse getEssay(@PathVariable Long id) {
        return essayService.getEssay(id);
    }

    @GetMapping
    public List<EssayResponse> getAllEssays() {
        return essayService.getAllEssays();
    }
} 