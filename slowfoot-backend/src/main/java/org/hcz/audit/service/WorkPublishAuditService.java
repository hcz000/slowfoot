package org.hcz.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.hcz.common.constant.AuditStatus;
import org.hcz.common.constant.NotificationType;
import org.hcz.file.entity.File;
import org.hcz.audit.entity.VideoAuditJob;
import org.hcz.audit.entity.WorkAuditTask;
import org.hcz.common.constant.AuditTaskStatus;
import org.hcz.audit.vo.AuditResponse;
import org.hcz.work.entity.TextWork;
import org.hcz.work.entity.Work;
import org.hcz.work.entity.WorkAssetEntity;
import org.hcz.work.entity.WorkType;
import org.hcz.audit.event.WorkAuditCompletedEvent;
import org.hcz.audit.event.WorkAuditSubmittedEvent;
import org.hcz.common.exception.AppException;
import org.hcz.common.exception.ErrorCode;
import org.hcz.common.messaging.mapper.EventConsumeMapper;
import org.hcz.work.mapper.TextWorkMapper;
import org.hcz.work.mapper.WorkAssetMapper;
import org.hcz.work.mapper.WorkMapper;
import org.hcz.file.service.AliyunOssFileService;
import org.hcz.file.service.FileService;
import org.hcz.notification.service.NotificationService;
import org.hcz.common.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;


// 作品发布审核：消费 work_audit_task 发件箱，回写审核结论并通知作者。图片 / 文字单阶段完成；
// 视频拆为两阶段——提交云端异步审核 + 轮询结论（VideoAuditResultPoller 经 completeVideoAudit 写回）。

@Service
public class WorkPublishAuditService {

    private static final Logger log = LoggerFactory.getLogger(WorkPublishAuditService.class);


// work.audit_msg 是 VARCHAR(500)，写入前必须收敛到列长度以内。

    private static final int AUDIT_MSG_MAX_LENGTH = 500;

// 审核失败时最多展开的条目数，其余折叠成一行统计。

    private static final int AUDIT_MSG_MAX_ITEMS = 10;
    private static final String PASSED_MESSAGE = "通过";

    private final WorkMapper workMapper;
    private final WorkAssetMapper workAssetMapper;
    private final TextWorkMapper textWorkMapper;
    private final EventConsumeMapper eventConsumeMapper;
    private final FileService fileService;
    private final AliyunOssFileService ossFileService;
    private final MediaAuditService mediaAuditService;
    private final VideoAuditJobService videoAuditJobService;
    private final WorkAuditTaskService workAuditTaskService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ThreadPoolTaskExecutor auditExecutor;


// 本类只依赖 WorkMapper，<b>永远不要注入 WorkService</b>：
// WorkModerationService 依赖本类（人工复审复用 publishAuditResult），
// 反向依赖会构成循环。需要读写作品时直接用 WorkMapper。

    public WorkPublishAuditService(WorkMapper workMapper,
                                   WorkAssetMapper workAssetMapper,
                                   TextWorkMapper textWorkMapper,
                                   EventConsumeMapper eventConsumeMapper,
                                   FileService fileService,
                                   AliyunOssFileService ossFileService,
                                   MediaAuditService mediaAuditService,
                                   VideoAuditJobService videoAuditJobService,
                                   WorkAuditTaskService workAuditTaskService,
                                   NotificationService notificationService,
                                   ApplicationEventPublisher applicationEventPublisher,
                                   @Qualifier("auditEventExecutor") ThreadPoolTaskExecutor auditExecutor) {
        this.workMapper = workMapper;
        this.workAssetMapper = workAssetMapper;
        this.textWorkMapper = textWorkMapper;
        this.eventConsumeMapper = eventConsumeMapper;
        this.fileService = fileService;
        this.ossFileService = ossFileService;
        this.mediaAuditService = mediaAuditService;
        this.videoAuditJobService = videoAuditJobService;
        this.workAuditTaskService = workAuditTaskService;
        this.notificationService = notificationService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.auditExecutor = auditExecutor;
    }

