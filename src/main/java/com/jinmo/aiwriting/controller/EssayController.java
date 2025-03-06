package com.jinmo.aiwriting.controller;

import com.jinmo.aiwriting.domain.dto.EssayRequest;
import com.jinmo.aiwriting.domain.dto.EssayResponse;
import com.jinmo.aiwriting.service.EssayService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

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

    /**
     * 获取作文历史（分页）
     */
    @GetMapping("/history")
    public ResponseEntity<Page<EssayResponse>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(essayService.getEssayHistory(page, size));
    }

    /**
     * 获取作文详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<EssayResponse> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(essayService.getEssayDetail(id));
    }
}
