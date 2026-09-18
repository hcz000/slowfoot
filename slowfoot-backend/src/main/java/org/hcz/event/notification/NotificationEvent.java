package org.hcz.event.notification;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

// 通知事件
@Getter
public class NotificationEvent extends ApplicationEvent {

    private final String eventId;
    private final Long userId;
    // 操作者ID
    private final Long actorId;
    private final String type;
    // 目标ID
    private final Long targetId;
    // 视频ID
    private final Long workId;
    // 内容
    private final String content;

    public NotificationEvent(Object source, Long userId, Long actorId, String type,
                             Long targetId, Long workId, String content) {
        this(source, UUID.randomUUID().toString(), userId, actorId, type, targetId, workId, content);
    }

    public NotificationEvent(Object source, String eventId, Long userId, Long actorId, String type,
                             Long targetId, Long workId, String content) {
        super(source);
        this.eventId = eventId == null ? UUID.randomUUID().toString() : eventId;
        this.userId = userId;
        this.actorId = actorId;
        this.type = type;
        this.targetId = targetId;
        this.workId = workId;
        this.content = content;
    }
}
