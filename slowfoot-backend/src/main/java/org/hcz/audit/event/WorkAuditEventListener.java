package org.hcz.audit.event;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.hcz.audit.service.WorkAuditTaskService;
import org.hcz.audit.service.WorkPublishAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 作品审核完成事件监听器
@Component
@RocketMQMessageListener(
        topic = WorkAuditMessaging.SUBMITTED_TOPIC,
        selectorExpression = WorkAuditMessaging.WORK_AUDIT_TAG,
        consumerGroup = "${rocketmq.consumer.groups.work-audit}")
public class WorkAuditEventListener implements RocketMQListener<WorkAuditSubmittedMessage> {

    private static final Logger log = LoggerFactory.getLogger(WorkAuditEventListener.class);

    private final WorkPublishAuditService auditService;
    private final WorkAuditTaskService taskService;

    public WorkAuditEventListener(WorkPublishAuditService auditService,
                                  WorkAuditTaskService taskService) {
        this.auditService = auditService;
        this.taskService = taskService;
    }

    @Override
    public void onMessage(WorkAuditSubmittedMessage message) {
        WorkAuditSubmittedEvent event = new WorkAuditSubmittedEvent(
                this, message.getTaskId(), message.getEventId(), message.getWorkId());
        try {
            auditService.audit(event);
        } catch (Exception e) {
            log.error("Work audit task failed, taskId={}", event.getTaskId(), e);
            taskService.markFailure(event.getTaskId(), e.getMessage());
        }
    }
}
