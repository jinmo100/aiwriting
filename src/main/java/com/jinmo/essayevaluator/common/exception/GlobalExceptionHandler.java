package com.jinmo.essayevaluator.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.validation.ConstraintViolationException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusinessException(BusinessException e) {
        log.error("业务错误: {}", e.getMessage());
        Map<String, Object> response = new HashMap<>();
        response.put("error", "BUSINESS_ERROR");
        response.put("message", e.getMessage());
        response.put("details", Map.of(
            "suggestion", "请检查业务操作是否合规"
        ));
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
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