    @Transactional
    public void audit(WorkAuditSubmittedEvent event) {
        if (eventConsumeMapper.insertIgnore(event.getEventId(), "work-audit") == 0) {
            return;
        }

        Work work = workMapper.selectById(event.getWorkId());
        if (work == null || !Objects.equals(work.getAuditStatus(), AuditStatus.PROCESS.code())) {
            workAuditTaskService.markSuccess(event.getTaskId());
            return;
        }

        // 快照随任务落库，异步审核时实读的作品行可能已被后续编辑覆盖。
        WorkAuditTask task = event.getTaskId() == null ? null
                : workAuditTaskService.getById(event.getTaskId());

        if (WorkType.VIDEO.name().equals(work.getWorkType())) {
            auditVideo(work, task, event);
            workAuditTaskService.markSuccess(event.getTaskId());
            return;
        }

        writeConclusion(work, inspect(work, task));
        workAuditTaskService.markSuccess(event.getTaskId());
    }


// 视频作品第一阶段：封面 / 标题 / 简介同步审，视频本体只提交不等待。
// 提交成功即落 job 返回，作品保持“审核中”，终局由 VideoAuditResultPoller 经 completeVideoAudit 写回。
// 提交失败抛 DEPENDENCY_FAILURE 回滚整个事务，由 work_audit_task 的指数退避重试接管（与旧同步实现一致）。

    private void auditVideo(Work work, WorkAuditTask task, WorkAuditSubmittedEvent event) {
        VideoInspectResult result = inspectVideo(work, task);
        if (result.aliyunTaskId() == null) {
            writeConclusion(work, result.summary());
            return;
        }
        VideoAuditJob job = new VideoAuditJob();
        job.setWorkId(work.getId());
        job.setAuditTaskId(event.getTaskId());
        job.setEventId(event.getEventId());
        job.setFileId(work.getPrimaryFileId());
        job.setAliyunTaskId(result.aliyunTaskId());
        job.setPendingFailures(result.pendingFailures());
        job.setNewState(result.newState());
        job.setStatus(AuditTaskStatus.PENDING.code());
        job.setRetryCount(0);
        job.setNextPollAt(new Date());
        videoAuditJobService.save(job);
    }

    // 结论回写：条件更新保证不会覆盖人工已定的结论。
    private void writeConclusion(Work work, AuditSummary summary) {
        LambdaUpdateWrapper<Work> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Work::getId, work.getId())
                .eq(Work::getAuditStatus, AuditStatus.PROCESS.code())
                .set(Work::getAuditStatus, summary.status())
                .set(Work::getAuditMsg, summary.message());
        if (workMapper.update(null, wrapper) > 0) {
            work.setAuditStatus(summary.status());
            work.setAuditMsg(summary.message());
            publishAuditResult(work, AuditStatus.PROCESS.code(),
                    summary.approvedForPublish(), summary.removeFromFeed());
        }
    }


// 人工复审：管理员直接定结论，没有「本次是否重审了视频」这回事。

    public void publishAuditResult(Work work, Integer oldStatus) {
        publishAuditResult(work, oldStatus,
                Objects.equals(work == null ? null : work.getAuditStatus(), AuditStatus.SUCCESS.code()),
                false);
    }


// 审核结论发布：自动审核与人工复审共用，靠 audit_notification_sent 保证只通知一次。
// 真正的推流副作用由 WorkAuditCompletedEventListener 在事务提交后执行。

    public void publishAuditResult(Work work, Integer oldStatus,
                                   boolean approvedForPublish, boolean removeFromFeed) {
        if (work == null || Objects.equals(oldStatus, work.getAuditStatus())) {
            return;
        }
        if (Objects.equals(work.getAuditStatus(), AuditStatus.SUCCESS.code())
                || Objects.equals(work.getAuditStatus(), AuditStatus.PASS.code())) {
            sendAuditNotification(work);
            applicationEventPublisher.publishEvent(new WorkAuditCompletedEvent(
                    this,
                    work.getId(),
                    work.getUserId(),
                    work.getGmtCreated() == null ? null : work.getGmtCreated().getTime(),
                    approvedForPublish,
                    removeFromFeed));
        }
    }


