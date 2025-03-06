package com.jinmo.aiwriting.common.exception;

import com.jinmo.aiwriting.common.response.ErrorResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AIServiceException.class)
    public ResponseEntity<ErrorResponse> handleAIServiceException(AIServiceException e) {
        log.error("AI服务异常", e);
        return ResponseEntity.internalServerError()
            .body(new ErrorResponse("AI服务暂时不可用,请稍后重试"));
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException e) {
        return ResponseEntity.notFound()
            .build();
    }
} 