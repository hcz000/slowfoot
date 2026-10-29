package org.hcz.audit.scheduler;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.hcz.audit.entity.VideoAuditJob;
import org.hcz.audit.service.MediaAuditService;
import org.hcz.audit.service.VideoAuditJobService;
import org.hcz.audit.service.WorkPublishAuditService;
import org.hcz.audit.vo.AuditResponse;
import org.hcz.common.constant.AuditStatus;
import org.hcz.file.service.AliyunOssFileService;
import org.hcz.file.service.FileService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


// 轮询器只做「状态推进」：每种云端结论对应唯一的状态机迁移，失败预算只在真正出错时消耗。

@ExtendWith(MockitoExtension.class)
class VideoAuditResultPollerTests {

    private static final long JOB_ID = 9L;
    private static final long WORK_ID = 1L;
    private static final long POLL_DELAY_MILLIS = 5_000L;
    private static final long TIMEOUT_MINUTES = 10L;

    @Mock private VideoAuditJobService jobService;
    @Mock private MediaAuditService mediaAuditService;
    @Mock private WorkPublishAuditService workPublishAuditService;
    @Mock private FileService fileService;
    @Mock private AliyunOssFileService ossFileService;

    // LambdaQueryWrapper 解析列名依赖 MyBatis-Plus 的 TableInfo 缓存，纯单测需手工补齐。
    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), VideoAuditJob.class);
    }

    private VideoAuditResultPoller poller;


// list(Wrapper) 与 list(IPage) 重载对裸 any() 有歧义，统一用这个显式定型的匹配器。

    @SuppressWarnings("unchecked")
    private static Wrapper<VideoAuditJob> wrapperAny() {
        return (Wrapper<VideoAuditJob>) org.mockito.ArgumentMatchers.any(Wrapper.class);
    }

    @BeforeEach
    void setUp() {
        poller = new VideoAuditResultPoller(jobService, mediaAuditService, workPublishAuditService,
                fileService, ossFileService, POLL_DELAY_MILLIS, TIMEOUT_MINUTES);
    }

    @Test
    void finishedConclusionCompletesTheJob() {
        VideoAuditJob job = job();
        when(jobService.list(wrapperAny())).thenReturn(List.of(job));
        when(jobService.claimForPoll(eq(JOB_ID), any())).thenReturn(true);
        when(mediaAuditService.fetchVideo("aliyun-task-1"))
                .thenReturn(new MediaAuditService.VideoOutcome(MediaAuditService.VideoState.FINISHED,
                        new AuditResponse(AuditStatus.SUCCESS.code(), "正常")));

        poller.poll();

        verify(workPublishAuditService).completeVideoAudit(eq(JOB_ID), any(AuditResponse.class));
        verify(jobService, never()).reschedule(anyLong(), any());
    }

    @Test
    void pendingConclusionReschedulesWithoutConsumingRetryBudget() {
        VideoAuditJob job = job();
        when(jobService.list(wrapperAny())).thenReturn(List.of(job));
        when(jobService.claimForPoll(eq(JOB_ID), any())).thenReturn(true);
        when(mediaAuditService.fetchVideo("aliyun-task-1"))
                .thenReturn(new MediaAuditService.VideoOutcome(MediaAuditService.VideoState.PENDING, null));

        poller.poll();

        verify(jobService).reschedule(eq(JOB_ID), any(Date.class));
        verify(jobService, never()).markFailure(anyLong(), any());
        verify(workPublishAuditService, never()).completeVideoAudit(anyLong(), any());
    }

    @Test
    void pendingConclusionPastDeadlineTransfersToManual() {
        VideoAuditJob job = job();
        job.setGmtCreated(new Date(System.currentTimeMillis() - (TIMEOUT_MINUTES + 1) * 60_000L));
        when(jobService.list(wrapperAny())).thenReturn(List.of(job));
        when(jobService.claimForPoll(eq(JOB_ID), any())).thenReturn(true);
        when(mediaAuditService.fetchVideo("aliyun-task-1"))
                .thenReturn(new MediaAuditService.VideoOutcome(MediaAuditService.VideoState.PENDING, null));

        poller.poll();

        verify(jobService).expireToManual(eq(JOB_ID), contains("超时"));
        verify(workPublishAuditService).transferVideoAuditToManual(eq(WORK_ID), contains("超时"));
        verify(jobService, never()).reschedule(anyLong(), any());
    }

    @Test
    void lostHandlerResubmitsBeforeQuerying() {
        VideoAuditJob job = job();
        job.setAliyunTaskId(null);
        when(jobService.list(wrapperAny())).thenReturn(List.of(job));
        when(jobService.claimForPoll(eq(JOB_ID), any())).thenReturn(true);
        when(mediaAuditService.submitVideo(any())).thenReturn("aliyun-task-2");
        when(mediaAuditService.fetchVideo("aliyun-task-2"))
                .thenReturn(new MediaAuditService.VideoOutcome(MediaAuditService.VideoState.PENDING, null));

        poller.poll();

        verify(jobService).bindAliyunTask(JOB_ID, "aliyun-task-2");
    }

    @Test
    void closedSwitchDuringPollCompletesAsPassed() {
        VideoAuditJob job = job();
        job.setAliyunTaskId(" ");
        when(jobService.list(wrapperAny())).thenReturn(List.of(job));
        when(jobService.claimForPoll(eq(JOB_ID), any())).thenReturn(true);
        when(mediaAuditService.submitVideo(any())).thenReturn(null);

        poller.poll();

        verify(workPublishAuditService).completeVideoAudit(eq(JOB_ID),
                eq(new AuditResponse(AuditStatus.SUCCESS.code(), "正常")));
        verify(mediaAuditService, never()).fetchVideo(anyString());
    }

    @Test
    void exhaustedFailuresTransferWorkToManual() {
        VideoAuditJob job = job();
        when(jobService.list(wrapperAny())).thenReturn(List.of(job));
        when(jobService.claimForPoll(eq(JOB_ID), any())).thenReturn(true);
        when(mediaAuditService.fetchVideo("aliyun-task-1")).thenThrow(new RuntimeException("boom"));
        when(jobService.markFailure(eq(JOB_ID), any())).thenReturn(true);
        when(jobService.getById(JOB_ID)).thenReturn(job);

        poller.poll();

        verify(workPublishAuditService).transferVideoAuditToManual(eq(WORK_ID), contains("人工"));
        verify(workPublishAuditService, never()).completeVideoAudit(anyLong(), any());
    }

    @Test
    void claimedByOtherInstanceSkipsTheJob() {
        when(jobService.list(wrapperAny())).thenReturn(List.of(job()));
        when(jobService.claimForPoll(eq(JOB_ID), any())).thenReturn(false);

        poller.poll();

        verifyNoQuery();
    }

    private void verifyNoQuery() {
        verify(mediaAuditService, never()).fetchVideo(anyString());
        verify(workPublishAuditService, never()).completeVideoAudit(anyLong(), any());
    }

    private VideoAuditJob job() {
        VideoAuditJob job = new VideoAuditJob();
        job.setId(JOB_ID);
        job.setWorkId(WORK_ID);
        job.setFileId(500L);
        job.setAliyunTaskId("aliyun-task-1");
        job.setGmtCreated(new Date());
        return job;
    }
}
