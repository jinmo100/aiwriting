package com.jinmo.aiwriting.service.ai.strategy;

import org.springframework.ai.chat.messages.Message;
import java.util.List;
import java.util.Map;

public interface ModelRequestStrategy {
    Map<String, Object> buildRequestBody(String model, List<Message> messages, Map<String, Object> options);
    String getModelName();
} 