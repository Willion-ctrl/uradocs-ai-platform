package com.auradoc.auradocbackend.contraller;

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
    public KbDoc getDocById(@PathVariable Long id) {
        return kbDocMapper.selectById(id);
    }
}