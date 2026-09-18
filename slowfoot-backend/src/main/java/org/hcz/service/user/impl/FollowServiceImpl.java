package org.hcz.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hcz.constant.RedisKeys;
import org.hcz.constant.NotificationType;
import org.hcz.entity.user.Follow;
import org.hcz.entity.work.Work;
import org.hcz.entity.work.WorkType;
import org.hcz.entity.vo.BasePage;
import org.hcz.exception.AppException;
import org.hcz.exception.ErrorCode;
import org.hcz.mapper.user.FollowMapper;
import org.hcz.mapper.work.WorkMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.hcz.service.feed.FeedService;
import org.hcz.service.notification.NotificationService;
import org.hcz.service.user.FollowService;
import org.hcz.util.RedisCacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.stream.Collectors;



@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService {

    @Autowired
    private FeedService feedService;

    @Autowired
    private WorkMapper workMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedisCacheUtil redisCacheUtil;

    @Autowired
    private NotificationService notificationService;

    @Override
    public long getFollowCount(Long userId) {
        return count(new LambdaQueryWrapper<Follow>().eq(Follow::getUserId, userId));
    }

    @Override
    public long getFansCount(Long userId) {
        return count(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowId, userId));
    }

    @Override
    public Collection<Long> getFollow(Long userId, BasePage basePage) {
        if (basePage == null) {
            try {
                final Set<Object> set = redisCacheUtil.zGet(RedisKeys.USER_FOLLOW + userId);
                if (!ObjectUtils.isEmpty(set)) {
                    return set.stream().map(o->Long.valueOf(o.toString())).collect(Collectors.toList());
                }
            } catch (Exception ignored) {
                // Redis unavailable: fall back to the source table below.
            }
            return list(new LambdaQueryWrapper<Follow>().eq(Follow::getUserId, userId)
                    .orderByDesc(Follow::getGmtCreated)).stream()
                    .map(Follow::getFollowId).collect(Collectors.toList());
        }
        final Set<ZSetOperations.TypedTuple<Object>> typedTuples = redisCacheUtil.zSetGetByPage(RedisKeys.USER_FOLLOW + userId, basePage.getPage(), basePage.getLimit());
        // 可能redis崩了,从db拿
        if (ObjectUtils.isEmpty(typedTuples)) {
            final List<Follow> follows = page(basePage.page(),new LambdaQueryWrapper<Follow>().eq(Follow::getUserId, userId).orderByDesc(Follow::getGmtCreated)).getRecords();
            if (ObjectUtils.isEmpty(follows)) {
                return Collections.emptyList();
            }
            return follows.stream().map(Follow::getFollowId).collect(Collectors.toList());
        }
        return typedTuples.stream().map(t -> Long.parseLong(t.getValue().toString())).collect(Collectors.toList());
    }


    @Override
    public Collection<Long> getFans(Long userId, BasePage basePage) {
        if (basePage == null) {
            try {
                final Set<Object> set = redisCacheUtil.zGet(RedisKeys.USER_FANS + userId);
                if (!ObjectUtils.isEmpty(set)) {
                    return set.stream().map(o->Long.valueOf(o.toString())).collect(Collectors.toList());
                }
            } catch (Exception ignored) {
                // Redis unavailable: fall back to the source table below.
            }
            return list(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowId, userId)
                    .orderByDesc(Follow::getGmtCreated)).stream()
                    .map(Follow::getUserId).collect(Collectors.toList());
        }
        final Set<ZSetOperations.TypedTuple<Object>> typedTuples = redisCacheUtil.zSetGetByPage(RedisKeys.USER_FANS + userId, basePage.getPage(), basePage.getLimit());
        if (ObjectUtils.isEmpty(typedTuples)) {
            final List<Follow> follows = page(basePage.page(),new LambdaQueryWrapper<Follow>().eq(Follow::getFollowId, userId)).getRecords();
            if (ObjectUtils.isEmpty(follows)){
                return Collections.emptyList();
            }
            return follows.stream().map(Follow::getUserId).collect(Collectors.toList());
        }
        return typedTuples.stream().map(t -> Long.parseLong(t.getValue().toString())).collect(Collectors.toList());
    }

    @Override
    public Boolean follows(Long followsId, Long userId) {

        if (followsId.equals(userId)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "你不能关注自己");
        }

        // 直接保存(唯一索引),保存失败则删除
        final Follow follow = new Follow();
        follow.setFollowId(followsId);
        follow.setUserId(userId);
        try {
            save(follow);
            final Date date = new Date();
            // 自己关注列表添加
            redisTemplate.opsForZSet().add(RedisKeys.USER_FOLLOW + userId, followsId, date.getTime());
            // 对方粉丝列表添加
            redisTemplate.opsForZSet().add(RedisKeys.USER_FANS + followsId, userId, date.getTime());
        } catch (Exception e) {
            // 删除
            remove(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowId, followsId).eq(Follow::getUserId, userId));
            // 删除收件箱的作品
            // 获取关注人的作品
            final List<Long> videoIds = workMapper.selectList(new LambdaQueryWrapper<Work>()
                            .eq(Work::getWorkType, WorkType.VIDEO.name())
                            .eq(Work::getUserId, followsId)
                            .eq(Work::getOpenState, 0)
                            .select(Work::getId))
                    .stream()
                    .map(Work::getId)
                    .collect(Collectors.toList());
            feedService.deleteInBoxFeed(userId, videoIds);
            // 自己关注列表删除
            redisTemplate.opsForZSet().remove(RedisKeys.USER_FOLLOW + userId, followsId);
            // 对方粉丝列表删除
            redisTemplate.opsForZSet().remove(RedisKeys.USER_FANS + followsId, userId);
            return false;
        }

        try {
            notificationService.publish(followsId, userId, NotificationType.FOLLOW,
                    followsId, null, "有人关注了你");
        } catch (Exception ignored) {
            // Notification persistence must not change the follow result.
        }

        return true;
    }

    @Override
    public Boolean isFollows(Long followId, Long userId) {
        if (userId == null || followId == null) return false;
        return count(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowId,followId).eq(Follow::getUserId,userId)) == 1;
    }
}
