package org.hcz.work.controller;

import jakarta.validation.Valid;
import org.hcz.common.vo.BasePage;
import org.hcz.work.dto.ImageWorkPublishRequest;
import org.hcz.work.dto.TextWorkPublishRequest;
import org.hcz.work.dto.VideoWorkPublishRequest;
import org.hcz.common.security.access.Limit;
import org.hcz.file.service.AliyunOssFileService;
import org.hcz.work.service.WorkService;
import org.hcz.common.util.ApiResponse;
import org.hcz.common.util.UserHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;


// 统一作品接口：视频、图片、文字作品的发布、详情与互动。

@RestController
@RequestMapping("/slowfoot/work")
public class WorkController {

    private final WorkService workService;
    private final AliyunOssFileService fileService;

    public WorkController(WorkService workService, AliyunOssFileService fileService) {
        this.workService = workService;
        this.fileService = fileService;
    }


// 获取文件上传 token。

    @GetMapping("/token")
    public ApiResponse getToken(@RequestParam(required = false) String filename) {
        return ApiResponse.ok().data(fileService.createUploadTicket("video/mp4", filename));
    }


// 发布 / 修改视频作品。

    @PostMapping
    @Limit(limit = 5, time = 3600L, msg = "发布作品一小时内不可超过5次")
    public ApiResponse publishVideo(@RequestBody @Validated VideoWorkPublishRequest request) {
        workService.publishVideoWork(request, UserHolder.get());
        return ApiResponse.ok().message("发布成功，请等待审核");
    }


// 发布多图片作品。

    @PostMapping("/image")
    public ApiResponse publishImage(@RequestBody @Valid ImageWorkPublishRequest request) {
        return ApiResponse.ok().data(workService.publishImage(request, UserHolder.get()))
                .message("图片作品发布成功");
    }


// 发布文字作品。

    @PostMapping("/text")
    public ApiResponse publishText(@RequestBody @Valid TextWorkPublishRequest request) {
        return ApiResponse.ok().data(workService.publishText(request, UserHolder.get()))
                .message("文字作品发布成功");
    }


// 我发布的作品，含全部作品类型。

    @GetMapping("/mine")
    public ApiResponse pageMine(BasePage basePage) {
        return ApiResponse.ok().data(workService.pageMine(basePage, UserHolder.get()));
    }


// 作品详情（仅作者本人可见）。

    @GetMapping("/{id}")
    public ApiResponse getMine(@PathVariable Long id) {
        return ApiResponse.ok().data(workService.getMine(id, UserHolder.get()));
    }


// 删除作品。

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        workService.deleteMine(id, UserHolder.get());
        return ApiResponse.ok().message("作品删除成功");
    }


// 幂等地设置点赞状态。

    @PutMapping("/like/{id}")
    public ApiResponse like(@PathVariable Long id) {
        boolean changed = workService.setLike(id, true);
        return ApiResponse.ok().message("已点赞").data(changed);
    }

    @DeleteMapping("/like/{id}")
    public ApiResponse unlike(@PathVariable Long id) {
        boolean changed = workService.setLike(id, false);
        return ApiResponse.ok().message("取消点赞").data(changed);
    }


// 添加浏览记录。

    @PostMapping("/history/{id}")
    public ApiResponse addHistory(@PathVariable Long id) throws Exception {
        workService.recordView(id, UserHolder.get());
        return ApiResponse.ok();
    }


// 当前用户的浏览记录。

    @GetMapping("/history")
    public ApiResponse getHistory(BasePage basePage) {
        return ApiResponse.ok().data(workService.getHistory(basePage));
    }


// 获取指定收藏夹下的作品。

    @GetMapping("/favorites/{favoritesId}")
    public ApiResponse listByFavorites(@PathVariable Long favoritesId) {
        return ApiResponse.ok().data(workService.listByFavorites(favoritesId));
    }


// 幂等地设置作品在指定收藏夹中的状态。

    @PutMapping("/favorites/{fId}/{workId}")
    public ApiResponse favorite(@PathVariable Long fId, @PathVariable Long workId) {
        boolean changed = workService.setFavorite(fId, workId, true);
        return ApiResponse.ok().message("已收藏").data(changed);
    }

    @DeleteMapping("/favorites/{fId}/{workId}")
    public ApiResponse unfavorite(@PathVariable Long fId, @PathVariable Long workId) {
        boolean changed = workService.setFavorite(fId, workId, false);
        return ApiResponse.ok().message("取消收藏").data(changed);
    }


// 当前审核队列状态，前端据此显示「快速 / 慢速」。

    @GetMapping("/audit/queue/state")
    public ApiResponse getAuditQueueState() {
        return ApiResponse.ok().message(workService.getAuditQueueState());
    }


// 关注流（滚动分页，拉模式）。

    @GetMapping("/follow/feed")
    public ApiResponse followFeed(@RequestParam(required = false) Long lastTime) throws ParseException {
        return ApiResponse.ok().data(workService.followFeed(UserHolder.get(), lastTime));
    }


// 初始化关注流收件箱。

    @PostMapping("/init/follow/feed")
    public ApiResponse initFollowFeed() {
        workService.initFollowFeed(UserHolder.get());
        return ApiResponse.ok();
    }
}
