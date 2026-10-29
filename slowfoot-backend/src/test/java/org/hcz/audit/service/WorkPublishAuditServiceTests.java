package org.hcz.audit.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.hcz.audit.entity.VideoAuditJob;
import org.hcz.audit.entity.WorkAuditTask;
import org.hcz.audit.service.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.hcz.common.constant.AuditStatus;
import org.hcz.common.constant.NotificationType;
import org.hcz.file.entity.File;
import org.hcz.audit.vo.AuditResponse;
import org.hcz.work.entity.TextWork;
import org.hcz.work.entity.Work;
import org.hcz.work.entity.WorkAssetEntity;
import org.hcz.work.entity.WorkType;
import org.hcz.audit.event.WorkAuditSubmittedEvent;
import org.hcz.common.messaging.mapper.EventConsumeMapper;
import org.hcz.work.mapper.TextWorkMapper;
import org.hcz.work.mapper.WorkAssetMapper;
import org.hcz.work.mapper.WorkMapper;
import org.hcz.file.service.AliyunOssFileService;
import org.hcz.file.service.FileService;
import org.hcz.notification.service.NotificationService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


// 作品审核编排。构造注入使其可手工构造，不需要 Spring 上下文。

@ExtendWith(MockitoExtension.class)
class WorkPublishAuditServiceTests {

    private static final long WORK_ID = 1L;
    private static final long OWNER_ID = 7L;

    @Mock private WorkMapper workMapper;
    @Mock private WorkAssetMapper workAssetMapper;
    @Mock private TextWorkMapper textWorkMapper;
    @Mock private EventConsumeMapper eventConsumeMapper;
    @Mock private FileService fileService;
    @Mock private MediaAuditService mediaAuditService;
    @Mock private VideoAuditJobService videoAuditJobService;
    @Mock private WorkAuditTaskService workAuditTaskService;
    @Mock private NotificationService notificationService;
    @Mock private AliyunOssFileService ossFileService;
    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @Mock private ThreadPoolTaskExecutor auditExecutor;


// LambdaUpdateWrapper 通过 MyBatis-Plus 的 lambda 缓存解析列名，而缓存只在实体被
// TableInfoHelper 处理过之后才有。纯单测没有 MyBatis 启动过程，这里手工补上。

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Work.class);
    }

    private WorkPublishAuditService service;

    @BeforeEach
    void setUp() {
        service = new WorkPublishAuditService(workMapper, workAssetMapper, textWorkMapper, eventConsumeMapper,
                fileService, ossFileService, mediaAuditService, videoAuditJobService,
                workAuditTaskService, notificationService, applicationEventPublisher, auditExecutor);
    }

    // ---------- inspect：审核判定与消息收敛 ----------

    @Test
    void inspectFoldsFiftyRejectedImagesIntoColumnSafeMessage() {
        stubImageWork(50);
        stubPassingTitle();
        when(mediaAuditService.auditImage(anyString()))
                .thenReturn(new AuditResponse(AuditStatus.PASS.code(), "内容未通过审核"));

        WorkPublishAuditService.AuditSummary summary = service.inspect(work(WorkType.IMAGE), null);

        assertEquals(AuditStatus.PASS.code(), summary.status());
        assertTrue(summary.message().length() <= 500, summary.message());
        assertTrue(summary.message().contains("图片 1：内容未通过审核"), summary.message());
        assertTrue(summary.message().contains("图片 10：内容未通过审核"), summary.message());
        assertTrue(summary.message().contains("另有 40 项未通过审核"), summary.message());
    }

    @Test
    void inspectTruncatesLongProviderMessagesToColumnLength() {
        stubImageWork(3);
        stubPassingTitle();
        when(mediaAuditService.auditImage(anyString()))
                .thenReturn(new AuditResponse(AuditStatus.PASS.code(), "违".repeat(400)));

        WorkPublishAuditService.AuditSummary summary = service.inspect(work(WorkType.IMAGE), null);

        assertEquals(AuditStatus.PASS.code(), summary.status());
        assertEquals(500, summary.message().length());
    }

    @Test
    void inspectPassesWhenEverythingPasses() {
        stubImageWork(1);
        stubPassingTitle();
        when(mediaAuditService.auditImage(anyString()))
                .thenReturn(new AuditResponse(AuditStatus.SUCCESS.code(), "正常"));

        WorkPublishAuditService.AuditSummary summary = service.inspect(work(WorkType.IMAGE), null);

        assertEquals(AuditStatus.SUCCESS.code(), summary.status());
        assertEquals("通过", summary.message());
    }

    @Test
    void inspectReportsTitleFailure() {
        stubImageWork(1);
        when(fileService.getById(anyLong())).thenReturn(file());
        when(mediaAuditService.auditText("标题"))
                .thenReturn(new AuditResponse(AuditStatus.PASS.code(), "含垃圾信息"));
        when(mediaAuditService.auditImage(anyString()))
                .thenReturn(new AuditResponse(AuditStatus.SUCCESS.code(), "正常"));

        WorkPublishAuditService.AuditSummary summary = service.inspect(work(WorkType.IMAGE), null);

        assertEquals(AuditStatus.PASS.code(), summary.status());
        assertEquals("标题：含垃圾信息", summary.message());
    }


