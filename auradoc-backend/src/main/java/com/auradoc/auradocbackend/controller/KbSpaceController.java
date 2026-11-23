package com.auradoc.auradocbackend.controller;

import com.auradoc.auradocbackend.common.BusinessException;
import com.auradoc.auradocbackend.common.ErrorCode;
import com.auradoc.auradocbackend.common.Result;
import com.auradoc.auradocbackend.dto.SpaceRequest;
import com.auradoc.auradocbackend.entity.KbSpace;
import com.auradoc.auradocbackend.mapper.KbSpaceMapper;
import com.auradoc.auradocbackend.security.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/spaces")
@RequiredArgsConstructor
public class KbSpaceController {

    private final KbSpaceMapper kbSpaceMapper;

    /** 查询当前用户可见的知识空间（自己创建的 + 团队公开的） */
    @GetMapping
    public Result<List<KbSpace>> listSpaces() {
        Long userId = UserContext.getUserId();
        List<KbSpace> spaces = kbSpaceMapper.selectList(
                new LambdaQueryWrapper<KbSpace>()
                        .eq(KbSpace::getOwnerId, userId)
                        .or()
                        .eq(KbSpace::getVisibility, 1)
                        .orderByDesc(KbSpace::getUpdatedAt)
        );
        return Result.success(spaces);
    }

    /** 创建知识空间 */
    @PostMapping
    public Result<KbSpace> createSpace(@Valid @RequestBody SpaceRequest req) {
        KbSpace space = new KbSpace();
        space.setName(req.getName());
        space.setDescription(req.getDescription() != null ? req.getDescription() : "");
        space.setVisibility(req.getVisibility() != null ? req.getVisibility() : 0);
        space.setOwnerId(UserContext.getUserId());
        kbSpaceMapper.insert(space);
        return Result.success(space);
    }

    /** 逻辑删除知识空间（仅创建者可删） */
    @DeleteMapping("/{id}")
    public Result<Void> deleteSpace(@PathVariable Long id) {
        KbSpace space = kbSpaceMapper.selectById(id);
        if (space == null) {
            throw new BusinessException(ErrorCode.SPACE_NOT_FOUND);
        }
        if (!space.getOwnerId().equals(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        kbSpaceMapper.deleteById(id);
        return Result.success();
    }
}