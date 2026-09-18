package org.hcz.event.mq;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hcz.event.audit.WorkAuditSubmittedEvent;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkAuditSubmittedMessage {

    private String eventId;
    private Long taskId;
    private Long workId;

    public static WorkAuditSubmittedMessage from(WorkAuditSubmittedEvent event) {
        return new WorkAuditSubmittedMessage(event.getEventId(), event.getTaskId(), event.getWorkId());
    }
}
