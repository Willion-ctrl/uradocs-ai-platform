package com.auradoc.auradocbackend.contraller;

import com.auradoc.auradocbackend.common.BusinessException;
import com.auradoc.auradocbackend.common.ErrorCode;
import com.auradoc.auradocbackend.common.Result;
import com.auradoc.auradocbackend.entity.KbDoc;
import com.auradoc.auradocbackend.mapper.KbDocMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class KbDocController {

    private final KbDocMapper kbDocMapper;

    @GetMapping("/{id}")
    public Result<KbDoc> getDocById(@PathVariable Long id) {
        KbDoc doc = kbDocMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(ErrorCode.DOC_NOT_FOUND);
        }
        return Result.success(doc);
    }
}