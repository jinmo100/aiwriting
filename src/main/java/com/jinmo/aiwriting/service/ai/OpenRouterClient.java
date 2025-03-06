package com.jinmo.aiwriting.service.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.aiwriting.service.ai.strategy.GeminiRequestStrategy;
import com.jinmo.aiwriting.service.ai.strategy.GptRequestStrategy;
import com.jinmo.aiwriting.service.ai.strategy.ModelRequestStrategy;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenRouterClient implements ChatClient {
    private final RestTemplate restTemplate;
    private final RetryTemplate retryTemplate;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Map<String, String> headers;
    private final ObjectMapper objectMapper;
    private final Map<String, ModelRequestStrategy> strategies;

    public OpenRouterClient(String apiKey, String baseUrl, String model,
            Map<String, String> headers, RetryTemplate retryTemplate) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.headers = headers;
        this.objectMapper = new ObjectMapper();
        this.retryTemplate = retryTemplate;
        this.restTemplate = createRestTemplate();

        // 初始化策略
        this.strategies = new HashMap<>();
        strategies.put("openai/gpt-3.5-turbo", new GptRequestStrategy());
        strategies.put("google/gemini-2.0-pro-exp-02-05:free",
                new GeminiRequestStrategy());
        strategies.put("google/gemma-2-9b-it:free",
                new GeminiRequestStrategy());
    }

    private RestTemplate createRestTemplate() {
        RestTemplate template = new RestTemplate();
        template.getInterceptors().add((request, body, execution) -> {
            HttpHeaders headers = request.getHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("HTTP-Referer", "http://localhost:8080");
            headers.set("X-Title", "AI Essay Grading System");
            headers.set("User-Agent", "Spring AI");

            return execution.execute(request, body);
        });
        return template;
    }

    @Override
    public String call(String message) {
        return ChatClient.super.call(message);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        try {
            return retryTemplate.execute(context -> {
                try {
                    log.debug("Sending request to OpenRouter API");

                    // 获取对应的策略
                    ModelRequestStrategy strategy = strategies.get(model);
                    if (strategy == null) {
                        throw new RuntimeException("Unsupported model: " + model);
                    }

                    // 使用策略构建请求体
                    Map<String, Object> options = new HashMap<>();
                    options.put("temperature", 0.7);
                    options.put("max_tokens", 2048);

                    Map<String, Object> requestBody =
                            strategy.buildRequestBody(model, prompt.getInstructions(), options);

                    // 修正API端点URL
                    String apiUrl = baseUrl + "/chat/completions";
                    log.debug("Sending request to: {}", apiUrl);
                    log.debug("Request body: {}", objectMapper.writeValueAsString(requestBody));

                    // 打印请求的所有信息
                    log.info("curl {} \\\n" +
                            "  -H \"Content-Type: application/json\" \\\n" +
                            "  -H \"Authorization: Bearer {}\" \\\n" +
                            "  -H \"HTTP-Referer: {}\" \\\n" +
                            "  -H \"X-Title: {}\" \\\n" +
                            "  -H \"User-Agent: {}\" \\\n" +
                            "  -d '{}'",
                            apiUrl,
                            apiKey,
                            "http://localhost:8080",
                            "AI Essay Grading System", 
                            "Spring AI",
                            objectMapper.writeValueAsString(requestBody));

                    // 发送请求
                    ResponseEntity<Map> response =
                            restTemplate.postForEntity(apiUrl, requestBody, Map.class);

                    if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                        throw new RuntimeException("Failed to get response from OpenRouter");
                    }

                    // 处理响应
                    Map responseBody = response.getBody();
                    List<Map<String, Object>> choices =
                            (List<Map<String, Object>>) responseBody.get("choices");
                    if (choices == null || choices.isEmpty()) {
                        throw new RuntimeException("No response choices available");
                    }

                    Map<String, Object> choice = choices.get(0);
                    Map<String, String> message = (Map<String, String>) choice.get("message");
                    String content = message.get("content");

                    // 创建Generation并返回ChatResponse
                    List<Generation> generations = List.of(new Generation(content));
                    return new ChatResponse(generations);

                } catch (Exception e) {
                    log.error("Error calling OpenRouter API (Attempt {}): {}",
                            context.getRetryCount(), e.getMessage());
                    throw e;
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Error processing JSON response: {}", e.getMessage());
            throw new RuntimeException("Error processing JSON response", e);
        }
    }
}
