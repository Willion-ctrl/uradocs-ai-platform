package com.auradoc.auradocbackend.service;

import com.auradoc.auradocbackend.common.BusinessException;
import com.auradoc.auradocbackend.common.ErrorCode;
import com.auradoc.auradocbackend.entity.KbDoc;
import com.auradoc.auradocbackend.entity.KbSpace;
import com.auradoc.auradocbackend.mapper.KbDocMapper;
import com.auradoc.auradocbackend.mapper.KbSpaceMapper;
import com.auradoc.auradocbackend.security.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KbDocService {

    /** 空间可见性常量：0-私有，1-团队公开 */
    private static final int VISIBILITY_PUBLIC = 1;
    /** 送入大模型生成摘要的最大正文字符数 */
    private static final int MAX_SUMMARY_INPUT_CHARS = 8000;

    private final KbDocMapper kbDocMapper;
    private final KbSpaceMapper kbSpaceMapper;
    private final AiChatService aiChatService;

    /**
     * 1. 查询指定空间下的文档列表（剔除大文本 content_md，提升列表加载性能）
     */
    public List<KbDoc> listDocs(Long spaceId) {
        if (spaceId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "知识空间 ID 不能为空");
        }
        assertSpaceReadable(spaceId, requireCurrentUserId());

        LambdaQueryWrapper<KbDoc> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(KbDoc.class, info -> !"content_md".equals(info.getColumn()))
                .eq(KbDoc::getSpaceId, spaceId)
                .orderByDesc(KbDoc::getUpdatedAt)
                .orderByDesc(KbDoc::getId);
        return kbDocMapper.selectList(wrapper);
    }

    /**
     * 2. 查询单篇文档完整详情（含正文与 AI 摘要，带空间读权限校验）
     */
    public KbDoc getDocById(Long id) {
        KbDoc doc = requireDocExists(id);
        assertSpaceReadable(doc.getSpaceId(), requireCurrentUserId());
        return doc;
    }

    /**
     * 3. 新建文档（仅空间所有者可创建）
     */
    public KbDoc createDoc(KbDoc req) {
        if (req == null || req.getSpaceId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "所属知识空间不能为空");
        }
        Long userId = requireCurrentUserId();
        assertSpaceWritable(req.getSpaceId(), userId);

        // 构造干净的落库对象，防止前端透传伪造 id / aiSummary 等字段
        KbDoc doc = new KbDoc();
        doc.setSpaceId(req.getSpaceId());
        doc.setTitle(req.getTitle() == null || req.getTitle().isBlank()
                ? "未命名文档"
                : req.getTitle().trim());
        doc.setContentMd(req.getContentMd() == null ? "" : req.getContentMd());
        doc.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        doc.setAuthorId(userId);

        kbDocMapper.insert(doc);
        return kbDocMapper.selectById(doc.getId());
    }

    /**
     * 4. 更新文档（白名单字段更新 + 跨空间移动双向鉴权）
     */
    public KbDoc updateDoc(Long id, KbDoc req) {
        if (req == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "更新内容不能为空");
        }
        Long userId = requireCurrentUserId();
        KbDoc existing = requireDocExists(id);
        assertSpaceWritable(existing.getSpaceId(), userId);

        // 若请求移动文档到其他空间，校验目标空间写权限
        if (req.getSpaceId() != null && !req.getSpaceId().equals(existing.getSpaceId())) {
            assertSpaceWritable(req.getSpaceId(), userId);
            existing.setSpaceId(req.getSpaceId());
        }

        if (req.getTitle() != null) {
            if (req.getTitle().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "文档标题不能为空");
            }
            existing.setTitle(req.getTitle().trim());
        }
        if (req.getContentMd() != null) {
            existing.setContentMd(req.getContentMd());
        }
        if (req.getStatus() != null) {
            existing.setStatus(req.getStatus());
        }

        kbDocMapper.updateById(existing);
        return kbDocMapper.selectById(id);
    }

    /**
     * 5. 逻辑删除文档（仅空间所有者可删除）
     */
    public void deleteDoc(Long id) {
        KbDoc existing = requireDocExists(id);
        assertSpaceWritable(existing.getSpaceId(), requireCurrentUserId());
        kbDocMapper.deleteById(id);
    }

    /**
     * 6. 一键生成 AI 摘要并持久化
     * 注意：刻意不加 @Transactional，避免大模型网络耗时长时间占用数据库连接
     */
    public KbDoc generateAiSummary(Long docId) {
        Long userId = requireCurrentUserId();
        KbDoc doc = requireDocExists(docId);
        // 仅允许有写权限的用户触发，避免未授权调用消耗 API 额度
        assertSpaceWritable(doc.getSpaceId(), userId);

        if (doc.getContentMd() == null || doc.getContentMd().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文档正文为空，无法生成摘要");
        }

        // 截断超长正文，防止触发大模型 Token 上限
        String contentForAi = doc.getContentMd().length() > MAX_SUMMARY_INPUT_CHARS
                ? doc.getContentMd().substring(0, MAX_SUMMARY_INPUT_CHARS)
                : doc.getContentMd();

        // 事务外调用外部大模型
        String summary = aiChatService.generateSummary(doc.getTitle(), contentForAi);

        // 仅局部更新 ai_summary 字段，避免覆盖并发编辑中的正文
        LambdaUpdateWrapper<KbDoc> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(KbDoc::getId, docId)
                .set(KbDoc::getAiSummary, summary);
        kbDocMapper.update(null, updateWrapper);

        return kbDocMapper.selectById(docId);
    }

    /**
     * 7. 多字段联合检索（标题 / 正文 / AI 摘要），严格隔离无权访问的私有空间
     */
    public List<KbDoc> searchDocs(Long spaceId, String keyword) {
        Long userId = requireCurrentUserId();
        LambdaQueryWrapper<KbDoc> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(KbDoc.class, info -> !"content_md".equals(info.getColumn()));

        if (spaceId != null) {
            // 指定空间检索：校验该空间可读权限
            assertSpaceReadable(spaceId, userId);
            wrapper.eq(KbDoc::getSpaceId, spaceId);
        } else {
            // 全局检索：仅限当前用户有权访问的空间（自有空间 + 团队公开空间）
            List<Long> accessibleSpaceIds = getAccessibleSpaceIds(userId);
            if (accessibleSpaceIds.isEmpty()) {
                return Collections.emptyList();
            }
            wrapper.in(KbDoc::getSpaceId, accessibleSpaceIds);
        }

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w
                    .like(KbDoc::getTitle, kw)
                    .or().like(KbDoc::getContentMd, kw)
                    .or().like(KbDoc::getAiSummary, kw)
            );
        }

        wrapper.orderByDesc(KbDoc::getUpdatedAt)
                .orderByDesc(KbDoc::getId);
        return kbDocMapper.selectList(wrapper);
    }

    // ==================== 私有校验与辅助方法 ====================

    private Long requireCurrentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或登录状态已失效");
        }
        return userId;
    }

    private KbDoc requireDocExists(Long docId) {
        KbDoc doc = kbDocMapper.selectById(docId);
        if (doc == null) {
            throw new BusinessException(ErrorCode.DOC_NOT_FOUND, "文档不存在或已被删除");
        }
        return doc;
    }

    private KbSpace requireSpaceExists(Long spaceId) {
        KbSpace space = kbSpaceMapper.selectById(spaceId);
        if (space == null) {
            throw new BusinessException(ErrorCode.SPACE_NOT_FOUND, "所属知识空间不存在或已被删除");
        }
        return space;
    }

    /** 校验读权限：空间公开 OR 当前用户是空间所有者 */
    private void assertSpaceReadable(Long spaceId, Long userId) {
        KbSpace space = requireSpaceExists(spaceId);
        boolean isOwner = userId.equals(space.getOwnerId());
        boolean isPublic = Integer.valueOf(VISIBILITY_PUBLIC).equals(space.getVisibility());
        if (!isOwner && !isPublic) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该私有知识空间");
        }
    }

    /** 校验写权限：仅空间所有者可操作 */
    private void assertSpaceWritable(Long spaceId, Long userId) {
        KbSpace space = requireSpaceExists(spaceId);
        if (!userId.equals(space.getOwnerId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅知识空间所有者可进行修改或删除操作");
        }
    }

    /** 获取当前用户可读的所有空间 ID */
    private List<Long> getAccessibleSpaceIds(Long userId) {
        LambdaQueryWrapper<KbSpace> spaceWrapper = new LambdaQueryWrapper<>();
        spaceWrapper.select(KbSpace::getId)
                .eq(KbSpace::getOwnerId, userId)
                .or()
                .eq(KbSpace::getVisibility, VISIBILITY_PUBLIC);
        return kbSpaceMapper.selectList(spaceWrapper)
                .stream()
                .map(KbSpace::getId)
                .toList();
    }
}