// 最高价值的回归测试：正文只存在于 text_work（作品.content 是 @TableField(exist = false)），
// 异步化后若忘记重新读库，正文会被静默漏审。

    @Test
    void inspectReadsTextContentFromTextWork() {
        when(textWorkMapper.selectById(WORK_ID)).thenReturn(textWork("正文"));
        when(mediaAuditService.auditText("标题"))
                .thenReturn(new AuditResponse(AuditStatus.SUCCESS.code(), "正常"));
        when(mediaAuditService.auditText("正文"))
                .thenReturn(new AuditResponse(AuditStatus.SUCCESS.code(), "正常"));

        WorkPublishAuditService.AuditSummary summary = service.inspect(work(WorkType.TEXT), null);

        assertEquals(AuditStatus.SUCCESS.code(), summary.status());
        verify(mediaAuditService).auditText("正文");
    }

    @Test
    void inspectFoldsTitleDescriptionAndContentFailures() {
        when(textWorkMapper.selectById(WORK_ID)).thenReturn(textWork("正文"));
        when(mediaAuditService.auditText("标题"))
                .thenReturn(new AuditResponse(AuditStatus.PASS.code(), "涉政"));
        when(mediaAuditService.auditText("简介"))
                .thenReturn(new AuditResponse(AuditStatus.PASS.code(), "辱骂"));
        when(mediaAuditService.auditText("正文"))
                .thenReturn(new AuditResponse(AuditStatus.PASS.code(), "色情"));

        Work work = work(WorkType.TEXT);
        work.setDescription("简介");

        WorkPublishAuditService.AuditSummary summary = service.inspect(work, null);

        assertEquals(AuditStatus.PASS.code(), summary.status());
        assertEquals("标题：涉政\n简介：辱骂\n正文：色情", summary.message());
    }

    // ---------- audit：发件箱消费、守卫与通知 ----------

    @Test
    void auditSkipsWhenTheEventWasAlreadyConsumed() {
        when(eventConsumeMapper.insertIgnore(anyString(), eq("work-audit"))).thenReturn(0);

        service.audit(event());

        verifyNoInteractions(workMapper);
        verifyNoInteractions(notificationService);
    }


