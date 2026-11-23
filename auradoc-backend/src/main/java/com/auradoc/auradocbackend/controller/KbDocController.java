package com.auradoc.auradocbackend.controller;

import com.auradoc.auradocbackend.common.Result;
import com.auradoc.auradocbackend.entity.KbDoc;
import com.auradoc.auradocbackend.service.KbDocService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class KbDocController {

    private final KbDocService kbDocService;

    /** 1. 查询空间下的文档列表 */
    @GetMapping
    public Result<List<KbDoc>> listDocs(@RequestParam Long spaceId) {
        return Result.success(kbDocService.listDocs(spaceId));
    }

    /** 2. 多字段联合检索（放在 /{id} 上方，防止 "search" 被误解析成 Long id） */
    @GetMapping("/search")
    public Result<List<KbDoc>> searchDocs(
            @RequestParam(required = false) Long spaceId,
            @RequestParam(required = false) String keyword) {
        return Result.success(kbDocService.searchDocs(spaceId, keyword));
    }

    /** 3. 获取单篇文档详情 */
    @GetMapping("/{id}")
    public Result<KbDoc> getDoc(@PathVariable Long id) {
        return Result.success(kbDocService.getDocById(id));
    }

    /** 4. 新建文档 */
    @PostMapping
    public Result<KbDoc> createDoc(@RequestBody KbDoc req) {
        return Result.success(kbDocService.createDoc(req));
    }

    /** 5. 更新文档 */
    @PutMapping("/{id}")
    public Result<KbDoc> updateDoc(@PathVariable Long id, @RequestBody KbDoc req) {
        return Result.success(kbDocService.updateDoc(id, req));
    }

    /** 6. 逻辑删除文档 */
    @DeleteMapping("/{id}")
    public Result<Void> deleteDoc(@PathVariable Long id) {
        kbDocService.deleteDoc(id);
        return Result.success(null);
    }

    /** 7. 一键生成并保存文档 AI 摘要 */
    @PostMapping("/{id}/summary")
    public Result<KbDoc> generateSummary(@PathVariable Long id) {
        return Result.success(kbDocService.generateAiSummary(id));
    }
}