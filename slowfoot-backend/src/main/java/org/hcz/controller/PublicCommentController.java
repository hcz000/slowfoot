package org.hcz.controller;

import org.hcz.entity.vo.BasePage;
import org.hcz.service.work.comment.WorkCommentService;
import org.hcz.util.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/luckyjourney/index/comment")
public class PublicCommentController {

    @Autowired
    private WorkCommentService commentService;

    @GetMapping("/work/{workId}")
    public ApiResponse list(@PathVariable Long workId, BasePage basePage) {
        return ApiResponse.ok().data(commentService.pageApproved(workId, basePage));
    }
}
