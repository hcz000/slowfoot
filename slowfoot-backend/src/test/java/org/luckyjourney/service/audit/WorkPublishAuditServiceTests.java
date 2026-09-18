package org.hcz.service.audit;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.hcz.service.audit.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.hcz.constant.AuditStatus;
import org.hcz.constant.NotificationType;
import org.hcz.entity.File;
import org.hcz.entity.response.AuditResponse;
import org.hcz.entity.work.TextWork;
import org.hcz.entity.work.Work;
import org.hcz.entity.work.WorkAssetEntity;
import org.hcz.entity.work.WorkType;
import org.hcz.event.audit.WorkAuditSubmittedEvent;
import org.hcz.mapper.audit.EventConsumeMapper;
import org.hcz.mapper.work.TextWorkMapper;
import org.hcz.mapper.work.WorkAssetMapper;
import org.hcz.mapper.work.WorkMapper;
import org.hcz.service.file.AliyunOssFileService;
import org.hcz.service.file.FileService;
import org.hcz.service.notification.NotificationService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    @Mock private TextAuditService textAuditService;
    @Mock private ImageAuditService imageAuditService;
    @Mock private WorkAuditTaskService workAuditTaskService;
    @Mock private NotificationService notificationService;
    @Mock private AliyunOssFileService ossFileService;
    @Mock private VideoFileAuditService videoFileAuditService;
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
                fileService, ossFileService, textAuditService, imageAuditService, videoFileAuditService,
                workAuditTaskService, notificationService, applicationEventPublisher, auditExecutor);
    }

    // ---------- inspect：审核判定与消息收敛 ----------

    @Test
    void inspectFoldsFiftyRejectedImagesIntoColumnSafeMessage() {
        stubImageWork(50);
        stubPassingTitle();
        when(imageAuditService.audit(anyString()))
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
        when(imageAuditService.audit(anyString()))
                .thenReturn(new AuditResponse(AuditStatus.PASS.code(), "违".repeat(400)));

        WorkPublishAuditService.AuditSummary summary = service.inspect(work(WorkType.IMAGE), null);

        assertEquals(AuditStatus.PASS.code(), summary.status());
        assertEquals(500, summary.message().length());
    }

    @Test
    void inspectPassesWhenEverythingPasses() {
        stubImageWork(1);
        stubPassingTitle();
        when(imageAuditService.audit(anyString()))
                .thenReturn(new AuditResponse(AuditStatus.SUCCESS.code(), "正常"));

        WorkPublishAuditService.AuditSummary summary = service.inspect(work(WorkType.IMAGE), null);

        assertEquals(AuditStatus.SUCCESS.code(), summary.status());
        assertEquals("通过", summary.message());
    }

    @Test
    void inspectReportsTitleFailure() {
        stubImageWork(1);
        when(fileService.getById(anyLong())).thenReturn(file());
        when(textAuditService.audit("标题"))
                .thenReturn(new AuditResponse(AuditStatus.PASS.code(), "含垃圾信息"));
        when(imageAuditService.audit(anyString()))
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
        when(textAuditService.audit("标题"))
                .thenReturn(new AuditResponse(AuditStatus.SUCCESS.code(), "正常"));
        when(textAuditService.audit("正文"))
                .thenReturn(new AuditResponse(AuditStatus.SUCCESS.code(), "正常"));

        WorkPublishAuditService.AuditSummary summary = service.inspect(work(WorkType.TEXT), null);

        assertEquals(AuditStatus.SUCCESS.code(), summary.status());
        verify(textAuditService).audit("正文");
    }

    @Test
    void inspectFoldsTitleDescriptionAndContentFailures() {
        when(textWorkMapper.selectById(WORK_ID)).thenReturn(textWork("正文"));
        when(textAuditService.audit("标题"))
                .thenReturn(new AuditResponse(AuditStatus.PASS.code(), "涉政"));
        when(textAuditService.audit("简介"))
                .thenReturn(new AuditResponse(AuditStatus.PASS.code(), "辱骂"));
        when(textAuditService.audit("正文"))
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
        when(imageAuditService.audit(anyString()))
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
        when(imageAuditService.audit(anyString()))
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
        when(textAuditService.audit("标题")).thenReturn(new AuditResponse(AuditStatus.SUCCESS.code(), "正常"));
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

    private File file() {
        File file = new File();
        file.setFileKey("uploads/x.jpg");
        return file;
    }
}