// audit_status 已经不是 PROCESS（例如管理员抢先复审）时不该回写。

    @Test
    void auditWritesBackOnlyWhileStillProcessing() {
        stubConsumableEvent();
        when(workMapper.selectById(WORK_ID)).thenReturn(work(WorkType.IMAGE));
        stubImageWork(1);
        stubPassingTitle();
        when(mediaAuditService.auditImage(anyString()))
                .thenReturn(new AuditResponse(AuditStatus.SUCCESS.code(), "正常"));
        when(workMapper.update(isNull(), any())).thenReturn(0);

        service.audit(event());

        verify(notificationService, never()).publish(any(), any(), any(), any(), any(), any());
        verify(workAuditTaskService).markSuccess(3L);
    }

    @Test
    void auditNotifiesTheAuthorOnce() {
        stubConsumableEvent();
        when(workMapper.selectById(WORK_ID)).thenReturn(work(WorkType.IMAGE));
        stubImageWork(1);
        stubPassingTitle();
        when(mediaAuditService.auditImage(anyString()))
                .thenReturn(new AuditResponse(AuditStatus.SUCCESS.code(), "正常"));
        when(workMapper.update(isNull(), any())).thenReturn(1);

        service.audit(event());

        verify(notificationService, times(1)).publish(eq(OWNER_ID), isNull(),
                eq(NotificationType.WORK_AUDIT), eq(WORK_ID), eq(WORK_ID), anyString());
        verify(workAuditTaskService).markSuccess(3L);
    }

    @Test
    void auditMarksTheWorkSuccessfulWhenItVanished() {
        stubConsumableEvent();
        when(workMapper.selectById(WORK_ID)).thenReturn(null);

        service.audit(event());

        verify(workAuditTaskService).markSuccess(3L);
        verify(notificationService, never()).publish(any(), any(), any(), any(), any(), any());
    }

    // ---------- 视频两阶段：提交不落结论 / 即时结论回归 / completeVideoAudit ----------


// 最高价值的回归测试：提交成功后不得回写作品结论，只能落 job；
// 否则作品会在云端结论出来前被错误地定为「通过」。

    @Test
    void videoSubmissionPersistsJobWithoutWritingConclusion() {
        stubConsumableEvent();
        when(workMapper.selectById(WORK_ID)).thenReturn(videoWork());
        when(fileService.getById(anyLong())).thenReturn(file());
        when(ossFileService.getAccessUrl(anyString())).thenReturn("https://oss/x");
        when(mediaAuditService.submitVideo("https://oss/x")).thenReturn("aliyun-task-1");
        when(mediaAuditService.auditImage(anyString()))
                .thenReturn(new AuditResponse(AuditStatus.SUCCESS.code(), "正常"));
        when(mediaAuditService.auditText("标题"))
                .thenReturn(new AuditResponse(AuditStatus.SUCCESS.code(), "正常"));

        service.audit(event());

        verify(videoAuditJobService).save(any(VideoAuditJob.class));
        verify(workMapper, never()).update(any(), any());
        verify(workAuditTaskService).markSuccess(3L);
    }

    @Test
    void submittedJobCarriesTaskIdAndFailureSnapshot() {
        stubConsumableEvent();
        when(workMapper.selectById(WORK_ID)).thenReturn(videoWork());
        when(fileService.getById(anyLong())).thenReturn(file());
        when(ossFileService.getAccessUrl(anyString())).thenReturn("https://oss/x");
        when(mediaAuditService.submitVideo("https://oss/x")).thenReturn("aliyun-task-1");
        when(mediaAuditService.auditImage(anyString()))
                .thenReturn(new AuditResponse(AuditStatus.PASS.code(), "涉政"));
        when(mediaAuditService.auditText("标题"))
                .thenReturn(new AuditResponse(AuditStatus.SUCCESS.code(), "正常"));

        service.audit(event());

        var captor = org.mockito.ArgumentCaptor.forClass(VideoAuditJob.class);
        verify(videoAuditJobService).save(captor.capture());
        VideoAuditJob job = captor.getValue();
        assertEquals(WORK_ID, job.getWorkId());
        assertEquals(3L, job.getAuditTaskId());
        assertEquals("event-1", job.getEventId());
        assertEquals("aliyun-task-1", job.getAliyunTaskId());
        assertEquals("封面有违规行为: 涉政", job.getPendingFailures());
        assertNull(job.getNewState());
    }


