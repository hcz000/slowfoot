package org.hcz.entity.audit;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hcz.entity.BaseEntity;

import java.io.Serializable;
import java.util.Date;


// 用于评论文本审核的持久化任务。

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("comment_audit_task")
public class CommentAuditTask extends BaseEntity implements AuditTaskEntity, Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    private Long commentId;
    private Integer status;
    private Integer retryCount;
    private Date nextRetryAt;
    private Date lockedUntil;
    private String lastError;
}
