package com.jinmo.aiwriting.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.aiwriting.common.exception.AIServiceException;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
//import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.jinmo.aiwriting.service.ai.OpenRouterClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class EssayAIService {
    
    private final OpenRouterClient openRouterClient;
    private final ObjectMapper objectMapper;
    
    public EssayAnalysis analyzeEssay(String content) {
        String systemPromptText = """
            你是一位专业的英语作文评分专家。请对提供的英语作文进行严格的分析和评分。
            
            评分标准 (总分100分):
            1. 内容完整性和逻辑性 (30分)
               - 主题明确
               - 论述完整
               - 逻辑连贯
            
            2. 语言准确性和词汇使用 (30分)
               - 词汇丰富度
               - 用词准确性
               - 表达多样性
            
            3. 语法正确性 (20分)
               - 句法结构
               - 时态使用
               - 语法规则
            
            4. 文章结构和格式 (20分)
               - 段落组织
               - 衔接过渡
               - 格式规范
            
            请以JSON格式返回分析结果，确保包含以下字段：
            {
                "score": <总分，0-100的整数>,
                "strengths": <作文的主要优点，具体列举>,
                "suggestions": <改进建议，针对性和可操作性强>
            }
            
            请确保返回的是有效的JSON格式。
            """;
            
        try {
            log.info("开始分析作文 - 配置信息:");
            log.info("模型: {}", openRouterClient.getClass().getSimpleName());
            log.info("内容长度: {} 字符", content.length());
            
            var prompt = new Prompt(systemPromptText + "\n\n待评分作文:\n" + content);
            log.debug("发送到AI的完整prompt:\n{}", prompt.getContents());
            
            log.info("开始调用AI服务...");
            long startTime = System.currentTimeMillis();
            
            ChatResponse response = openRouterClient.call(prompt);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("AI服务调用完成 - 耗时: {}ms", duration);
            
            if (response == null || response.getResult() == null) {
                log.error("AI服务返回空响应");
                throw new AIServiceException("AI服务返回空响应");
            }
            
            String jsonResponse = response.getResult().getOutput().getContent();
            log.debug("AI原始响应:\n{}", jsonResponse);
            
            try {
                EssayAnalysis analysis = objectMapper.readValue(jsonResponse, EssayAnalysis.class);
                log.info("解析后的评分结果: score={}, strengths={}, suggestions={}", 
                    analysis.score(), 
                    analysis.strengths(), 
                    analysis.suggestions());
                return analysis;
            } catch (Exception e) {
                log.error("JSON解析失败: {}", jsonResponse, e);
                throw new AIServiceException("AI响应格式错误");
            }
            
        } catch (RestClientException e) {
            log.error("AI服务调用失败", e);
            throw new AIServiceException("AI服务暂时不可用，请稍后重试");
        } catch (Exception e) {
            log.error("AI服务异常", e);
            throw new AIServiceException("AI服务分析失败");
        }
    }

    public String hey(String message) {
        var prompt = new Prompt(message);

        ChatResponse response = openRouterClient.call(prompt);
        return response.getResult().getOutput().getContent();
    }
} 