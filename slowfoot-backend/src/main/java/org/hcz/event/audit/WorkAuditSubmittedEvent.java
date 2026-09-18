package org.hcz.event.audit;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

// 作品审核提交事件
@Getter
public class WorkAuditSubmittedEvent extends ApplicationEvent {

    private final String eventId;
    private final Long taskId;
    private final Long workId;

    public WorkAuditSubmittedEvent(Object source, Long taskId, String eventId, Long workId) {
        super(source);
        this.eventId = eventId == null ? UUID.randomUUID().toString() : eventId;
        this.taskId = taskId;
        this.workId = workId;
    }
}
