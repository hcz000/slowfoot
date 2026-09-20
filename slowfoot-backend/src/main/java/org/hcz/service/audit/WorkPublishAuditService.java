package org.hcz.service.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.hcz.constant.AuditStatus;
import org.hcz.constant.NotificationType;
import org.hcz.entity.File;
import org.hcz.entity.audit.WorkAuditTask;
import org.hcz.entity.response.AuditResponse;
import org.hcz.entity.work.TextWork;
import org.hcz.entity.work.Work;
import org.hcz.entity.work.WorkAssetEntity;
import org.hcz.entity.work.WorkType;
import org.hcz.messaging.workaudit.WorkAuditCompletedEvent;
import org.hcz.messaging.workaudit.WorkAuditSubmittedEvent;
import org.hcz.exception.AppException;
import org.hcz.exception.ErrorCode;
import org.hcz.mapper.audit.EventConsumeMapper;
import org.hcz.mapper.work.TextWorkMapper;
import org.hcz.mapper.work.WorkAssetMapper;
import org.hcz.mapper.work.WorkMapper;
import org.hcz.service.file.AliyunOssFileService;
import org.hcz.service.file.FileService;
import org.hcz.service.notification.NotificationService;
import org.hcz.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


// 作品发布审核：消费 work_audit_task 发件箱，回写审核结论并通知作者。作品 / 图片 / 文字共用一条链路。

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
    private final TextAuditService textAuditService;
    private final ImageAuditService imageAuditService;
    private final VideoFileAuditService videoFileAuditService;
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
                                   TextAuditService textAuditService,
                                   ImageAuditService imageAuditService,
                                   VideoFileAuditService videoFileAuditService,
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
        this.textAuditService = textAuditService;
        this.imageAuditService = imageAuditService;
        this.videoFileAuditService = videoFileAuditService;
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
        AuditSummary summary = inspect(work, task);

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
        workAuditTaskService.markSuccess(event.getTaskId());
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
        if (WorkType.VIDEO.name().equals(work.getWorkType())) {
            return inspectVideo(work, task);
        }
        // 未知类型说明有 bug，留日志并放行，避免把任务卡在重试里。
        log.warn("work audit skipped, unsupported workType={}, workId={}", work.getWorkType(), work.getId());
        return AuditSummary.of(true, PASSED_MESSAGE, List.of());
    }


// 视频作品审核。
// 只有「新增」或「修改为不公开」才需要重跑视频与封面审核——纯元数据修改不该重跑昂贵的视频审核。
// 标题/简介只在与快照不同时才审。

    private AuditSummary inspectVideo(Work work, WorkAuditTask task) {
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

        if (needAuditVideo) {
            AuditResponse videoResult = videoFileAuditService.audit(ossFileAccessUrl(work.getPrimaryFileId()));
            if (!isSuccess(videoResult, "视频")) {
                passed = false;
                messages.add("视频有违规行为: " + videoResult.getMsg());
            }
            AuditResponse coverResult = imageAuditService.audit(ossFileAccessUrl(work.getCoverFileId()));
            if (!isSuccess(coverResult, "封面")) {
                passed = false;
                messages.add("封面有违规行为: " + coverResult.getMsg());
            }
        }

        if (!Objects.equals(work.getTitle(), oldTitle)) {
            AuditResponse titleResult = textAuditService.audit(work.getTitle());
            if (!isSuccess(titleResult, "标题")) {
                passed = false;
                messages.add("标题有违规行为: " + titleResult.getMsg());
            }
        }
        if (!Objects.equals(work.getDescription(), oldDescription)
                && StringUtils.hasText(work.getDescription())) {
            AuditResponse descriptionResult = textAuditService.audit(work.getDescription());
            if (!isSuccess(descriptionResult, "简介")) {
                passed = false;
                messages.add("简介有违规行为: " + descriptionResult.getMsg());
            }
        }

        return AuditSummary.ofVideo(passed, PASSED_MESSAGE, messages, needAuditVideo, newState);
    }

    private AuditSummary inspectImage(Work work) {
        List<String> messages = new ArrayList<>();
        boolean passed = true;

        AuditResponse titleResult = textAuditService.audit(work.getTitle());
        if (!isSuccess(titleResult, "标题")) {
            passed = false;
            messages.add("标题：" + titleResult.getMsg());
        }
        if (StringUtils.hasText(work.getDescription())) {
            AuditResponse descriptionResult = textAuditService.audit(work.getDescription());
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
            AuditResponse imageResult = imageAuditService.audit(file.getFileKey());
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

        AuditResponse titleResult = textAuditService.audit(work.getTitle());
        if (!isSuccess(titleResult, "标题")) {
            passed = false;
            messages.add("标题：" + titleResult.getMsg());
        }
        if (StringUtils.hasText(work.getDescription())) {
            AuditResponse descriptionResult = textAuditService.audit(work.getDescription());
            if (!isSuccess(descriptionResult, "简介")) {
                passed = false;
                messages.add("简介：" + descriptionResult.getMsg());
            }
        }
        AuditResponse contentResult = textAuditService.audit(textWork.getContent());
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

    private static String joinAuditItems(List<String> items) {
        if (items.size() <= AUDIT_MSG_MAX_ITEMS) {
            return TextUtils.truncate(String.join("\n", items), AUDIT_MSG_MAX_LENGTH);
        }
        List<String> kept = new ArrayList<>(items.subList(0, AUDIT_MSG_MAX_ITEMS));
        kept.add("另有 " + (items.size() - AUDIT_MSG_MAX_ITEMS) + " 项未通过审核");
        return TextUtils.truncate(String.join("\n", kept), AUDIT_MSG_MAX_LENGTH);
    }
}
