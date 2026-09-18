package org.hcz.listener.audit;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.hcz.event.audit.CommentAuditSubmittedEvent;
import org.hcz.event.mq.CommentAuditSubmittedMessage;
import org.hcz.event.mq.RocketMQTopics;
import org.hcz.service.audit.CommentAuditService;
import org.hcz.service.audit.CommentAuditTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 评论审核完成事件监听器
@Component
@RocketMQMessageListener(
        topic = RocketMQTopics.AUDIT_SUBMITTED,
        selectorExpression = RocketMQTopics.COMMENT_AUDIT_TAG,
        consumerGroup = "${rocketmq.consumer.groups.comment-audit}")
public class CommentAuditEventListener implements RocketMQListener<CommentAuditSubmittedMessage> {

    private static final Logger log = LoggerFactory.getLogger(CommentAuditEventListener.class);

    private final CommentAuditService auditService;
    private final CommentAuditTaskService taskService;

    public CommentAuditEventListener(CommentAuditService auditService,
                                     CommentAuditTaskService taskService) {
        this.auditService = auditService;
        this.taskService = taskService;
    }

    @Override
    public void onMessage(CommentAuditSubmittedMessage message) {
        CommentAuditSubmittedEvent event = new CommentAuditSubmittedEvent(
                this, message.getTaskId(), message.getEventId(), message.getCommentId());
        try {
            auditService.audit(event);
        } catch (Exception e) {
            log.error("Comment audit task failed, taskId={}", event.getTaskId(), e);
            taskService.markFailure(event.getTaskId(), e.getMessage());
        }
    }
}
