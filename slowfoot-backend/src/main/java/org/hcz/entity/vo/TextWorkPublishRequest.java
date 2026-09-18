package org.hcz.entity.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;


// 请求 body 用于 正在发布  text 作品.

public record TextWorkPublishRequest(
        @NotBlank(message = "title cannot be blank")
        @Size(max = 255, message = "title is too long")
        String title,
        @Size(max = 1000, message = "description is too long")
        String description,
        @NotBlank(message = "content cannot be blank")
        @Size(max = 100000, message = "content is too long")
        String content,
        @NotNull(message = "work category cannot be null")
        @Positive(message = "work category is invalid")
        Long typeId,
        @Size(max = 255, message = "labels are too long")
        String labelNames,
        Boolean openState
) {
}
