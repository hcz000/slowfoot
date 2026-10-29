package org.hcz.audit.event;

import org.hcz.common.messaging.service.EventOutboxService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class WorkAuditRocketMQPublisher {

    private final EventOutboxService outbox;
    private final String submittedTopic;
    private final String completedTopic;

    public WorkAuditRocketMQPublisher(EventOutboxService outbox,
                                      @Value("${rocketmq.topics.audit-submitted}") String submittedTopic,
                                      @Value("${rocketmq.topics.audit-completed}") String completedTopic) {
        this.outbox = outbox;
        this.submittedTopic = submittedTopic;
        this.completedTopic = completedTopic;
    }

    @EventListener
    public void handle(WorkAuditSubmittedEvent event) {
        outbox.enqueue(event.getEventId(), "WORK_AUDIT_SUBMITTED", submittedTopic,
                WorkAuditMessaging.WORK_AUDIT_TAG, WorkAuditSubmittedMessage.from(event));
    }

    @EventListener
    public void handle(WorkAuditCompletedEvent event) {
        outbox.enqueue(event.getEventId(), "WORK_AUDIT_COMPLETED", completedTopic,
                null, WorkAuditCompletedMessage.from(event));
    }
}
