package org.hcz.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hcz.entity.vo.BasePage;
import org.hcz.entity.vo.Model;
import org.hcz.entity.vo.UpdateUserVO;
import org.hcz.entity.vo.UserModel;
import org.hcz.entity.work.Work;
import org.hcz.constant.AuditStatus;
import org.hcz.mapper.work.WorkMapper;
import org.hcz.service.file.AliyunOssFileService;
import org.hcz.entity.user.Favorites;
import org.hcz.entity.vo.*;
import org.hcz.exception.AppException;
import org.hcz.exception.ErrorCode;
import org.hcz.util.UserHolder;
import org.hcz.service.user.FavoritesService;
import org.hcz.service.user.UserService;
import org.hcz.util.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;



@RestController
@RequestMapping("/luckyjourney/customer")
public class CustomerController {


    @Autowired
    AliyunOssFileService ossFileService;

    @Autowired
    private UserService userService;

    @Autowired
    private WorkMapper workMapper;

    @Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private FavoritesService favoritesService;


// 获取个人信息
// @param userId
// @return
// @throws Exception

    @GetMapping("/getInfo/{userId}")
    public ApiResponse getInfo(@PathVariable Long userId){
        return ApiResponse.ok().data(userService.getInfo(userId));
    }


// 获取用户信息
// @return

    @GetMapping("/getInfo")
    public ApiResponse getDefaultInfo(){
        return ApiResponse.ok().data(userService.getInfo(UserHolder.get()));
    }


// 获取关注人员
// @param basePage
// @param userId
// @return

    @GetMapping("/follows")
    public ApiResponse getFollows(BasePage basePage, Long userId){
        return ApiResponse.ok().data(userService.getFollows(userId,basePage));
    }


// 获取粉丝
// @param basePage
// @param userId
// @return

    @GetMapping("/fans")
    public ApiResponse getFans(BasePage basePage,Long userId){
        return ApiResponse.ok().data(userService.getFans(userId,basePage));
    }


    @GetMapping("/favorites")
    public ApiResponse listFavorites(){
        final Long userId = UserHolder.get();
        List<Favorites> favorites = favoritesService.listByUserId(userId);
        return ApiResponse.ok().data(favorites);
    }



    @GetMapping("/favorites/{id}")
    public ApiResponse getFavorites(@PathVariable Long id){
        return ApiResponse.ok().data(favoritesService.getById(id));
    }


// 添加/修改收藏夹
// @param favorites
// @return

    @PostMapping("/favorites")
    public ApiResponse saveOrUpdateFavorites(@RequestBody @Validated Favorites favorites){
        final Long userId = UserHolder.get();
        final Long id = favorites.getId();
        favorites.setUserId(userId);
        final long count = favoritesService.count(new LambdaQueryWrapper<Favorites>()
                .eq(Favorites::getName, favorites.getName()).eq(Favorites::getUserId, userId).ne(Favorites::getId,favorites.getId()));
        if (count == 1){
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "已存在同名的收藏夹");
        }
        favoritesService.saveOrUpdate(favorites);
        return ApiResponse.ok().message(id !=null ? "修改成功" : "添加成功");
    }


// 鍒犻櫎鏀惰棌澶?
// @param id
// @return

    @DeleteMapping("/favorites/{id}")
    public ApiResponse deleteFavorites(@PathVariable Long id){
        favoritesService.remove(id,UserHolder.get());
        return ApiResponse.ok().message("删除成功");
    }


// 订阅分类

    @PostMapping("/subscribe")
    public ApiResponse subscribe(@RequestParam(required = false) String types){
        final HashSet<Long> typeSet = new HashSet<>();
        String msg = "取消订阅";
        if (!ObjectUtils.isEmpty(types)){
            for (String s : types.split(",")) {
                typeSet.add(Long.parseLong(s));
            }
            msg = "订阅成功";
        }
        userService.subscribe(typeSet);
        return ApiResponse.ok().message(msg);
    }


// 鑾峰彇鐢ㄦ埛璁㈤槄鐨勫垎绫?
// @return

    @GetMapping("/subscribe")
    public ApiResponse listSubscribeType(){
        return ApiResponse.ok().data(userService.listSubscribeType(UserHolder.get()));
    }

    // 获取用户没订阅的分类
    @GetMapping("/noSubscribe")
    public ApiResponse listNoSubscribeType(){
        return ApiResponse.ok().data(userService.listNoSubscribeType(UserHolder.get()));
    }


