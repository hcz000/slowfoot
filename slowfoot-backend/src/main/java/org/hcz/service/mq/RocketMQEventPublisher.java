package org.hcz.service.mq;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.hcz.event.audit.CommentAuditSubmittedEvent;
import org.hcz.event.mq.CommentAuditSubmittedMessage;
import org.hcz.event.mq.RocketMQTopics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RocketMQEventPublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final String auditSubmittedTopic;

    public RocketMQEventPublisher(
            RocketMQTemplate rocketMQTemplate,
            @Value("${rocketmq.topics.audit-submitted}") String auditSubmittedTopic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.auditSubmittedTopic = auditSubmittedTopic;
    }

    public void publish(CommentAuditSubmittedEvent event) {
        rocketMQTemplate.syncSend(destination(auditSubmittedTopic, RocketMQTopics.COMMENT_AUDIT_TAG),
                CommentAuditSubmittedMessage.from(event));
    }

    private String destination(String topic, String tag) {
        return topic + ":" + tag;
    }
}
