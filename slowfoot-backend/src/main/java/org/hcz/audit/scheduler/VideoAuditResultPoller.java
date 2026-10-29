package org.hcz.audit.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hcz.audit.entity.VideoAuditJob;
import org.hcz.audit.service.MediaAuditService;
import org.hcz.audit.service.VideoAuditJobService;
import org.hcz.audit.service.WorkPublishAuditService;
import org.hcz.common.constant.AuditStatus;
import org.hcz.common.constant.AuditTaskStatus;
import org.hcz.audit.vo.AuditResponse;
import org.hcz.file.entity.File;
import org.hcz.file.service.AliyunOssFileService;
import org.hcz.file.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;


// 视频审核结果轮询器：驱动 video_audit_job 状态机。
// 每轮对单个任务只做一次轻量 HTTP 查询（不 sleep），慢的代价从「占消费线程 2 分钟」
// 变成「多等几个轮询周期」。租约抢占保证多实例不重复处理，锁过期任务可被重新领取。

@Component
public class VideoAuditResultPoller {

    private static final Logger log = LoggerFactory.getLogger(VideoAuditResultPoller.class);
    private static final int BATCH_SIZE = 20;
    private static final String RETRY_EXHAUSTED_MESSAGE = "自动审核多次失败，请等待人工审核";
    private static final String TIMEOUT_MESSAGE = "视频审核超时，已转人工审核";

    private final VideoAuditJobService jobService;
    private final MediaAuditService mediaAuditService;
    private final WorkPublishAuditService workPublishAuditService;
    private final FileService fileService;
    private final AliyunOssFileService ossFileService;
    private final long pollDelayMillis;
    private final long timeoutMillis;

    public VideoAuditResultPoller(VideoAuditJobService jobService,
                                  MediaAuditService mediaAuditService,
                                  WorkPublishAuditService workPublishAuditService,
                                  FileService fileService,
                                  AliyunOssFileService ossFileService,
                                  @Value("${audit.video-poll-delay-ms:5000}") long pollDelayMillis,
                                  @Value("${audit.video-timeout-minutes:10}") long timeoutMinutes) {
        this.jobService = jobService;
        this.mediaAuditService = mediaAuditService;
        this.workPublishAuditService = workPublishAuditService;
        this.fileService = fileService;
        this.ossFileService = ossFileService;
        this.pollDelayMillis = pollDelayMillis;
        this.timeoutMillis = timeoutMinutes * 60_000L;
    }

    @Scheduled(fixedDelayString = "${audit.video-poll-delay-ms:5000}")
    public void poll() {
        Date now = new Date();
        LambdaQueryWrapper<VideoAuditJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.and(x -> x.eq(VideoAuditJob::getStatus, AuditTaskStatus.PENDING.code())
                                .le(VideoAuditJob::getNextPollAt, now))
                        .or(x -> x.eq(VideoAuditJob::getStatus, AuditTaskStatus.PROCESSING.code())
                                .lt(VideoAuditJob::getLockedUntil, now)))
                .orderByAsc(VideoAuditJob::getId)
                .last("LIMIT " + BATCH_SIZE);

        List<VideoAuditJob> jobs = jobService.list(wrapper);
        for (VideoAuditJob job : jobs) {
            if (!jobService.claimForPoll(job.getId(), now)) {
                continue;
            }
            try {
                handle(job, now);
            } catch (Exception e) {
                log.error("Video audit poll failed, jobId={}, workId={}", job.getId(), job.getWorkId(), e);
                handleFailure(job.getId(), e.getMessage());
            }
        }
    }

    private void handle(VideoAuditJob job, Date now) {
        String taskId = job.getAliyunTaskId();
        if (taskId == null || taskId.isBlank()) {
            // 上一任处理者在提交成功前失联：重建访问 URL 重新提交。
            taskId = mediaAuditService.submitVideo(ossAccessUrl(job.getFileId()));
            if (taskId == null) {
                // 审核开关中途被关闭：视频部分直接按通过收尾。
                workPublishAuditService.completeVideoAudit(job.getId(),
                        new AuditResponse(AuditStatus.SUCCESS.code(), "正常"));
                return;
            }
            jobService.bindAliyunTask(job.getId(), taskId);
        }

        MediaAuditService.VideoOutcome outcome = mediaAuditService.fetchVideo(taskId);
        switch (outcome.state()) {
            case FINISHED -> workPublishAuditService.completeVideoAudit(job.getId(), outcome.response());
            case FAILED -> handleFailure(job.getId(), "阿里云视频审核任务失败");
            case PENDING -> {
                Date deadline = job.getGmtCreated() == null
                        ? new Date(now.getTime() + timeoutMillis)
                        : new Date(job.getGmtCreated().getTime() + timeoutMillis);
                if (now.after(deadline)) {
                    jobService.expireToManual(job.getId(), TIMEOUT_MESSAGE);
                    workPublishAuditService.transferVideoAuditToManual(job.getWorkId(), TIMEOUT_MESSAGE);
                } else {
                    jobService.reschedule(job.getId(), new Date(now.getTime() + pollDelayMillis));
                }
            }
        }
    }

    // 提交 / 查询真正失败才消耗重试预算；耗尽后作品转人工。
    private void handleFailure(Long jobId, String message) {
        if (jobService.markFailure(jobId, message)) {
            VideoAuditJob job = jobService.getById(jobId);
            if (job != null) {
                workPublishAuditService.transferVideoAuditToManual(job.getWorkId(), RETRY_EXHAUSTED_MESSAGE);
            }
        }
    }

    private String ossAccessUrl(Long fileId) {
        File file = fileService.getById(fileId);
        return file == null ? null : ossFileService.getAccessUrl(file.getFileKey());
    }
}
