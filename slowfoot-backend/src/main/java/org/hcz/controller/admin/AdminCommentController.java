package org.hcz.controller.admin;

import org.hcz.security.access.Auth;
import org.hcz.entity.vo.BasePage;
import org.hcz.entity.vo.CommentAuditRequest;
import org.hcz.service.work.comment.WorkCommentService;
import org.hcz.util.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/comment")
public class AdminCommentController {

    @Autowired
    private WorkCommentService commentService;

    @GetMapping("/page")
    @Auth("admin:comment:page")
    public ApiResponse page(BasePage basePage, @RequestParam(required = false) Integer auditStatus) {
        return ApiResponse.ok().data(commentService.pageForAudit(basePage, auditStatus));
    }

    @PostMapping("/audit")
    @Auth("admin:comment:audit")
    public ApiResponse audit(@RequestBody @Validated CommentAuditRequest request) {
        commentService.audit(request);
        return ApiResponse.ok().message("评论审核完成");
    }
}
