package com.jinmo.aiwriting.controller;

import com.jinmo.aiwriting.service.ai.EssayAIService;
import com.jinmo.aiwriting.service.ai.EssayAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class AITestController {

    private final EssayAIService essayAIService;

    @GetMapping("/ai")
    public ResponseEntity<Map<String, Object>> testAI() {
        log.info("开始AI服务测试");
        Map<String, Object> result = new HashMap<>();

        try {
            String testEssay = """
                    The Impact of Technology on Modern Society
                    
                    Technology has become an integral part of our daily lives, transforming how we work, communicate, and interact with one another. This essay explores the various ways in which technology has influenced modern society.
                    
                    First, technology has revolutionized communication. Through smartphones and social media platforms, people can instantly connect with others across the globe. This has made the world smaller and more interconnected than ever before.
                    
                    Second, technology has changed the workplace dramatically. Remote work has become increasingly common, enabled by video conferencing and cloud computing. This shift has led to greater flexibility and work-life balance for many professionals.
                    
                    However, technology also presents challenges. Issues like digital addiction, privacy concerns, and cybersecurity threats have emerged as significant problems that society must address.
                    
                    In conclusion, while technology offers numerous benefits, we must carefully consider its impact and work to mitigate its potential drawbacks.
                    """;

            log.info("测试文本长度: {} 字符", testEssay.length());

            // 记录开始时间
            long startTime = System.currentTimeMillis();

            // 调用AI服务
            EssayAnalysis analysis = essayAIService.analyzeEssay(testEssay);

            // 计算耗时
            long duration = System.currentTimeMillis() - startTime;

            // 构建返回结果
            result.put("success", true);
            result.put("duration", duration + "ms");
            result.put("analysis", analysis);
            result.put("testEssay", testEssay);

            log.info("AI服务测试成功 - 耗时: {}ms", duration);
            log.info("评分结果: {}", analysis);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("AI服务测试失败", e);

            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            result.put("errorTrace", e.getStackTrace());

            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/test2")
    public ResponseEntity<Map<String, Object>> test2() {
        log.info("开始AI服务测试2");
        Map<String, Object> result = new HashMap<>();

        try {
            String response = essayAIService.hey("你好");
            result.put("response", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("AI服务测试失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());

        }
        return ResponseEntity.internalServerError().body(result);
    }
} 