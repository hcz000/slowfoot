package org.hcz.entity.notification;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hcz.entity.BaseEntity;


// 站内通知。

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("user_notification")
public class Notification extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String eventId;


// 接收人。

    private Long userId;


// 触发通知的用户；系统通知时为空。

    private Long actorId;


// 通知类型：LIKE / FAVORITE / 关注 / COMMENT / REPLY / COMMENT_AUDIT / WORK_AUDIT。

    private String type;

    private Long targetId;


// 关联的作品 ID。

    @TableField("work_id")
    private Long workId;

    private String content;

    private Boolean readState;

    @TableField(exist = false)
    private String actorName;

    @TableField(exist = false)
    private Long actorAvatar;
}
