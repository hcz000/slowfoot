package org.hcz.listener.mq;

import org.hcz.event.audit.WorkAuditCompletedEvent;
import org.hcz.service.mq.RocketMQEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WorkAuditCompletedRocketMQPublisher {

    private final RocketMQEventPublisher eventPublisher;

    public WorkAuditCompletedRocketMQPublisher(RocketMQEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(WorkAuditCompletedEvent event) {
        eventPublisher.publish(event);
    }
}
