package com.auradoc.auradocbackend.common;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 通用状态码
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "请求参数不合法"),
    UNAUTHORIZED(401, "未登录或 Token 已过期"),
    FORBIDDEN(403, "无权限访问该资源"),
    NOT_FOUND(404, "请求的资源不存在"),
    INTERNAL_ERROR(500, "系统内部异常，请稍后重试"),

    // 用户模块 (1xxx)
    USER_ALREADY_EXISTS(1001, "用户名已被注册"),
    INVALID_CREDENTIALS(1002, "用户名或密码错误"),
    USER_DISABLED(1003, "该账号已被禁用"),

    // 知识库与文档模块 (2xxx)
    SPACE_NOT_FOUND(2001, "知识空间不存在"),
    DOC_NOT_FOUND(2002, "文档不存在或已被删除"),

    // AI 模块 (3xxx)
    AI_SERVICE_ERROR(3001, "AI 服务调用失败，请稍后重试");

    private final int code;
    private final String message;
}