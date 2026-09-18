package org.hcz.event.mq;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hcz.event.audit.WorkAuditCompletedEvent;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkAuditCompletedMessage {

    private String eventId;
    private Long workId;
    private Long userId;
    private Long createdTime;
    private boolean approvedForPublish;
    private boolean removeFromFeed;

    public static WorkAuditCompletedMessage from(WorkAuditCompletedEvent event) {
        return new WorkAuditCompletedMessage(
                event.getEventId(),
                event.getWorkId(),
                event.getUserId(),
                event.getCreatedTime(),
                event.isApprovedForPublish(),
                event.isRemoveFromFeed());
    }
}
