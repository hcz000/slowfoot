package org.hcz.listener.mq;

import org.hcz.event.work.WorkInteractionEvent;
import org.hcz.service.mq.RocketMQEventPublisher;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WorkInteractionRocketMQPublisher {

    private final RocketMQEventPublisher eventPublisher;

    public WorkInteractionRocketMQPublisher(RocketMQEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Order(Ordered.LOWEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(WorkInteractionEvent event) {
        eventPublisher.publish(event);
    }
}
