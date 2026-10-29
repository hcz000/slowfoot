package org.hcz.comment.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
