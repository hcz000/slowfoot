package org.hcz.comment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hcz.common.entity.BaseEntity;


//  comment 或 reply attached   作品.

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("work_comment")
public class WorkComment extends BaseEntity {

    private static final long serialVersionUID = 1L;


// 关联的作品 ID。

    @TableField("work_id")
    private Long workId;

    private Long userId;


// Direct parent comment. Zero means  top-level comment.

    private Long parentId;


// Top-level comment ID 用于  load  whole discussion  one query.

    private Long rootId;

    private Long replyToUserId;

    @NotBlank
    private String content;


// Reuses 该 project's AuditStatus 值: 0 passed, 1 pending, 2 rejected, 3 manual.

    private Integer auditStatus;

    private String auditMsg;


// Prevents 重复 互动 notifications after  audit retry.

    private Boolean notificationSent;


// Prevents 重复 audit 结果 notifications.

    private Boolean auditNotificationSent;
}
