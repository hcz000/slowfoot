package org.hcz.service.work.comment;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.hcz.entity.work.WorkComment;
import org.hcz.entity.vo.BasePage;
import org.hcz.entity.vo.CommentAuditRequest;
import org.hcz.entity.vo.CommentCreateRequest;
import org.hcz.entity.vo.WorkCommentVO;

import java.util.List;

public interface WorkCommentService {

    WorkComment create(CommentCreateRequest request, Long userId);

    IPage<WorkCommentVO> pageApproved(Long workId, BasePage basePage);

    void delete(Long id, Long userId);

    IPage<WorkComment> pageForAudit(BasePage basePage, Integer auditStatus);

    void audit(CommentAuditRequest request);

    List<WorkComment> listByRootIds(List<Long> rootIds);
}
