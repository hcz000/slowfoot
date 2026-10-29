package org.hcz.audit.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.hcz.common.constant.AuditStatus;
import org.hcz.audit.entity.CommentAuditTask;
import org.hcz.comment.entity.WorkComment;
import org.hcz.audit.mapper.CommentAuditTaskMapper;
import org.hcz.comment.mapper.WorkCommentMapper;
import org.springframework.stereotype.Service;


// Persists 并 retries comment audit tasks.

@Service
public class CommentAuditTaskService
        extends AbstractAuditTaskService<CommentAuditTaskMapper, CommentAuditTask> {

    private static final String RETRY_EXHAUSTED_MESSAGE = "自动审核多次失败，请等待人工审核";

    private final WorkCommentMapper commentMapper;

    public CommentAuditTaskService(WorkCommentMapper commentMapper) {
        this.commentMapper = commentMapper;
    }

    @Override
    protected void markBusinessFailed(CommentAuditTask task) {
        commentMapper.update(null, new UpdateWrapper<WorkComment>()
                .eq("id", task.getCommentId())
                .eq("audit_status", AuditStatus.PROCESS.code())
                .set("audit_status", AuditStatus.MANUAL.code())
                .set("audit_msg", RETRY_EXHAUSTED_MESSAGE));
    }

    @Override
    protected String fallbackErrorMessage() {
        return "评论审核任务执行失败";
    }
}
