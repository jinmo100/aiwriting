package com.jinmo.aiwriting.service.ai.strategy;

import org.springframework.ai.chat.messages.Message;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GptRequestStrategy implements ModelRequestStrategy {
    
    @Override
    public Map<String, Object> buildRequestBody(String model, List<Message> messages, Map<String, Object> options) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", convertMessages(messages));
        requestBody.put("temperature", options.getOrDefault("temperature", 0.7));
        requestBody.put("max_tokens", options.getOrDefault("max_tokens", 2048));
        return requestBody;
    }

    private List<Map<String, String>> convertMessages(List<Message> messages) {
        return messages.stream().map(msg -> {
            Map<String, String> messageMap = new HashMap<>();
            messageMap.put("role", msg.getMessageType().getValue());
            messageMap.put("content", msg.getContent());
            return messageMap;
        }).collect(Collectors.toList());
    }

    @Override
    public String getModelName() {
        return "openai/gpt-3.5-turbo";
    }
} 