// 关注/取关
// @param followsUserId
// @return

    @PostMapping("/follows")
    public ApiResponse follows(@RequestParam Long followsUserId){

        return ApiResponse.ok().message(userService.follows(followsUserId) ? "已关注" : "已取关");
    }


// 用户停留时长修改模型
// @param model
// @return

    @PostMapping("/updateUserModel")
    public ApiResponse updateUserModel(@RequestBody Model model){
        final Long currentUserId = UserHolder.get();
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        if (model == null || model.getWorkId() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "作品不能为空");
        }
        final Work work = workMapper.selectById(model.getWorkId());
        if (work == null || !"VIDEO".equals(work.getWorkType())
                || !Integer.valueOf(AuditStatus.SUCCESS.code()).equals(work.getAuditStatus())
                || Boolean.TRUE.equals(work.getOpenState())) {
            throw new AppException(ErrorCode.WORK_NOT_FOUND, "视频作品不存在");
        }

        final Double score;
        if (model.getWatchTimeSeconds() != null) {
            final double durationSeconds = durationSeconds(work.getDuration());
            final double watchTimeSeconds = model.getWatchTimeSeconds();
            if (!Double.isFinite(watchTimeSeconds) || watchTimeSeconds < 0
                    || durationSeconds <= 0 || watchTimeSeconds > durationSeconds) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "观看时长不合法");
            }
            if (model.getWatchSessionId() == null || model.getWatchSessionId().isBlank()
                    || model.getWatchSessionId().length() > 100) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "播放会话不能为空");
            }
            final String idempotencyKey = org.hcz.constant.RedisKeys.USER_WATCH_SCORE
                    + currentUserId + ":" + work.getId() + ":" + model.getWatchSessionId();
            final Boolean firstReport = redisTemplate.opsForValue()
                    .setIfAbsent(idempotencyKey, "1", 1, TimeUnit.DAYS);
            if (Boolean.FALSE.equals(firstReport)) {
                return ApiResponse.ok();
            }
            score = scoreForWatchRatio(watchTimeSeconds / durationSeconds);
        } else if (model.getScore() != null
                && (Double.compare(model.getScore(), -0.5D) == 0
                || Double.compare(model.getScore(), 1.0D) == 0)) {
            // 兼容旧版前端；新前端必须使用 watchTimeSeconds。
            score = model.getScore();
        } else {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "观看时长不能为空");
        }

        final List<Model> models = new ArrayList<>();
        for (String label : work.buildLabel()) {
            final Model labelModel = new Model();
            labelModel.setLabel(label);
            labelModel.setWorkId(work.getId());
            labelModel.setScore(score);
            models.add(labelModel);
        }
        if (!models.isEmpty()) {
            final UserModel userModel = new UserModel();
            userModel.setUserId(currentUserId);
            userModel.setModels(models);
            userService.updateUserModel(userModel);
        }
        return ApiResponse.ok();
    }


// 将存储的 mm:ss 或 HH:mm:ss 显示值转换为秒数。

    public static double durationSeconds(String value) {
        if (value == null || value.isBlank()) {
            return 0.0D;
        }
        final String[] parts = value.trim().split(":");
        if (parts.length > 3) {
            return 0.0D;
        }
        try {
            double seconds = 0.0D;
            for (int i = 0; i < parts.length; i++) {
                final String part = parts[i];
                final double parsed = Double.parseDouble(part);
                if (!Double.isFinite(parsed) || parsed < 0) {
                    return 0.0D;
                }
                if ((parts.length == 2 && i == 0 || i > 0) && parsed >= 60.0D) {
                    return 0.0D;
                }
                seconds = seconds * 60.0D + parsed;
            }
            return seconds;
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }

    public static double scoreForWatchRatio(double ratio) {
        if (ratio >= 0.8D) return 1.0D;
        if (ratio >= 0.5D) return 0.6D;
        if (ratio >= 0.2D) return 0.2D;
        return -0.5D;
    }


// 获取用户上传头像的token
// @return

    @GetMapping("/avatar/token")
    public ApiResponse avatarToken(@RequestParam(required = false) String filename){
        return ApiResponse.ok().data(ossFileService.createUploadTicket("image/jpeg", filename));
    }


// 修改用户信息
// @param user
// @return

    @PutMapping
    public ApiResponse updateUser(@RequestBody @Validated UpdateUserVO user){
        userService.updateUser(user);
        return ApiResponse.ok().message("修改成功");
    }


}