// 审核开关关闭（submitVideo 返回 null）时保持旧语义：直接写终局结论，不落 job。

    @Test
    void videoWritesImmediateConclusionWhenAuditSwitchIsOff() {
        stubConsumableEvent();
        when(workMapper.selectById(WORK_ID)).thenReturn(videoWork());
        stubPassingTitle();
        when(ossFileService.getAccessUrl(anyString())).thenReturn("https://oss/x");
        when(mediaAuditService.submitVideo("https://oss/x")).thenReturn(null);
        when(mediaAuditService.auditImage(anyString()))
                .thenReturn(new AuditResponse(AuditStatus.SUCCESS.code(), "正常"));
        when(workMapper.update(isNull(), any())).thenReturn(1);

        service.audit(event());

        verify(videoAuditJobService, never()).save(any());
        verify(workMapper, atLeastOnce()).update(isNull(), any());
        verify(workAuditTaskService).markSuccess(3L);
    }


// 纯元数据修改不重跑昂贵的视频审核：不提交、不审封面，结论即时写出且不推流。

    @Test
    void metadataOnlyVideoEditSkipsVideoResubmission() {
        Work work = videoWork();
        work.setDescription("新简介");
        stubConsumableEvent();
        when(workMapper.selectById(WORK_ID)).thenReturn(work);
        WorkAuditTask task = task(false, false, false, "标题", null);
        when(workAuditTaskService.getById(3L)).thenReturn(task);
        when(mediaAuditService.auditText("新简介"))
                .thenReturn(new AuditResponse(AuditStatus.SUCCESS.code(), "正常"));
        when(workMapper.update(isNull(), any())).thenReturn(1);

        service.audit(event());

        verify(mediaAuditService, never()).submitVideo(any());
        verify(mediaAuditService, never()).auditImage(any());
        verify(videoAuditJobService, never()).save(any());
        verify(applicationEventPublisher).publishEvent(
                org.mockito.ArgumentMatchers.<org.hcz.audit.event.WorkAuditCompletedEvent>argThat(
                        e -> !e.isApprovedForPublish() && !e.isRemoveFromFeed()));
        verify(workAuditTaskService).markSuccess(3L);
    }

    @Test
    void completeVideoAuditDiscardsConclusionWhenWorkAlreadyDecided() {
        when(videoAuditJobService.getById(9L)).thenReturn(job(9L, null, null));
        Work decided = videoWork();
        decided.setAuditStatus(AuditStatus.SUCCESS.code());
        when(workMapper.selectById(WORK_ID)).thenReturn(decided);

        service.completeVideoAudit(9L, new AuditResponse(AuditStatus.SUCCESS.code(), "正常"));

        verify(videoAuditJobService).markCompleted(9L);
        verify(workMapper, never()).update(any(), any());
    }


// 第一阶段记录的失败项必须与视频结论合并，不能丢。

    @Test
    void completeVideoAuditMergesPendingFailures() {
        when(videoAuditJobService.getById(9L)).thenReturn(job(9L, "标题：涉政", null));
        when(workMapper.selectById(WORK_ID)).thenReturn(videoWork());
        when(workMapper.update(isNull(), any())).thenReturn(1);

        service.completeVideoAudit(9L, new AuditResponse(AuditStatus.PASS.code(), "色情"));

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(notificationService).publish(eq(OWNER_ID), isNull(),
                eq(NotificationType.WORK_AUDIT), eq(WORK_ID), eq(WORK_ID), captor.capture());
        assertTrue(captor.getValue().contains("标题：涉政"));
        assertTrue(captor.getValue().contains("视频有违规行为: 色情"));
        verify(videoAuditJobService).markCompleted(9L);
    }

    @Test
    void completeVideoAuditPassesAndTriggersPublish() {
        when(videoAuditJobService.getById(9L)).thenReturn(job(9L, null, null));
        when(workMapper.selectById(WORK_ID)).thenReturn(videoWork());
        when(workMapper.update(isNull(), any())).thenReturn(1);

        service.completeVideoAudit(9L, new AuditResponse(AuditStatus.SUCCESS.code(), "正常"));

        verify(applicationEventPublisher).publishEvent(
                org.mockito.ArgumentMatchers.<org.hcz.audit.event.WorkAuditCompletedEvent>argThat(
                        e -> e.isApprovedForPublish() && !e.isRemoveFromFeed()));
        verify(videoAuditJobService).markCompleted(9L);
    }

    @Test
    void transferVideoAuditToManualOnlyOverridesProcessing() {
        when(workMapper.update(isNull(), any())).thenReturn(0);

        service.transferVideoAuditToManual(WORK_ID, "自动审核多次失败，请等待人工审核");

        verify(workMapper, never()).selectById(anyLong());
        verifyNoInteractions(notificationService);
    }

    // ---------- publishAuditResult：人工复审共用入口 ----------

    @Test
    void publishAuditResultSkipsUnchangedStatus() {
        service.publishAuditResult(work(AuditStatus.SUCCESS.code()), AuditStatus.SUCCESS.code());

        verifyNoInteractions(workMapper);
        verifyNoInteractions(notificationService);
    }


