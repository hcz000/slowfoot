package org.hcz.work.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;


// 视频作品发布/修改请求。id 非空即修改，修改不允许更换视频源。

public record VideoWorkPublishRequest(
        Long id,
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
        @NotNull(message = "视频源不能为空")
        @Positive(message = "视频源参数不正确")
        Long videoFileId,
        @Positive(message = "封面文件参数不正确")
        Long coverFileId
) {
}
