package com.auradoc.auradocbackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SpaceRequest {
    @NotBlank(message = "空间名称不能为空")
    private String name;
    private String description;
    private Integer visibility = 0; // 0-仅自己可见 1-团队公开
}