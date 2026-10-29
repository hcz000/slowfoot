package org.hcz.comment.event;

import org.hcz.common.messaging.service.EventOutboxService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CommentAuditRocketMQPublisher {

    private final EventOutboxService outbox;
    private final String topic;

    public CommentAuditRocketMQPublisher(EventOutboxService outbox,
                                         @Value("${rocketmq.topics.audit-submitted}") String topic) {
        this.outbox = outbox;
        this.topic = topic;
    }

    @EventListener
    public void handle(CommentAuditSubmittedEvent event) {
        outbox.enqueue(event.getEventId(), "COMMENT_AUDIT_SUBMITTED", topic,
                CommentAuditMessaging.COMMENT_AUDIT_TAG, CommentAuditSubmittedMessage.from(event));
    }
}
