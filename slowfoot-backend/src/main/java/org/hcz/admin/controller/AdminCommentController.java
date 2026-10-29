package org.hcz.admin.controller;

import org.hcz.admin.security.Auth;
import org.hcz.common.vo.BasePage;
import org.hcz.comment.dto.CommentAuditRequest;
import org.hcz.comment.service.WorkCommentService;
import org.hcz.common.util.ApiResponse;
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
