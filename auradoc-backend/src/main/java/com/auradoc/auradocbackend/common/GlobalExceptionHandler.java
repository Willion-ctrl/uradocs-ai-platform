package com.auradoc.auradocbackend.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 拦截自定义业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常 [code={}]: {}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 拦截 @Valid 参数校验失败异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = (fieldError != null) ? fieldError.getDefaultMessage() : ErrorCode.BAD_REQUEST.getMessage();
        log.warn("参数校验失败: {}", message);
        return Result.error(ErrorCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 兜底拦截所有未捕获的系统异常，防止堆栈泄露给前端
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpectedException(Exception e) {
        log.error("系统未捕获异常", e);
        return Result.error(ErrorCode.INTERNAL_ERROR);
    }
}