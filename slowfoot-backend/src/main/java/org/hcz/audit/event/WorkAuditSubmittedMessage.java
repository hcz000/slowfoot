package org.hcz.audit.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
