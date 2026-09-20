package org.hcz.messaging.workaudit;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WorkAuditRocketMQPublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final String submittedTopic;
    private final String completedTopic;

    public WorkAuditRocketMQPublisher(
            RocketMQTemplate rocketMQTemplate,
            @Value("${rocketmq.topics.audit-submitted}") String submittedTopic,
            @Value("${rocketmq.topics.audit-completed}") String completedTopic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.submittedTopic = submittedTopic;
        this.completedTopic = completedTopic;
    }

    @EventListener
    public void handle(WorkAuditSubmittedEvent event) {
        rocketMQTemplate.syncSend(submittedTopic + ":" + WorkAuditMessaging.WORK_AUDIT_TAG,
                WorkAuditSubmittedMessage.from(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(WorkAuditCompletedEvent event) {
        rocketMQTemplate.syncSend(completedTopic, WorkAuditCompletedMessage.from(event));
    }
}
