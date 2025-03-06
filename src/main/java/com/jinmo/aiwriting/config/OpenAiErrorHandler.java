package com.jinmo.aiwriting.config;

import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;

@Slf4j
@Component
public class OpenAiErrorHandler implements ResponseErrorHandler {
    
    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        return response.getStatusCode().isError();
    }
    
    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        log.error("OpenAI API错误: {} - {}", 
            response.getStatusCode(), 
            new String(response.getBody().readAllBytes()));
            
        throw new RuntimeException("AI服务调用失败: " + response.getStatusCode());
    }
} 