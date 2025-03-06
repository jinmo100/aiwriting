package com.jinmo.aiwriting.service.ai.strategy;

import org.springframework.ai.chat.messages.Message;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class GeminiRequestStrategy implements ModelRequestStrategy {
    
    @Override
    public Map<String, Object> buildRequestBody(String model, List<Message> messages, Map<String, Object> options) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", convertMessagesForGemini(messages));
        return requestBody;
    }

    private List<Map<String, Object>> convertMessagesForGemini(List<Message> messages) {
        return messages.stream().map(msg -> {
            Map<String, Object> messageMap = new HashMap<>();
            messageMap.put("role", msg.getMessageType().getValue());
            
            // 构建Gemini特定的content格式
            List<Map<String, Object>> contents = new ArrayList<>();
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", msg.getContent());
            contents.add(textContent);
            
            messageMap.put("content", contents);
            return messageMap;
        }).collect(Collectors.toList());
    }

    @Override
    public String getModelName() {
        return "google/gemini-2.0-pro-exp-02-05:free";
    }
} 