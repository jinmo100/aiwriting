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
        // response = response.replaceAll(""", "\"")
        // .replaceAll(""", "\"")
        // .replaceAll("'", "'")
        // .replaceAll("'", "'");
        return response;
    }

    public EssayAnalysis analyzeEssay(String content) {
        String systemPromptText =
                """
                        你是一位专业的英语作文评分专家。请对提供的英语作文进行严格的分析和评分。

                        评分标准 (总分100分):
                        1. 内容完整性和逻辑性 (30分)
                        2. 语言准确性和词汇使用 (30分)
                        3. 语法正确性 (20分)
                        4. 文章结构和格式 (20分)

                        请严格按照以下JSON格式返回分析结果：
                        {
                            "score": <0-100的整数>,
                            "strengths": [
                                "<优点1，需包含具体例子>",
                                "<优点2，需包含具体例子>",
                                "<优点3，需包含具体例子>"
                            ],
                            "suggestions": [
                                "<建议1，需包含具体例子>",
                                "<建议2，需包含具体例子>",
                                "<建议3，需包含具体例子>",
                                "<建议4，需包含具体例子>",
                                "<建议5，需包含具体例子>"
                            ]
                        }

                        注意事项：
                        1. strengths和suggestions必须是数组格式，使用[]包裹
                        2. 每个优点和建议都必须是完整的中文句子
                        3. 每个优点和建议都必须包含具体的文本示例
                        4. 不要使用Markdown格式的列表符号
                        5. 不要在JSON中使用多余的换行和缩进

                        优点示例：
                        "文章用词准确，恰当使用了学术词汇，如使用'consequently'作为逻辑连接词，'phenomenon'描述自然现象"

                        建议示例：
                        "句式结构单一，建议使用更多复合句。例如，可以将'He is tall. He plays basketball.'改为'Being tall, he excels at basketball.'"

                        请确保返回的是严格的JSON格式，不要添加任何额外的格式化字符。
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
