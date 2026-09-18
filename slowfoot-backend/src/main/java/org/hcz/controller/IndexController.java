package org.hcz.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hcz.entity.work.WorkCategory;
import org.hcz.entity.work.Work;
import org.hcz.entity.work.WorkShare;
import org.hcz.entity.vo.BasePage;
import org.hcz.entity.vo.ShareResult;
import org.hcz.config.ShareProperties;
import org.hcz.service.user.UserService;
import org.hcz.service.work.category.WorkCategoryService;
import org.hcz.service.work.WorkService;
import org.hcz.util.JwtUtils;
import org.hcz.util.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;



@RestController
@RequestMapping("/luckyjourney/index")
public class IndexController {

    @Autowired
    private UserService userService;

    @Autowired
    private WorkService workService;

    @Autowired
    private WorkCategoryService workCategoryService;

    @Autowired
    private ShareProperties shareProperties;


// 兴趣推送
// @return

    @GetMapping("/pushWorks")
    public ApiResponse pushWorks(HttpServletRequest request){
        final Long userId = JwtUtils.getUserId(request);
        return ApiResponse.ok().data(workService.pushWorks(userId));
    }


// 搜索作品
// @return

    @GetMapping("/search")
    public ApiResponse searchWorks(@RequestParam(required = false) String searchName, BasePage basePage,HttpServletRequest request){

        return ApiResponse.ok().data(workService.searchWorks(searchName,basePage,JwtUtils.getUserId(request)));
    }


// 根据分类获取作品
// @param typeId
// @return

    @GetMapping("/work/type/{typeId}")
    public ApiResponse getVideoByTypeId(@PathVariable Long typeId){

        return ApiResponse.ok().data(workService.listByCategory(typeId));
    }


// 鑾峰彇鎵€鏈夊垎绫?
// @return

    @GetMapping("/types")
    public ApiResponse getTypes(HttpServletRequest request){
        final List<WorkCategory> types = workCategoryService.list(new LambdaQueryWrapper<WorkCategory>().select(WorkCategory::getIcon, WorkCategory::getId, WorkCategory::getName).orderByDesc(WorkCategory::getSort));

        final Set<Long> set = userService.listSubscribeType(JwtUtils.getUserId(request)).stream().map(WorkCategory::getId).collect(Collectors.toSet());

        for (WorkCategory type : types) {
            if (set.contains(type.getId())) {
                type.setUsed(true);
            }else {
                type.setUsed(false);
            }
        }
        return ApiResponse.ok().data(types);
    }


// 分享作品
// @param workId
// @param request
// @return

    @PostMapping("/share/{workId}")
    public ApiResponse share(@PathVariable Long workId, HttpServletRequest request){

        String ip = null;
        if (request.getHeader("x-forwarded-for") == null)
            ip = request.getRemoteAddr();
        else
            ip = request.getHeader("x-forwarded-for");
        final WorkShare workShare = new WorkShare();

        workShare.setWorkId(workId);
        workShare.setIp(ip);
        if (JwtUtils.checkAccessToken(request)) {
            workShare.setUserId(JwtUtils.getUserId(request));
        }
        boolean firstShare = workService.shareWork(workShare);
        ShareResult result = new ShareResult(workId, shareProperties.buildShareUrl(workId), firstShare);
        return ApiResponse.ok().data(result);
    }


// 根据id获取作品详情
// @param id
// @return

    @GetMapping("/work/{id}")
    public ApiResponse getVideoById(@PathVariable Long id,HttpServletRequest request){
        final Long userId = JwtUtils.getUserId(request);
        return ApiResponse.ok().data(workService.getPublicWork(id,userId));
    }


// 获取热度排行榜
// @return

    @GetMapping("/work/hot/rank")
    public ApiResponse listHotRank(){
        return ApiResponse.ok().data(workService.hotRank());
    }


// 根据作品标签推送相似作品
// @param video
// @return

    @GetMapping("/work/similar")
    public ApiResponse pushSimilar(Work work){
        return ApiResponse.ok().data(workService.listSimilar(work.getId()));
    }


// 推送热门作品
// @return

    @GetMapping("/work/hot")
    public ApiResponse listHotWork(){
        return ApiResponse.ok().data(workService.listHotWork());
    }


// 根据用户id获取公开作品
// @param userId
// @param basePage
// @return

    @GetMapping("/work/user")
    public ApiResponse listPublicByUserId(@RequestParam(required = false) Long userId,
                               BasePage basePage,HttpServletRequest request){

        userId = userId == null ? JwtUtils.getUserId(request) : userId;
        return ApiResponse.ok().data(workService.listPublicByUserId(userId,basePage));
    }


// 获取用户搜索记录
// @return

    @GetMapping("/search/history")
    public ApiResponse searchHistory(HttpServletRequest request){
        return ApiResponse.ok().data(userService.searchHistory(JwtUtils.getUserId(request)));
    }

    // 删除搜索记录
    @DeleteMapping("/search/history")
    public ApiResponse deleteSearchHistory(HttpServletRequest request){
        userService.deleteSearchHistory(JwtUtils.getUserId(request));
        return ApiResponse.ok();
    }
}
