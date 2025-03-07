package com.jinmo.aiwriting.common.exception;

import com.jinmo.aiwriting.common.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.validation.ConstraintViolationException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AIServiceException.class)
    public ResponseEntity<Object> handleAIServiceException(AIServiceException e) {
        String message = e.getMessage();
        log.error(message);
        
        // 检查消息是否为JSON格式
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(message);
            // 如果是JSON格式，直接返回
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(jsonNode);
        } catch (JsonProcessingException ex) {
            // 如果不是JSON格式，包装成标准错误格式
            Map<String, Object> response = new HashMap<>();
            response.put("error", "AI_SERVICE_ERROR");
            response.put("message", message);
            response.put("details", Map.of(
                "suggestion", "请稍后重试"
            ));
            return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
        }
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFoundException(ResourceNotFoundException e) {
        log.error(e.getMessage());
        Map<String, Object> response = new HashMap<>();
        response.put("error", "RESOURCE_NOT_FOUND");
        response.put("message", e.getMessage());
        response.put("details", Map.of(
            "suggestion", "请检查请求的资源是否存在"
        ));
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGenericException(Exception e) {
        log.error("服务器内部错误", e);
        Map<String, Object> response = new HashMap<>();
        response.put("error", "INTERNAL_SERVER_ERROR");
        response.put("message", "服务器内部错误");
        response.put("details", Map.of(
            "suggestion", "请稍后重试"
        ));
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationException(MethodArgumentNotValidException e) {
        log.error("请求参数验证失败: {}", e.getMessage());
        
        // 获取所有验证错误
        List<String> errors = e.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(DefaultMessageSourceResolvable::getDefaultMessage)
            .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("error", "VALIDATION_ERROR");
        response.put("message", String.join(",", errors));
        response.put("details", Map.of(
            "errors", errors,
            "suggestion", "请检查输入内容是否符合要求"
        ));

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException e) {
        log.error("参数约束验证失败: {}", e.getMessage());
        
        List<String> errors = e.getConstraintViolations()
            .stream()
            .map(violation -> violation.getMessage())
            .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("error", "VALIDATION_ERROR");
        response.put("message", e.getMessage());
        response.put("details", Map.of(
            "errors", errors,
            "suggestion", "请检查输入内容是否符合要求"
        ));

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }
} 