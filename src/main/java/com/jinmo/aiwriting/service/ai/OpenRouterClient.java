package com.jinmo.aiwriting.service.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.messages.Message;
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

    public OpenRouterClient(String apiKey, String baseUrl, String model,
            Map<String, String> headers, RetryTemplate retryTemplate) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.headers = headers;
        this.objectMapper = new ObjectMapper();
        this.retryTemplate = retryTemplate;
        this.restTemplate = createRestTemplate();
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

                    // 构建请求体
                    List<Message> messages = prompt.getInstructions();

                    Map<String, Object> requestBody = new HashMap<>();
                    requestBody.put("model", model);
                    requestBody.put("messages", convertMessages(messages));
                    requestBody.put("temperature", 0.7);
                    requestBody.put("max_tokens", 2048);

                    // 修正API端点URL
                    String apiUrl = baseUrl + "/chat/completions";
                    log.debug("Sending request to: {}", apiUrl);
                    log.debug("Request body: {}", objectMapper.writeValueAsString(requestBody));

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
                    log.error("Error calling OpenRouter API (Attempt {}): {}", context.getRetryCount(),
                            e.getMessage());
                    throw e;
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Error processing JSON response: {}", e.getMessage());
            throw new RuntimeException("Error processing JSON response", e);
        }
    }

    private List<Map<String, String>> convertMessages(List<Message> messages) {
        return messages.stream().map(msg -> {
            Map<String, String> messageMap = new HashMap<>();
            messageMap.put("role", msg.getMessageType().getValue());
            messageMap.put("content", msg.getContent());
            return messageMap;
        }).collect(Collectors.toList());
    }
}
