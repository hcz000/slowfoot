package org.hcz.messaging.commentaudit;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CommentAuditRocketMQPublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final String topic;

    public CommentAuditRocketMQPublisher(
            RocketMQTemplate rocketMQTemplate,
            @Value("${rocketmq.topics.audit-submitted}") String topic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.topic = topic;
    }

    @EventListener
    public void handle(CommentAuditSubmittedEvent event) {
        rocketMQTemplate.syncSend(topic + ":" + CommentAuditMessaging.COMMENT_AUDIT_TAG,
                CommentAuditSubmittedMessage.from(event));
    }
}
