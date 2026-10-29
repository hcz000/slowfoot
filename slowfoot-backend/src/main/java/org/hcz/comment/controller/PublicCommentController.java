package org.hcz.comment.controller;

import org.hcz.common.vo.BasePage;
import org.hcz.comment.service.WorkCommentService;
import org.hcz.common.util.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/slowfoot/index/comment")
public class PublicCommentController {

    @Autowired
    private WorkCommentService commentService;

    @GetMapping("/work/{workId}")
    public ApiResponse list(@PathVariable Long workId, BasePage basePage) {
        return ApiResponse.ok().data(commentService.pageApproved(workId, basePage));
    }
}
