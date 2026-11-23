package com.auradoc.auradocbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DocRequest {
    @NotNull(message = "所属空间ID不能为空")
    private Long spaceId;
    private Long parentId = 0L;
    @NotBlank(message = "文档标题不能为空")
    private String title;
    private String contentMd = "";
    private Integer status = 1; // 0-草稿 1-已发布
}