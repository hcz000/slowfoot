package org.hcz.service.audit;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.hcz.constant.AuditStatus;
import org.hcz.constant.NotificationType;
import org.hcz.entity.audit.WorkAuditTask;
import org.hcz.entity.work.Work;
import org.hcz.mapper.audit.WorkAuditTaskMapper;
import org.hcz.mapper.work.WorkMapper;
import org.hcz.service.notification.NotificationService;
import org.springframework.stereotype.Service;


// Persists 并 retries 作品 audit outbox tasks.

@Service
public class WorkAuditTaskService
        extends AbstractAuditTaskService<WorkAuditTaskMapper, WorkAuditTask> {

    private static final String RETRY_EXHAUSTED_MESSAGE = "自动审核多次失败，请等待人工审核";

    private final WorkMapper workMapper;
    private final NotificationService notificationService;

    public WorkAuditTaskService(WorkMapper workMapper, NotificationService notificationService) {
        this.workMapper = workMapper;
        this.notificationService = notificationService;
    }


// work.id 在三种作品类型间唯一，因此不需要 work_type 过滤。
// 条件更新命中才发通知：并发或管理员已抢先复审时 update 返回 0，不重复打扰作者。
// 刻意不动 audit_notification_sent —— 那个标记留给最终结论，这样人工复审放行/驳回时还能再通知一次。

    @Override
    protected void markBusinessFailed(WorkAuditTask task) {
        int updated = workMapper.update(null, new UpdateWrapper<Work>()
                .eq("id", task.getWorkId())
                .eq("audit_status", AuditStatus.PROCESS.code())
                .set("audit_status", AuditStatus.MANUAL.code())
                .set("audit_msg", RETRY_EXHAUSTED_MESSAGE));
        if (updated != 1) {
            return;
        }
        Work work = workMapper.selectById(task.getWorkId());
        if (work == null) {
            return;
        }
        notificationService.publish(work.getUserId(), null, NotificationType.WORK_AUDIT,
                work.getId(), work.getId(), "自动审核多次未完成，你的作品已转人工审核，请耐心等待");
    }

    @Override
    protected String fallbackErrorMessage() {
        return "作品审核任务执行失败";
    }
}
