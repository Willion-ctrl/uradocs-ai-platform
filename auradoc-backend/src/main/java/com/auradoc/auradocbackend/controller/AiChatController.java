package com.auradoc.auradocbackend.controller;

import com.auradoc.auradocbackend.common.Result;
import com.auradoc.auradocbackend.dto.AiChatRequest;
import com.auradoc.auradocbackend.entity.AiChatLog;
import com.auradoc.auradocbackend.mapper.AiChatLogMapper;
import com.auradoc.auradocbackend.security.UserContext;
import com.auradoc.auradocbackend.service.AiChatService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;
    private final AiChatLogMapper aiChatLogMapper;

    /** 基于文档上下文的 SSE 流式问答 */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@Valid @RequestBody AiChatRequest req) {
        // 关键：在主线程中取出 userId 再交给异步线程
        Long userId = UserContext.getUserId();
        return aiChatService.streamChat(userId, req.getSessionId(), req.getDocId(), req.getQuestion());
    }

    /** 查询某个会话的完整聊天记录 */
    @GetMapping("/chat/history")
    public Result<List<AiChatLog>> getHistory(@RequestParam String sessionId) {
        Long userId = UserContext.getUserId();
        List<AiChatLog> logs = aiChatLogMapper.selectList(
                new LambdaQueryWrapper<AiChatLog>()
                        .eq(AiChatLog::getSessionId, sessionId)
                        .eq(AiChatLog::getUserId, userId)
                        .orderByAsc(AiChatLog::getCreatedAt)
        );
        return Result.success(logs);
    }
}