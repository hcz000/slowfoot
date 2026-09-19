package org.hcz.service.mq;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.hcz.event.audit.CommentAuditSubmittedEvent;
import org.hcz.event.audit.WorkAuditCompletedEvent;
import org.hcz.event.audit.WorkAuditSubmittedEvent;
import org.hcz.event.mq.CommentAuditSubmittedMessage;
import org.hcz.event.mq.NotificationMessage;
import org.hcz.event.mq.RocketMQTopics;
import org.hcz.event.mq.WorkAuditCompletedMessage;
import org.hcz.event.mq.WorkAuditSubmittedMessage;
import org.hcz.event.mq.WorkInteractionMessage;
import org.hcz.event.notification.NotificationEvent;
import org.hcz.event.work.WorkInteractionEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RocketMQEventPublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final String workInteractionTopic;
    private final String notificationTopic;
    private final String auditSubmittedTopic;
    private final String auditCompletedTopic;

    public RocketMQEventPublisher(
            RocketMQTemplate rocketMQTemplate,
            @Value("${rocketmq.topics.work-interaction}") String workInteractionTopic,
            @Value("${rocketmq.topics.notification}") String notificationTopic,
            @Value("${rocketmq.topics.audit-submitted}") String auditSubmittedTopic,
            @Value("${rocketmq.topics.audit-completed}") String auditCompletedTopic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.workInteractionTopic = workInteractionTopic;
        this.notificationTopic = notificationTopic;
        this.auditSubmittedTopic = auditSubmittedTopic;
        this.auditCompletedTopic = auditCompletedTopic;
    }

    public void publish(WorkInteractionEvent event) {
        rocketMQTemplate.syncSend(destination(workInteractionTopic, RocketMQTopics.INTERACTION_TAG),
                WorkInteractionMessage.from(event));
    }

    public void publish(NotificationEvent event) {
        rocketMQTemplate.syncSend(notificationTopic, NotificationMessage.from(event));
    }

    public void publish(WorkAuditSubmittedEvent event) {
        rocketMQTemplate.syncSend(destination(auditSubmittedTopic, RocketMQTopics.WORK_AUDIT_TAG),
                WorkAuditSubmittedMessage.from(event));
    }

    public void publish(CommentAuditSubmittedEvent event) {
        rocketMQTemplate.syncSend(destination(auditSubmittedTopic, RocketMQTopics.COMMENT_AUDIT_TAG),
                CommentAuditSubmittedMessage.from(event));
    }

    public void publish(WorkAuditCompletedEvent event) {
        rocketMQTemplate.syncSend(auditCompletedTopic, WorkAuditCompletedMessage.from(event));
    }

    private String destination(String topic, String tag) {
        return topic + ":" + tag;
    }
}
