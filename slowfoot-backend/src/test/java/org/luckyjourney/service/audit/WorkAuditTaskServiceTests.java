package org.hcz.service.audit;

import org.hcz.service.audit.WorkAuditTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.hcz.constant.AuditStatus;
import org.hcz.constant.NotificationType;
import org.hcz.entity.audit.WorkAuditTask;
import org.hcz.entity.work.Work;
import org.hcz.mapper.work.WorkMapper;
import org.hcz.service.notification.NotificationService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


// 自动审核重试耗尽转向人工审核时的通知行为。

@ExtendWith(MockitoExtension.class)
class WorkAuditTaskServiceTests {

    private static final long WORK_ID = 1L;
    private static final long OWNER_ID = 7L;

    @Mock
    private WorkMapper workMapper;

    @Mock
    private NotificationService notificationService;

    private WorkAuditTaskService service;

    @BeforeEach
    void setUp() {
        service = new WorkAuditTaskService(workMapper, notificationService);
    }

    @Test
    void escalatingToManualNotifiesTheAuthor() {
        when(workMapper.update(isNull(), any())).thenReturn(1);
        when(workMapper.selectById(WORK_ID)).thenReturn(work());

        service.markBusinessFailed(task());

        verify(notificationService).publish(eq(OWNER_ID), isNull(), eq(NotificationType.WORK_AUDIT),
                eq(WORK_ID), eq(WORK_ID), anyString());
    }


// 条件更新没命中说明已被管理员复审或并发处理，不再打扰作者。

    @Test
    void escalationStaysSilentWhenTheWorkWasAlreadyHandled() {
        when(workMapper.update(isNull(), any())).thenReturn(0);

        service.markBusinessFailed(task());

        verifyNoInteractions(notificationService);
        verify(workMapper, never()).selectById(any());
    }

    private WorkAuditTask task() {
        WorkAuditTask task = new WorkAuditTask();
        task.setId(3L);
        task.setWorkId(WORK_ID);
        task.setStatus(AuditStatus.MANUAL.code());
        return task;
    }

    private Work work() {
        Work work = new Work();
        work.setId(WORK_ID);
        work.setUserId(OWNER_ID);
        return work;
    }
}
