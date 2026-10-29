package org.hcz.audit.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.hcz.audit.entity.VideoAuditJob;
import org.hcz.audit.mapper.VideoAuditJobMapper;
import org.hcz.common.constant.AuditTaskStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;


// 视频审核任务的轻量状态机。刻意不继承 AbstractAuditTaskService：
// 那里的 claim 每轮消耗 retry_count，而"结论还没出"是正常轮询而非失败，
// 正常轮询不能计入重试预算。这里 retry_count 只在提交 / 查询真正失败时递增，
// 耗尽后由调用方（poller）负责把作品转人工。

@Service
public class VideoAuditJobService extends ServiceImpl<VideoAuditJobMapper, VideoAuditJob> {

    private static final int MAX_POLL_FAILURES = 5;
    private static final long RETRY_BASE_DELAY_MILLIS = 5_000L;
    private static final long LOCK_MILLIS = 60_000L;
    private static final int LAST_ERROR_MAX_LENGTH = 500;

    public VideoAuditJobService(VideoAuditJobMapper mapper) {
        this.baseMapper = mapper;
    }


// 抢占一轮处理权：到点的 PENDING，或锁已过期的 PROCESSING（上一任处理者失联）。
// 不加 retry_count —— 一轮轮询不是失败重试。

    public boolean claimForPoll(Long jobId, Date now) {
        Date lockUntil = new Date(now.getTime() + LOCK_MILLIS);
        UpdateWrapper<VideoAuditJob> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", jobId)
                .and(w -> w.and(x -> x.eq("status", AuditTaskStatus.PENDING.code())
                                .le("next_poll_at", now))
                        .or(x -> x.eq("status", AuditTaskStatus.PROCESSING.code())
                                .lt("locked_until", now)))
                .set("status", AuditTaskStatus.PROCESSING.code())
                .set("locked_until", lockUntil);
        return update(null, wrapper);
    }


// 本轮结论未出：回到 PENDING 并安排下一次查询。

    @Transactional
    public void reschedule(Long jobId, Date nextPollAt) {
        UpdateWrapper<VideoAuditJob> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", jobId)
                .eq("status", AuditTaskStatus.PROCESSING.code())
                .set("status", AuditTaskStatus.PENDING.code())
                .set("next_poll_at", nextPollAt)
                .set("locked_until", null);
        update(null, wrapper);
    }


// 重提交成功后回填新的阿里云 taskId。

    @Transactional
    public void bindAliyunTask(Long jobId, String aliyunTaskId) {
        UpdateWrapper<VideoAuditJob> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", jobId)
                .eq("status", AuditTaskStatus.PROCESSING.code())
                .set("aliyun_task_id", aliyunTaskId)
                .set("last_error", null);
        update(null, wrapper);
    }

    @Transactional
    public void markCompleted(Long jobId) {
        UpdateWrapper<VideoAuditJob> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", jobId)
                .set("status", AuditTaskStatus.SUCCESS.code())
                .set("locked_until", null)
                .set("last_error", null);
        update(null, wrapper);
    }


// 提交或查询真正失败：retry_count+1，指数退避；耗尽后置 FAILED 并返回 true，
// 由调用方把作品从 PROCESS 推到 MANUAL。

    @Transactional
    public boolean markFailure(Long jobId, String message) {
        VideoAuditJob job = getById(jobId);
        if (job == null || !Integer.valueOf(AuditTaskStatus.PROCESSING.code()).equals(job.getStatus())) {
            return false;
        }
        int retryCount = (job.getRetryCount() == null ? 0 : job.getRetryCount()) + 1;
        UpdateWrapper<VideoAuditJob> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", jobId)
                .eq("status", AuditTaskStatus.PROCESSING.code())
                .set("retry_count", retryCount)
                .set("last_error", truncate(message))
                .set("locked_until", null);
        if (retryCount >= MAX_POLL_FAILURES) {
            wrapper.set("status", AuditTaskStatus.FAILED.code());
        } else {
            long delay = RETRY_BASE_DELAY_MILLIS * (1L << Math.min(retryCount - 1, 6));
            wrapper.set("status", AuditTaskStatus.PENDING.code())
                    .set("next_poll_at", new Date(System.currentTimeMillis() + delay));
        }
        boolean exhausted = retryCount >= MAX_POLL_FAILURES;
        update(null, wrapper);
        return exhausted;
    }


// 结论超时未出（云端仍 PENDING 但已过截止时间）：直接终态 FAILED，调用方转人工。

    @Transactional
    public void expireToManual(Long jobId, String message) {
        UpdateWrapper<VideoAuditJob> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", jobId)
                .eq("status", AuditTaskStatus.PROCESSING.code())
                .set("status", AuditTaskStatus.FAILED.code())
                .set("last_error", truncate(message))
                .set("locked_until", null);
        update(null, wrapper);
    }

    private String truncate(String message) {
        if (message == null) {
            return "视频审核任务执行失败";
        }
        return message.length() <= LAST_ERROR_MAX_LENGTH
                ? message : message.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
