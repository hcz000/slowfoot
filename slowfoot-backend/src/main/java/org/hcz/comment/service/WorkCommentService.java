package org.hcz.comment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.hcz.comment.entity.WorkComment;
import org.hcz.common.vo.BasePage;
import org.hcz.comment.dto.CommentAuditRequest;
import org.hcz.comment.dto.CommentCreateRequest;
import org.hcz.comment.dto.WorkCommentVO;

import java.util.List;

public interface WorkCommentService {

    WorkComment create(CommentCreateRequest request, Long userId);

    IPage<WorkCommentVO> pageApproved(Long workId, BasePage basePage);

    void delete(Long id, Long userId);

    IPage<WorkComment> pageForAudit(BasePage basePage, Integer auditStatus);

    void audit(CommentAuditRequest request);

    List<WorkComment> listByRootIds(List<Long> rootIds);
}