// 视频审核第二阶段：poller 拿到云端结论后调用，合并第一阶段记录的失败项与视频结论回写作品。
// 作品已不在审核中（管理员抢先人工定论）时丢弃本次云端结论，仅关闭任务。

    @Transactional
    public void completeVideoAudit(Long jobId, AuditResponse videoResult) {
        VideoAuditJob job = videoAuditJobService.getById(jobId);
        if (job == null) {
            return;
        }
        Work work = workMapper.selectById(job.getWorkId());
        if (work == null || !Objects.equals(work.getAuditStatus(), AuditStatus.PROCESS.code())) {
            videoAuditJobService.markCompleted(jobId);
            return;
        }
        List<String> messages = new ArrayList<>();
        if (StringUtils.hasText(job.getPendingFailures())) {
            messages.addAll(Arrays.asList(job.getPendingFailures().split("\n")));
        }
        boolean passed = true;
        if (!isSuccess(videoResult, "视频")) {
            passed = false;
            messages.add("视频有违规行为: " + videoResult.getMsg());
        }
        writeConclusion(work, AuditSummary.ofVideo(passed, PASSED_MESSAGE, messages, true, job.getNewState()));
        videoAuditJobService.markCompleted(jobId);
    }


// 视频审核兑底：重试耗尽或超时，把作品转人工并通知作者。
// 条件更新 PROCESS 防并发：管理员已抢先定论时 update 返回 0，不重复推状态。

    @Transactional
    public void transferVideoAuditToManual(Long workId, String reason) {
        int updated = workMapper.update(null, new UpdateWrapper<Work>()
                .eq("id", workId)
                .eq("audit_status", AuditStatus.PROCESS.code())
                .set("audit_status", AuditStatus.MANUAL.code())
                .set("audit_msg", reason));
        if (updated != 1) {
            return;
        }
        Work work = workMapper.selectById(workId);
        if (work == null) {
            return;
        }
        notificationService.publish(work.getUserId(), null, NotificationType.WORK_AUDIT,
                work.getId(), work.getId(), "视频自动审核未完成，你的作品已转人工审核，请耐心等待");
    }


// 审核队列是否还有余量，供前端显示「快速 / 慢速」。

    public boolean getAuditQueueState() {
        var executor = auditExecutor.getThreadPoolExecutor();
        return executor.getActiveCount() < executor.getCorePoolSize()
                && executor.getQueue().isEmpty();
    }

    AuditSummary inspect(Work work, WorkAuditTask task) {
        if (WorkType.IMAGE.name().equals(work.getWorkType())) {
            return inspectImage(work);
        }
        if (WorkType.TEXT.name().equals(work.getWorkType())) {
            return inspectText(work);
        }
        // 视频由 auditVideo() 单独走两阶段链路，本入口不再处理视频；
        // 未知类型不可直接放行，转人工复核，避免类型漏注册造成未经审核的公开内容。
        log.warn("work audit requires manual review, unsupported workType={}, workId={}", work.getWorkType(), work.getId());
        return new AuditSummary(AuditStatus.MANUAL.code(),
                "未知作品类型，需要人工审核：" + work.getWorkType(), false, false);
    }


