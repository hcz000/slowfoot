package org.hcz.entity.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;


// 多图片作品发布请求。

public record ImageWorkPublishRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 255, message = "标题最多 255 个字符")
        String title,
        @Size(max = 1000, message = "简介最多 1000 个字符")
        String description,
        @NotNull(message = "作品分类不能为空")
        @Positive(message = "作品分类参数不正确")
        Long typeId,
        @Size(max = 255, message = "标签内容过长")
        String labelNames,
        Boolean openState,
        @NotEmpty(message = "至少需要一张图片")
        @Size(max = 50, message = "单个作品最多上传 50 张图片")
        List<@NotNull(message = "图片文件不能为空") @Positive(message = "图片文件参数不正确") Long> imageFileIds
) {
}
