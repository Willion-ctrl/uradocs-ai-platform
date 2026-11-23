package com.auradoc.auradocbackend.service;

import com.auradoc.auradocbackend.common.BusinessException;
import com.auradoc.auradocbackend.common.ErrorCode;
import com.auradoc.auradocbackend.entity.AiChatLog;
import com.auradoc.auradocbackend.entity.KbDoc;
import com.auradoc.auradocbackend.mapper.AiChatLogMapper;
import com.auradoc.auradocbackend.mapper.KbDocMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final KbDocMapper kbDocMapper;
    private final AiChatLogMapper aiChatLogMapper;
    private final ObjectMapper objectMapper;

    @Value("${auradoc.ai.base-url}")
    private String baseUrl;

    @Value("${auradoc.ai.api-key}")
    private String apiKey;

    @Value("${auradoc.ai.model}")
    private String model;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public SseEmitter streamChat(Long userId, String sessionId, Long docId, String question) {
        // 设置 3 分钟超时，避免长文本生成被提前掐断
        SseEmitter emitter = new SseEmitter(180_000L);
        String finalSessionId = (sessionId == null || sessionId.isBlank())
                ? UUID.randomUUID().toString() : sessionId;

        CompletableFuture.runAsync(() -> {
            StringBuilder fullReply = new StringBuilder();
            try {
                // 1. 先把 sessionId 推送给前端，方便多轮对话绑定
                emitter.send(SseEmitter.event()
                        .name("meta")
                        .data(Map.of("sessionId", finalSessionId)));

                // 2. 组装 System Prompt（若传了 docId，则注入文档正文作为知识库上下文）
                List<Map<String, String>> messages = new ArrayList<>();
                StringBuilder systemPrompt = new StringBuilder("你是 AuraDocs 企业级知识库智能助手，回答需准确、简洁，善用 Markdown 格式。");
                if (docId != null) {
                    KbDoc doc = kbDocMapper.selectById(docId);
                    if (doc != null) {
                        systemPrompt.append("\n\n请优先基于以下参考文档回答用户问题，若文档未提及请明确说明：\n")
                                .append("【文档标题】：").append(doc.getTitle()).append("\n")
                                .append("【文档正文】：\n").append(doc.getContentMd());
                    }
                }
                messages.add(Map.of("role", "system", "content", systemPrompt.toString()));

                // 3. 拉取同一会话最近 6 条历史记录，实现多轮上下文记忆
                List<AiChatLog> history = aiChatLogMapper.selectList(
                        new LambdaQueryWrapper<AiChatLog>()
                                .eq(AiChatLog::getSessionId, finalSessionId)
                                .eq(AiChatLog::getUserId, userId)
                                .orderByDesc(AiChatLog::getCreatedAt)
                                .last("LIMIT 6")
                );
                Collections.reverse(history);
                for (AiChatLog h : history) {
                    messages.add(Map.of("role", h.getRole(), "content", h.getContent()));
                }
                messages.add(Map.of("role", "user", "content", question));

                // 4. 持久化用户提问
                saveLog(finalSessionId, userId, docId, "user", question);

                // 5. 构造 OpenAI 兼容流式请求
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("model", model);
                payload.put("stream", true);
                payload.put("messages", messages);

                String endpoint = baseUrl.endsWith("/")
                        ? baseUrl + "chat/completions"
                        : baseUrl + "/chat/completions";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(120))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                        .build();

                // 6. 逐行读取流式响应并实时推送给前端
                HttpResponse<Stream<String>> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

                if (response.statusCode() != 200) {
                    String errBody = response.body().reduce("", (a, b) -> a + b);
                    log.error("LLM 接口异常 [status={}]: {}", response.statusCode(), errBody);
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", "AI 服务响应异常: " + response.statusCode())));
                    emitter.complete();
                    return;
                }

                try (Stream<String> lines = response.body()) {
                    for (String line : (Iterable<String>) lines::iterator) {
                        if (!line.startsWith("data:")) {
                            continue;
                        }
                        String data = line.substring(5).trim();
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        JsonNode root = objectMapper.readTree(data);
                        String delta = root.path("choices").path(0).path("delta").path("content").asText("");
                        if (!delta.isEmpty()) {
                            fullReply.append(delta);
                            emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data(Map.of("content", delta)));
                        }
                    }
                }

                // 7. 持久化完整 AI 回复并结束流
                if (!fullReply.isEmpty()) {
                    saveLog(finalSessionId, userId, docId, "assistant", fullReply.toString());
                }
                emitter.send(SseEmitter.event().name("done").data(Map.of("status", "DONE")));
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE 流式对话异常", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void saveLog(String sessionId, Long userId, Long docId, String role, String content) {
        AiChatLog chatLog = new AiChatLog();
        chatLog.setSessionId(sessionId);
        chatLog.setUserId(userId);
        chatLog.setDocId(docId);
        chatLog.setRole(role);
        chatLog.setContent(content);
        chatLog.setModelName(model);
        chatLog.setTokenUsage(0);
        aiChatLogMapper.insert(chatLog);
    }
    /** 同步调用大模型生成文档摘要（非流式，用于持久化到 kb_doc.ai_summary） */
    public String generateSummary(String title, String contentMd) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("stream", false);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "你是一个专业的技术文档助手。请用简洁的中文为以下文档生成 100 字以内的核心摘要，直接输出摘要内容，不要带“本文介绍了”等套话。"),
                    Map.of("role", "user", "content", "文档标题：" + title + "\n\n文档内容：\n" + contentMd)
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 摘要服务响应异常: HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            return root.path("choices").get(0).path("message").path("content").asText().trim();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "生成 AI 摘要失败: " + e.getMessage());
        }
    }
}