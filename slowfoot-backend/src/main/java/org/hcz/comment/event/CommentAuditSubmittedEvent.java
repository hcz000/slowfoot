package org.hcz.comment.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

// 评论审核提交事件
@Getter
public class CommentAuditSubmittedEvent extends ApplicationEvent {

    private final String eventId;
    private final Long taskId;
    private final Long commentId;

    public CommentAuditSubmittedEvent(Object source, Long taskId, String eventId, Long commentId) {
        super(source);
        this.eventId = eventId == null ? UUID.randomUUID().toString() : eventId;
        this.taskId = taskId;
        this.commentId = commentId;
    }
}
