package org.hcz.audit.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.hcz.common.constant.AuditTaskStatus;
import org.hcz.audit.entity.AuditTaskEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;


// 审核发件箱任务的公共实现：作品 / comment 两处的 claim、markSuccess、markFailure 逐字相同，
// 差异只有两处，都由子类钩子表达：
// 1. {@link #markBusinessFailed} —— 重试耗尽后把业务行从 PROCESS 推到 MANUAL；
// 2. {@link #fallbackErrorMessage} —— last_error 兜底文案。
//
// 三个 @Transactional 注解留在本类上，Spring 会解析继承来的方法注解。
//
// 注意：子类不要覆盖 markSuccess / markFailure。若确实必须覆盖，务必重新声明
// @Transactional(propagation = Propagation.REQUIRES_NEW)，否则失败记录会随外层事务一起回滚。
// 同理这两个方法不能声明为 final —— 这些 bean 没有自己的服务接口，Spring 走 CGLIB 代理，
// 而 CGLIB 无法代理 final 方法，注解会被静默忽略，REQUIRES_NEW 随之失效。

public abstract class AbstractAuditTaskService<M extends BaseMapper<T>, T extends AuditTaskEntity>
        extends ServiceImpl<M, T> {

    private static final int MAX_RETRIES = 5;
    private static final long RETRY_BASE_DELAY_MILLIS = 5_000L;
    private static final long LOCK_MILLIS = 10 * 60 * 1_000L;
    private static final int LAST_ERROR_MAX_LENGTH = 500;


// 重试耗尽：把业务行从 PROCESS 推到 MANUAL。

    protected abstract void markBusinessFailed(T task);


// 消息 为 null 时写进 last_error 的文案。

    protected abstract String fallbackErrorMessage();


// 条件更新抢占任务：PENDING 且到点，或 PROCESSING 但锁已过期。

    public boolean claim(Long taskId, Date now) {
        Date lockUntil = new Date(now.getTime() + LOCK_MILLIS);
        UpdateWrapper<T> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", taskId)
                .and(w -> w.and(x -> x.eq("status", AuditTaskStatus.PENDING.code())
                                .le("next_retry_at", now))
                        .or(x -> x.eq("status", AuditTaskStatus.PROCESSING.code())
                                .lt("locked_until", now)))
                .set("status", AuditTaskStatus.PROCESSING.code())
                .setSql("retry_count = retry_count + 1")
                .set("locked_until", lockUntil);
        return update(null, wrapper);
    }

    // 标记成功：更新状态为 SUCCESS，清空锁。
    @Transactional
    public void markSuccess(Long taskId) {
        if (taskId == null) {
            return;
        }
        UpdateWrapper<T> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", taskId)
                .eq("status", AuditTaskStatus.PROCESSING.code())
                .set("status", AuditTaskStatus.SUCCESS.code())
                .set("locked_until", null)
                .set("last_error", null);
        update(null, wrapper);
    }

    // 标记失败：更新状态为 FAILED 或 PENDING，设置下次重试时间，设置 last_error。
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailure(Long taskId, String message) {
        T task = getById(taskId);
        if (task == null || !Integer.valueOf(AuditTaskStatus.PROCESSING.code()).equals(task.getStatus())) {
            return;
        }

        int retryCount = task.getRetryCount() == null ? 1 : task.getRetryCount();
        UpdateWrapper<T> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", taskId)
                .eq("status", AuditTaskStatus.PROCESSING.code())
                .set("last_error", truncate(message));

        if (retryCount >= MAX_RETRIES) {
            wrapper.set("status", AuditTaskStatus.FAILED.code())
                    .set("locked_until", null);
            markBusinessFailed(task);
        } else {
            long delay = RETRY_BASE_DELAY_MILLIS * (1L << Math.min(retryCount - 1, 6));
            wrapper.set("status", AuditTaskStatus.PENDING.code())
                    .set("next_retry_at", new Date(System.currentTimeMillis() + delay))
                    .set("locked_until", null);
        }
        update(null, wrapper);
    }
    // 截断 last_error 文案：不超过 500 字节，超出部分截断。
    private String truncate(String message) {
        if (message == null) {
            return fallbackErrorMessage();
        }
        return message.length() <= LAST_ERROR_MAX_LENGTH
                ? message : message.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
