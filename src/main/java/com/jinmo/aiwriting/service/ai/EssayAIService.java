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
        try {
            // 检测字符串编码
            if (!isUtf8(response)) {
                // 如果不是UTF-8，尝试转换
                byte[] bytes = response.getBytes("ISO-8859-1");
                response = new String(bytes, "UTF-8");
            }

            // 移除可能的Markdown代码块标记
            response = response.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "");

            // 移除开头和结尾的空白字符
            response = response.trim();

            // 处理JSON中的特殊字符
            response = response.replaceAll("\\\\n", "\n").replaceAll("\\\\r", "\r")
                    .replaceAll("\\\\t", "\t");

            return response;
        } catch (Exception e) {
            log.error("清理JSON响应时出错", e);
            return response;
        }
    }

    // 检测字符串是否为UTF-8编码
    private boolean isUtf8(String str) {
        try {
            byte[] bytes = str.getBytes();
            String test = new String(bytes, "UTF-8");
            return str.equals(test);
        } catch (Exception e) {
            return false;
        }
    }

    public EssayAnalysis analyzeEssay(String content) {
        // 首先检测是否为英语文本
        if (!isEnglishText(content)) {
            throw new AIServiceException("""
                    {
                        "error": "INVALID_LANGUAGE",
                        "message": "只支持英语作文评分",
                        "details": {
                            "expected": "英语",
                            "suggestion": "请提供英语作文"
                        }
                    }""");
        }

        String systemPromptText =
                """
                        你是一位专业的中国籍的英语作文评分专家。你的任务是对英语作文进行评分和分析。
                        如果发现提交的不是英语作文，请返回以下JSON格式的错误提示：
                        {
                            "error": "INVALID_LANGUAGE",
                            "message": "非英语作文，无法评分",
                            "details": {
                                "reason": "<发现的具体问题>",
                                "suggestion": "请提供英语作文"
                            }
                        }

                        评分标准 (总分100分):
                        1. 内容完整性和逻辑性 (30分) - 评估文章的主题展开、论据支撑和逻辑连贯性
                        2. 语言准确性和词汇使用 (30分) - 评估英语词汇运用、表达准确性和词汇丰富度
                        3. 语法正确性 (20分) - 评估英语语法规则运用、句式正确性
                        4. 文章结构和格式 (20分) - 评估段落组织、文章结构和格式规范

                        请严格按照以下JSON格式返回分析结果：
                        {
                            "score": <0-100的整数>,
                            "strengths": [
                                "<用中文描述优点1，需引用英语原文中的具体例子>",
                                "<用中文描述优点2，需引用英语原文中的具体例子>",
                                "<用中文描述优点3，需引用英语原文中的具体例子>"
                            ],
                            "suggestions": [
                                "<用中文给出建议1，需结合英语写作规范给出具体例子>",
                                "<用中文给出建议2，需结合英语写作规范给出具体例子>",
                                "<用中文给出建议3，需结合英语写作规范给出具体例子>",
                                "<用中文给出建议4，需结合英语写作规范给出具体例子>",
                                "<用中文给出建议5，需结合英语写作规范给出具体例子>"
                            ]
                        }

                        注意事项：
                        1. 必须返回标准UTF-8编码的JSON
                        2. strengths和suggestions必须是数组格式，使用[]包裹
                        3. 所有评价和建议必须用中文表达
                        4. 每个优点必须引用英语原文中的具体例子
                        5. 每个建议必须给出英语写作的具体改进示例
                        6. 不要使用Markdown格式的列表符号
                        7. 不要在JSON中使用多余的换行和缩进
                        8. 如果提交的不是英语作文，返回错误提示："非英语作文，无法评分"

                        优点示例：
                        "作者准确运用了学术英语词汇，如原文中使用'consequently'作为逻辑连接词，'phenomenon'描述自然现象"

                        建议示例：
                        "文中的简单句过多，建议增加复合句使用。例如，可以将'He is tall. He plays basketball.'改写为'Being tall, he excels at basketball.'"

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

    /**
     * 检测文本是否为英语 通过检查非英语字符的比例来判断
     */
    private boolean isEnglishText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        // 移除标点符号和空白字符
        text = text.replaceAll("[\\p{Punct}\\s]", "");

        // 统计非英语字符的数量
        long nonEnglishCount = text.chars()
                .filter(ch -> !((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))).count();

        // 如果非英语字符超过20%，则认为不是英语文本
        double nonEnglishRatio = (double) nonEnglishCount / text.length();

        log.debug("文本语言检测 - 总字符数: {}, 非英语字符数: {}, 比例: {}", text.length(), nonEnglishCount,
                nonEnglishRatio);

        return nonEnglishRatio <= 0.2; // 允许20%的非英语字符（数字等）
    }

    public String hey(String message) {
        var prompt = new Prompt(message);
        ChatResponse response = openRouterClient.call(prompt);
        String content = response.getResult().getOutput().getContent();
        return cleanJsonResponse(content);
    }
}
