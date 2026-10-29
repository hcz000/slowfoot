package org.hcz.audit.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.hcz.common.constant.AuditStatus;
import org.hcz.common.constant.NotificationType;
import org.hcz.audit.vo.AuditResponse;
import org.hcz.work.entity.Work;
import org.hcz.comment.entity.WorkComment;
import org.hcz.comment.event.CommentAuditSubmittedEvent;
import org.hcz.common.exception.AppException;
import org.hcz.common.exception.ErrorCode;
import org.hcz.common.messaging.mapper.EventConsumeMapper;
import org.hcz.comment.mapper.WorkCommentMapper;
import org.hcz.work.mapper.WorkMapper;
import org.hcz.notification.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class CommentAuditService {

    @Autowired
    private WorkCommentMapper commentMapper;

    @Autowired
    private WorkMapper workMapper;

    @Autowired
    private EventConsumeMapper eventConsumeMapper;

    @Autowired
    private MediaAuditService mediaAuditService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private CommentAuditTaskService taskService;

    // 执行持久化审核任务
    @Transactional
    public void audit(CommentAuditSubmittedEvent event) {
        if (eventConsumeMapper.insertIgnore(event.getEventId(), "comment-audit") == 0) {
            return;
        }

        WorkComment comment = commentMapper.selectById(event.getCommentId());
        if (comment == null || !Objects.equals(comment.getAuditStatus(), AuditStatus.PROCESS.code())) {
            taskService.markSuccess(event.getTaskId());
            return;
        }

        AuditResponse response = mediaAuditService.auditText(comment.getContent());
        if (response == null || response.getAuditStatus() == null) {
            throw new AppException(ErrorCode.DEPENDENCY_FAILURE, "评论审核服务未返回有效结果");
        }
        Integer status = Objects.equals(response.getAuditStatus(), AuditStatus.SUCCESS.code())
                ? AuditStatus.SUCCESS.code() : AuditStatus.PASS.code();
        String message = response.getMsg();
        if (message == null || message.isBlank()) {
            message = status.equals(AuditStatus.SUCCESS.code()) ? "通过" : "自动审核未通过";
        }
        WorkComment updated = new WorkComment();
        updated.setId(comment.getId());
        updated.setAuditStatus(status);
        updated.setAuditMsg(message);
        UpdateWrapper<WorkComment> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", comment.getId()).eq("audit_status", AuditStatus.PROCESS.code());
        if (commentMapper.update(updated, wrapper) > 0) {
            comment.setAuditStatus(status);
            comment.setAuditMsg(message);
            publishAuditResult(comment, AuditStatus.PROCESS.code());
        }
        taskService.markSuccess(event.getTaskId());
    }

    // 发布审核结果
    public void publishAuditResult(WorkComment comment, Integer oldStatus) {
        if (comment == null || Objects.equals(oldStatus, comment.getAuditStatus())) {
            return;
        }
        if (Objects.equals(comment.getAuditStatus(), AuditStatus.SUCCESS.code())) {
            sendInteractionNotification(comment);
            sendAuditNotification(comment);
        } else if (Objects.equals(comment.getAuditStatus(), AuditStatus.PASS.code())) {
            sendAuditNotification(comment);
        }
    }

    // 发送互动通知
    private void sendInteractionNotification(WorkComment comment) {
        UpdateWrapper<WorkComment> wrapper = new UpdateWrapper<>();
        wrapper.set("notification_sent", true)
                .eq("id", comment.getId())
                .and(w -> w.eq("notification_sent", false).or().isNull("notification_sent"));
        if (commentMapper.update(null, wrapper) == 0) {
            return;
        }

        Work work = workMapper.selectById(comment.getWorkId());
        if (work == null) {
            return;
        }
        boolean reply = comment.getParentId() != null && comment.getParentId() != 0L;
        Long recipient = reply ? comment.getReplyToUserId() : work.getUserId();
        notificationService.publish(recipient, comment.getUserId(),
                reply ? NotificationType.REPLY : NotificationType.COMMENT,
                comment.getId(), comment.getWorkId(),
                reply ? "有人回复了你的评论" : "有人评论了你的视频");
    }

    // 发送审核通知
    private void sendAuditNotification(WorkComment comment) {
        UpdateWrapper<WorkComment> wrapper = new UpdateWrapper<>();
        wrapper.set("audit_notification_sent", true)
                .eq("id", comment.getId())
                .and(w -> w.eq("audit_notification_sent", false).or().isNull("audit_notification_sent"));
        if (commentMapper.update(null, wrapper) == 0) {
            return;
        }
        String message = Objects.equals(comment.getAuditStatus(), AuditStatus.SUCCESS.code())
                ? "你的评论已通过审核" : "你的评论未通过审核：" + comment.getAuditMsg();
        notificationService.publish(comment.getUserId(), null, NotificationType.COMMENT_AUDIT,
                comment.getId(), comment.getWorkId(), message);
    }
}
