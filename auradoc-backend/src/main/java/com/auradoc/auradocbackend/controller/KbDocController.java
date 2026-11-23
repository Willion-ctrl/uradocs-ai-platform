package com.auradoc.auradocbackend.controller;

import com.auradoc.auradocbackend.common.BusinessException;
import com.auradoc.auradocbackend.common.ErrorCode;
import com.auradoc.auradocbackend.common.Result;
import com.auradoc.auradocbackend.dto.DocRequest;
import com.auradoc.auradocbackend.entity.KbDoc;
import com.auradoc.auradocbackend.mapper.KbDocMapper;
import com.auradoc.auradocbackend.mapper.KbSpaceMapper;
import com.auradoc.auradocbackend.security.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class KbDocController {

    private final KbDocMapper kbDocMapper;
    private final KbSpaceMapper kbSpaceMapper;

    /** 按空间查询文档列表（用于前端左侧目录树） */
    @GetMapping
    public Result<List<KbDoc>> listDocs(@RequestParam Long spaceId) {
        List<KbDoc> docs = kbDocMapper.selectList(
                new LambdaQueryWrapper<KbDoc>()
                        .eq(KbDoc::getSpaceId, spaceId)
                        .orderByAsc(KbDoc::getParentId)
                        .orderByAsc(KbDoc::getId)
        );
        return Result.success(docs);
    }

    /** 查询单篇文档详情 */
    @GetMapping("/{id}")
    public Result<KbDoc> getDocById(@PathVariable Long id) {
        KbDoc doc = kbDocMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(ErrorCode.DOC_NOT_FOUND);
        }
        return Result.success(doc);
    }

    /** 新建文档 */
    @PostMapping
    public Result<KbDoc> createDoc(@Valid @RequestBody DocRequest req) {
        if (kbSpaceMapper.selectById(req.getSpaceId()) == null) {
            throw new BusinessException(ErrorCode.SPACE_NOT_FOUND);
        }
        KbDoc doc = new KbDoc();
        doc.setSpaceId(req.getSpaceId());
        doc.setParentId(req.getParentId() != null ? req.getParentId() : 0L);
        doc.setTitle(req.getTitle());
        doc.setContentMd(req.getContentMd() != null ? req.getContentMd() : "");
        doc.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        doc.setAuthorId(UserContext.getUserId());
        kbDocMapper.insert(doc);
        return Result.success(doc);
    }

    /** 更新文档标题与正文 */
    @PutMapping("/{id}")
    public Result<KbDoc> updateDoc(@PathVariable Long id, @Valid @RequestBody DocRequest req) {
        KbDoc existing = kbDocMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.DOC_NOT_FOUND);
        }
        if (!existing.getAuthorId().equals(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        existing.setTitle(req.getTitle());
        existing.setContentMd(req.getContentMd() != null ? req.getContentMd() : "");
        existing.setStatus(req.getStatus() != null ? req.getStatus() : existing.getStatus());
        kbDocMapper.updateById(existing);
        return Result.success(existing);
    }

    /** 逻辑删除文档 */
    @DeleteMapping("/{id}")
    public Result<Void> deleteDoc(@PathVariable Long id) {
        KbDoc existing = kbDocMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.DOC_NOT_FOUND);
        }
        if (!existing.getAuthorId().equals(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        kbDocMapper.deleteById(id);
        return Result.success();
    }
}