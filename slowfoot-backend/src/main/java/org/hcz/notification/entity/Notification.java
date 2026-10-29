package org.hcz.notification.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hcz.common.entity.BaseEntity;


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

    @JsonSerialize(using = ToStringSerializer.class)
    private Long actorId;


// 通知类型：LIKE / FAVORITE / 关注 / COMMENT / REPLY / COMMENT_AUDIT / WORK_AUDIT。

    private String type;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;


// 关联的作品 ID。

    @TableField("work_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long workId;

    private String content;

    private Boolean readState;

    @TableField(exist = false)
    private String actorName;

    @TableField(exist = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long actorAvatar;
}
