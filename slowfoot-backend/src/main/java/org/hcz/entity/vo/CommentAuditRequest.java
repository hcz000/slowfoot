package org.hcz.entity.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentAuditRequest {

    @NotNull
    private Long id;

    @NotNull
    private Integer auditStatus;

    @Size(max = 255)
    private String auditMsg;
}
