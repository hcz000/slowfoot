package org.hcz.schedul;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hcz.constant.AuditTaskStatus;
import org.hcz.entity.audit.CommentAuditTask;
import org.hcz.messaging.commentaudit.CommentAuditSubmittedEvent;
import org.hcz.service.audit.CommentAuditTaskService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class CommentAuditTaskDispatcher {

    private final CommentAuditTaskService taskService;
    private final ApplicationEventPublisher eventPublisher;

    public CommentAuditTaskDispatcher(CommentAuditTaskService taskService,
                                      ApplicationEventPublisher eventPublisher) {
        this.taskService = taskService;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${audit.dispatch-delay-ms:1000}")
    public void dispatch() {
        Date now = new Date();
        LambdaQueryWrapper<CommentAuditTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.and(x -> x.eq(CommentAuditTask::getStatus, AuditTaskStatus.PENDING.code())
                                .le(CommentAuditTask::getNextRetryAt, now))
                        .or(x -> x.eq(CommentAuditTask::getStatus, AuditTaskStatus.PROCESSING.code())
                                .lt(CommentAuditTask::getLockedUntil, now)))
                .orderByAsc(CommentAuditTask::getId)
                .last("LIMIT 20");

        List<CommentAuditTask> tasks = taskService.list(wrapper);
        for (CommentAuditTask task : tasks) {
            if (!taskService.claim(task.getId(), now)) {
                continue;
            }
            eventPublisher.publishEvent(new CommentAuditSubmittedEvent(
                    this, task.getId(), task.getEventId(), task.getCommentId()));
        }
    }
}
