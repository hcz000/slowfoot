package org.hcz.audit.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hcz.common.constant.AuditTaskStatus;
import org.hcz.audit.entity.WorkAuditTask;
import org.hcz.audit.event.WorkAuditSubmittedEvent;
import org.hcz.audit.service.WorkAuditTaskService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class WorkAuditTaskDispatcher {

    private final WorkAuditTaskService taskService;
    private final ApplicationEventPublisher eventPublisher;

    public WorkAuditTaskDispatcher(WorkAuditTaskService taskService,
                                   ApplicationEventPublisher eventPublisher) {
        this.taskService = taskService;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${audit.dispatch-delay-ms:1000}")
    public void dispatch() {
        Date now = new Date();
        LambdaQueryWrapper<WorkAuditTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.and(x -> x.eq(WorkAuditTask::getStatus, AuditTaskStatus.PENDING.code())
                                .le(WorkAuditTask::getNextRetryAt, now))
                        .or(x -> x.eq(WorkAuditTask::getStatus, AuditTaskStatus.PROCESSING.code())
                                .lt(WorkAuditTask::getLockedUntil, now)))
                .orderByAsc(WorkAuditTask::getId)
                .last("LIMIT 20");

        List<WorkAuditTask> tasks = taskService.list(wrapper);
        for (WorkAuditTask task : tasks) {
            if (!taskService.claim(task.getId(), now)) {
                continue;
            }
            eventPublisher.publishEvent(new WorkAuditSubmittedEvent(
                    this, task.getId(), task.getEventId(), task.getWorkId()));
        }
    }
}
