package org.hcz.listener.mq;

import org.hcz.event.notification.NotificationEvent;
import org.hcz.service.mq.RocketMQEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationRocketMQPublisher {

    private final RocketMQEventPublisher eventPublisher;

    public NotificationRocketMQPublisher(RocketMQEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(NotificationEvent event) {
        eventPublisher.publish(event);
    }
}