// 视频作品第一阶段审核。
// 只有「新增」或「修改为不公开」才需要重跑视频与封面审核——纯元数据修改不该重跑昂贵的视频审核。
// 标题/简介只在与快照不同时才审。视频本体只提交不等待；审核开关关闭时 submitVideo 返回 null，
// 视频部分视为通过（与旧同步实现的「开关关 = 直接放行」语义一致）。

    private VideoInspectResult inspectVideo(Work work, WorkAuditTask task) {
        final boolean isAdd = task == null || !Boolean.FALSE.equals(task.getIsAdd());
        final Boolean oldState = task == null ? null : task.getOldState();
        final Boolean newState = task == null ? null : task.getNewState();
        final String oldTitle = task == null ? null : task.getOldTitle();
        final String oldDescription = task == null ? null : task.getOldDescription();

        final boolean needAuditVideo = isAdd
                ? Objects.equals(oldState, newState)
                : (!Objects.equals(oldState, newState) && !Boolean.TRUE.equals(newState));

        List<String> messages = new ArrayList<>();
        boolean passed = true;

        String aliyunTaskId = null;
        if (needAuditVideo) {
            aliyunTaskId = mediaAuditService.submitVideo(ossFileAccessUrl(work.getPrimaryFileId()));
            AuditResponse coverResult = mediaAuditService.auditImage(ossFileAccessUrl(work.getCoverFileId()));
            if (!isSuccess(coverResult, "封面")) {
                passed = false;
                messages.add("封面有违规行为: " + coverResult.getMsg());
            }
        }

        if (!Objects.equals(work.getTitle(), oldTitle)) {
            AuditResponse titleResult = mediaAuditService.auditText(work.getTitle());
            if (!isSuccess(titleResult, "标题")) {
                passed = false;
                messages.add("标题有违规行为: " + titleResult.getMsg());
            }
        }
        if (!Objects.equals(work.getDescription(), oldDescription)
                && StringUtils.hasText(work.getDescription())) {
            AuditResponse descriptionResult = mediaAuditService.auditText(work.getDescription());
            if (!isSuccess(descriptionResult, "简介")) {
                passed = false;
                messages.add("简介有违规行为: " + descriptionResult.getMsg());
            }
        }

        if (needAuditVideo && aliyunTaskId != null) {
            return VideoInspectResult.deferred(aliyunTaskId, joinAuditItems(messages), newState);
        }
        return VideoInspectResult.terminal(
                AuditSummary.ofVideo(passed, PASSED_MESSAGE, messages, needAuditVideo, newState), newState);
    }

    private AuditSummary inspectImage(Work work) {
        List<String> messages = new ArrayList<>();
        boolean passed = true;

        AuditResponse titleResult = mediaAuditService.auditText(work.getTitle());
        if (!isSuccess(titleResult, "标题")) {
            passed = false;
            messages.add("标题：" + titleResult.getMsg());
        }
        if (StringUtils.hasText(work.getDescription())) {
            AuditResponse descriptionResult = mediaAuditService.auditText(work.getDescription());
            if (!isSuccess(descriptionResult, "简介")) {
                passed = false;
                messages.add("简介：" + descriptionResult.getMsg());
            }
        }

        List<WorkAssetEntity> assets = workAssetMapper.selectList(new LambdaQueryWrapper<WorkAssetEntity>()
                .eq(WorkAssetEntity::getWorkId, work.getId())
                .orderByAsc(WorkAssetEntity::getSortOrder)
                .orderByAsc(WorkAssetEntity::getId));
        for (WorkAssetEntity asset : assets) {
            File file = fileService.getById(asset.getFileId());
            if (file == null) {
                passed = false;
                messages.add("图片 " + (asset.getSortOrder() + 1) + "：文件不存在");
                continue;
            }
            AuditResponse imageResult = mediaAuditService.auditImage(file.getFileKey());
            if (!isSuccess(imageResult, "图片")) {
                passed = false;
                messages.add("图片 " + (asset.getSortOrder() + 1) + "：" + imageResult.getMsg());
            }
        }
        return AuditSummary.of(passed, PASSED_MESSAGE, messages);
    }

    private AuditSummary inspectText(Work work) {
        // 正文只存在于 text_work，work.content 是 @TableField(exist = false)。
        TextWork textWork = textWorkMapper.selectById(work.getId());
        if (textWork == null) {
            // 发布与建任务是同一个事务，走到这里说明数据不一致，重试无意义 —— 推给人工。
            throw new AppException(ErrorCode.INTERNAL_ERROR, "文本作品正文缺失，等待人工审核");
        }
        List<String> messages = new ArrayList<>();
        boolean passed = true;

        AuditResponse titleResult = mediaAuditService.auditText(work.getTitle());
        if (!isSuccess(titleResult, "标题")) {
            passed = false;
            messages.add("标题：" + titleResult.getMsg());
        }
        if (StringUtils.hasText(work.getDescription())) {
            AuditResponse descriptionResult = mediaAuditService.auditText(work.getDescription());
            if (!isSuccess(descriptionResult, "简介")) {
                passed = false;
                messages.add("简介：" + descriptionResult.getMsg());
            }
        }
        AuditResponse contentResult = mediaAuditService.auditText(textWork.getContent());
        if (!isSuccess(contentResult, "正文")) {
            passed = false;
            messages.add("正文：" + contentResult.getMsg());
        }
        return AuditSummary.of(passed, PASSED_MESSAGE, messages);
    }


