package com.auradoc.auradocbackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiChatRequest {
    /** 会话ID，前端首次提问可不传，由后端自动生成 UUID 返回 */
    private String sessionId;

    /** 可选：关联的知识库文档ID，传入后 AI 会基于该文档内容回答 */
    private Long docId;

    @NotBlank(message = "提问内容不能为空")
    private String question;
}