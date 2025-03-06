package com.jinmo.aiwriting.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.aiwriting.common.exception.AIServiceException;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
// import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EssayAIService {

    private final OpenRouterClient openRouterClient;
    private final ObjectMapper objectMapper;

    private String cleanJsonResponse(String response) {
        // 移除可能的Markdown代码块标记
        response = response.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "");
        // 移除开头和结尾的空白字符
        response = response.trim();
        // 替换中文引号为英文引号
//        response = response.replaceAll("“", "\"")
//                         .replaceAll("”", "\"")
//                         .replaceAll("'", "'")
//                         .replaceAll("'", "'");
        return response;
    }

    public EssayAnalysis analyzeEssay(String content) {
        String systemPromptText =
                """
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
                            "strengths": <中文，作文的主要优点，具体列举>,
                            "suggestions": <中文，改进建议，针对性和可操作性强>
                        }

                        "strengths" 和 "suggestions" 字段必须使用中文回答,并且需要包含具体的例子。例如,不要只说"vocabulary usage accurate",而应该指出"恰当使用了academic vocabulary,如运用'phenomenon'一词描述自然现象,使用'consequently'作为逻辑连接词"。不要说"argument lacks depth",而应该建议"在讨论environmental protection时,建议补充具体论据,如'According to WHO research, air pollution causes 7 million premature deaths annually'"。请确保每个优点和建议都有具体的text evidence支持。

                        请确保返回的是有效的的JSON格式。
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

            // 清理JSON响应
            String cleanedJson = cleanJsonResponse(jsonResponse);
            log.debug("清理后的JSON:\n{}", cleanedJson);

            try {
                EssayAnalysis analysis = objectMapper.readValue(cleanedJson, EssayAnalysis.class);
                log.info("解析后的评分结果: score={}, strengths={}, suggestions={}", analysis.score(),
                        analysis.strengths(), analysis.suggestions());
                return analysis;
            } catch (Exception e) {
                log.error("JSON解析失败: {}", cleanedJson, e);
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
        String content = response.getResult().getOutput().getContent();
        return cleanJsonResponse(content);
    }
}
