package com.jinmo.aiwriting.common.exception;

/**
 * 业务异常
 * 用于业务逻辑中的可预期错误，如配置不存在、作文不存在等
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
