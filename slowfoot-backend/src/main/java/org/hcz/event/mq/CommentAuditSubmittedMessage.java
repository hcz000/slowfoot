package org.hcz.event.mq;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hcz.event.audit.CommentAuditSubmittedEvent;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CommentAuditSubmittedMessage {

    private String eventId;
    private Long taskId;
    private Long commentId;

    public static CommentAuditSubmittedMessage from(CommentAuditSubmittedEvent event) {
        return new CommentAuditSubmittedMessage(event.getEventId(), event.getTaskId(), event.getCommentId());
    }
}
