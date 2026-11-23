package com.auradoc.auradocbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_chat_log")
public class AiChatLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private Long userId;
    private Long docId;
    private String role;
    private String content;
    private String modelName;
    private Integer tokenUsage;
    @TableLogic
    private Integer isDeleted;
    private LocalDateTime createdAt;
}