// 条件更新匹配 0 行 = 已经通知过，不再重复发。

    @Test
    void publishAuditResultDoesNotNotifyTwice() {
        when(workMapper.update(isNull(), any())).thenReturn(0);

        service.publishAuditResult(work(AuditStatus.SUCCESS.code()), AuditStatus.PROCESS.code());

        verify(notificationService, never()).publish(any(), any(), any(), any(), any(), any());
    }

    // ---------- 夹具 ----------

    private WorkAuditSubmittedEvent event() {
        return new WorkAuditSubmittedEvent(this, 3L, "event-1", WORK_ID);
    }

    private void stubConsumableEvent() {
        when(eventConsumeMapper.insertIgnore("event-1", "work-audit")).thenReturn(1);
    }

    private void stubPassingTitle() {
        when(mediaAuditService.auditText("标题")).thenReturn(new AuditResponse(AuditStatus.SUCCESS.code(), "正常"));
        when(fileService.getById(anyLong())).thenReturn(file());
    }

    private void stubImageWork(int assetCount) {
        when(workAssetMapper.selectList(any())).thenReturn(assets(assetCount));
    }

    private Work work(WorkType type) {
        return work(type, AuditStatus.PROCESS.code());
    }

    private Work work(Integer auditStatus) {
        return work(WorkType.IMAGE, auditStatus);
    }

    private Work work(WorkType type, Integer auditStatus) {
        Work work = new Work();
        work.setId(WORK_ID);
        work.setUserId(OWNER_ID);
        work.setWorkType(type.name());
        work.setTitle("标题");
        work.setAuditStatus(auditStatus);
        return work;
    }

    private TextWork textWork(String content) {
        TextWork textWork = new TextWork();
        textWork.setId(WORK_ID);
        textWork.setContent(content);
        return textWork;
    }

    private List<WorkAssetEntity> assets(int count) {
        List<WorkAssetEntity> assets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            WorkAssetEntity asset = new WorkAssetEntity();
            asset.setWorkId(WORK_ID);
            asset.setFileId(100L + i);
            asset.setSortOrder(i);
            assets.add(asset);
        }
        return assets;
    }

    private Work videoWork() {
        Work work = work(WorkType.VIDEO);
        work.setPrimaryFileId(500L);
        work.setCoverFileId(501L);
        return work;
    }

    private WorkAuditTask task(Boolean isAdd, Boolean oldState, Boolean newState,
                               String oldTitle, String oldDescription) {
        WorkAuditTask task = new WorkAuditTask();
        task.setWorkId(WORK_ID);
        task.setIsAdd(isAdd);
        task.setOldState(oldState);
        task.setNewState(newState);
        task.setOldTitle(oldTitle);
        task.setOldDescription(oldDescription);
        return task;
    }

    private VideoAuditJob job(Long id, String pendingFailures, Boolean newState) {
        VideoAuditJob job = new VideoAuditJob();
        job.setId(id);
        job.setWorkId(WORK_ID);
        job.setFileId(500L);
        job.setAliyunTaskId("aliyun-task-1");
        job.setPendingFailures(pendingFailures);
        job.setNewState(newState);
        return job;
    }

    private File file() {
        File file = new File();
        file.setFileKey("uploads/x.jpg");
        return file;
    }
}