// 与 CommentAuditService 一致：审核服务没给出结论时抛错重试，而不是静默判负。

    private static boolean isSuccess(AuditResponse response, String label) {
        if (response == null || response.getAuditStatus() == null) {
            throw new AppException(ErrorCode.DEPENDENCY_FAILURE, label + "审核服务未返回有效结果");
        }
        return Objects.equals(response.getAuditStatus(), AuditStatus.SUCCESS.code());
    }

    private String ossFileAccessUrl(Long fileId) {
        if (fileId == null) {
            return null;
        }
        File file = fileService.getById(fileId);
        return file == null ? null : ossFileService.getAccessUrl(file.getFileKey());
    }

    private void sendAuditNotification(Work work) {
        UpdateWrapper<Work> wrapper = new UpdateWrapper<>();
        wrapper.set("audit_notification_sent", true)
                .eq("id", work.getId())
                .and(w -> w.eq("audit_notification_sent", false).or().isNull("audit_notification_sent"));
        if (workMapper.update(null, wrapper) == 0) {
            return;
        }
        String message = Objects.equals(work.getAuditStatus(), AuditStatus.SUCCESS.code())
                ? "你的作品已通过审核"
                : "你的作品未通过审核：" + work.getAuditMsg();
        notificationService.publish(work.getUserId(), null, NotificationType.WORK_AUDIT,
                work.getId(), work.getId(), message);
    }


// 审核结论；message 会写入 work.audit_msg，长度收敛统一在这里完成。

    record AuditSummary(Integer status, String message,
                        boolean approvedForPublish, boolean removeFromFeed) {


// 图片 / 文字作品：没有编辑接口，每次通过都是首次发布。

        static AuditSummary of(boolean passed, String passedMessage, List<String> failures) {
            return new AuditSummary(passed ? AuditStatus.SUCCESS.code() : AuditStatus.PASS.code(),
                    passed ? passedMessage : joinAuditItems(failures),
                    passed, false);
        }


// 视频作品：是否推流取决于本次有没有真的重审视频/封面，避免重复推流。

        static AuditSummary ofVideo(boolean passed, String passedMessage, List<String> failures,
                                    boolean needAuditVideo, Boolean newState) {
            return new AuditSummary(passed ? AuditStatus.SUCCESS.code() : AuditStatus.PASS.code(),
                    passed ? passedMessage : joinAuditItems(failures),
                    passed && needAuditVideo,
                    !needAuditVideo && Boolean.TRUE.equals(newState));
        }
    }

    // 视频第一阶段产物：aliyunTaskId 非空表示等待云端结论（summary 为空，同步失败项存入 pendingFailures）；
    // 否则 summary 即终局。

    record VideoInspectResult(AuditSummary summary, String aliyunTaskId, String pendingFailures, Boolean newState) {

        static VideoInspectResult terminal(AuditSummary summary, Boolean newState) {
            return new VideoInspectResult(summary, null, null, newState);
        }

        static VideoInspectResult deferred(String aliyunTaskId, String pendingFailures, Boolean newState) {
            return new VideoInspectResult(null, aliyunTaskId, pendingFailures, newState);
        }
    }

    private static String joinAuditItems(List<String> items) {
        if (items.isEmpty()) {
            return "";
        }
        if (items.size() <= AUDIT_MSG_MAX_ITEMS) {
            return TextUtils.truncate(String.join("\n", items), AUDIT_MSG_MAX_LENGTH);
        }
        List<String> kept = new ArrayList<>(items.subList(0, AUDIT_MSG_MAX_ITEMS));
        kept.add("另有 " + (items.size() - AUDIT_MSG_MAX_ITEMS) + " 项未通过审核");
        return TextUtils.truncate(String.join("\n", kept), AUDIT_MSG_MAX_LENGTH);
    }
}
