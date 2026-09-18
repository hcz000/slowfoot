package org.hcz.service.audit;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.hcz.constant.AuditStatus;
import org.hcz.entity.audit.CommentAuditTask;
import org.hcz.entity.work.WorkComment;
import org.hcz.mapper.audit.CommentAuditTaskMapper;
import org.hcz.mapper.work.WorkCommentMapper;
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
