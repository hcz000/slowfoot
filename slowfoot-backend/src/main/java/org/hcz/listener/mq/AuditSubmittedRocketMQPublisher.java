package org.hcz.listener.mq;

import org.hcz.event.audit.CommentAuditSubmittedEvent;
import org.hcz.service.mq.RocketMQEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AuditSubmittedRocketMQPublisher {

    private final RocketMQEventPublisher eventPublisher;

    public AuditSubmittedRocketMQPublisher(RocketMQEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void handle(CommentAuditSubmittedEvent event) {
        eventPublisher.publish(event);
    }
}
