package org.hcz.comment.controller;

import org.hcz.comment.dto.CommentCreateRequest;
import org.hcz.comment.entity.WorkComment;
import org.hcz.common.util.UserHolder;
import org.hcz.comment.service.WorkCommentService;
import org.hcz.common.util.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/slowfoot/comment")
public class CommentController {

    @Autowired
    private WorkCommentService commentService;

    @PostMapping
    public ApiResponse create(@RequestBody @Validated CommentCreateRequest request) {
        WorkComment comment = commentService.create(request, UserHolder.get());
        return ApiResponse.ok().data(comment).message("评论已提交，等待审核");
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        commentService.delete(id, UserHolder.get());
        return ApiResponse.ok().message("评论已删除");
    }
}
