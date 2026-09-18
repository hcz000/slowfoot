package org.hcz.entity.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class CommentCreateRequest {

    @NotNull
    @JsonAlias("videoId")
    private Long workId;


// Null 或 zero 创建  top-level comment.

    private Long parentId;

    @NotBlank
    @Size(max = 1000)
    private String content;
}
