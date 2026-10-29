package org.hcz.audit.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;


// 管理端作品人工复审请求。

@Data
public class WorkAuditRequest {

    @NotNull
    private Long id;

    @NotNull
    private Integer auditStatus;


// 作品.audit_msg 是 VARCHAR(500)，比 work_comment.audit_msg 宽。

    @Size(max = 500)
    private String auditMsg;
}
