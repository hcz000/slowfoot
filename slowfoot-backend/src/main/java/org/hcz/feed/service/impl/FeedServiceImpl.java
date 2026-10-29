package org.hcz.feed.service.impl;

import org.hcz.common.constant.RedisKeys;
import org.hcz.feed.service.FeedService;
import org.hcz.common.util.DateUtil;
import org.hcz.common.util.RedisCacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisCommands;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;



@Service
public class FeedServiceImpl implements FeedService {


    @Autowired
    private RedisCacheUtil redisCacheUtil;

    @Autowired
    private RedisTemplate redisTemplate;


    @Override
    @Async
    public void pusOutBoxFeed(Long userId, Long videoId, Long time) {
        pusOutBoxFeedSync(userId, videoId, time);
    }

    @Override
    public void pusOutBoxFeedSync(Long userId, Long videoId, Long time) {
        redisCacheUtil.zadd(RedisKeys.OUT_FOLLOW + userId, time, videoId, -1);
    }

    @Override
    public void pushInBoxFeed(Long userId, Long videoId, Long time) {
        // 需要推吗这个场景？只需要拉
    }

    @Override
    @Async
    public void deleteOutBoxFeed(Long userId,Collection<Long> fans,Long videoId) {
        deleteOutBoxFeedSync(userId, fans, videoId);
    }

    @Override
    public void deleteOutBoxFeedSync(Long userId,Collection<Long> fans,Long videoId) {
        String t = RedisKeys.IN_FOLLOW;
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            RedisCommands commands = connection.commands();
            for (Long fan : fans) {
                commands.zRem((t+fan).getBytes(StandardCharsets.UTF_8),String.valueOf(videoId).getBytes(StandardCharsets.UTF_8));
            }
            commands.zRem((RedisKeys.OUT_FOLLOW + userId).getBytes(StandardCharsets.UTF_8), String.valueOf(videoId).getBytes(StandardCharsets.UTF_8));
            return null;
        });
    }

    @Override
    @Async
    public void deleteInBoxFeed(Long userId,List<Long> videoIds) {
        redisTemplate.opsForZSet().remove(RedisKeys.IN_FOLLOW + userId, videoIds.toArray());
    }


    @Override
    @Async
    public void initFollowFeed(Long userId,Collection<Long> followIds) {
        String t2 = RedisKeys.IN_FOLLOW;
        final Date curDate = new Date();
        final Date limitDate = DateUtil.addDateDays(curDate, -7);

        final Set<ZSetOperations.TypedTuple<Long>> set = redisTemplate.opsForZSet().rangeWithScores(t2 + userId, -1, -1);
        if (!ObjectUtils.isEmpty(set)) {
            Double oldTime = set.iterator().next().getScore();
            init(userId,oldTime.longValue(),new Date().getTime(),followIds);
        } else {
            init(userId,limitDate.getTime(),curDate.getTime(),followIds);
        }

    }
    // 初始化关注人的发件箱
    public void init(Long userId,Long min,Long max,Collection<Long> followIds) {
        String t1 = RedisKeys.OUT_FOLLOW;
        String t2 = RedisKeys.IN_FOLLOW;
        // 查看关注人的发件箱
        final List<Set<DefaultTypedTuple>> result = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                RedisCommands commands = connection.commands();
                for (Long followId : followIds) {
                    commands.zRevRangeByScoreWithScores((t1 + followId).getBytes(StandardCharsets.UTF_8), min, max, 0, 50);
                }
                return null;
            });
        final HashSet<Long> ids = new HashSet<>();
        // 遍历结果集，将视频ID添加到集合中，同时添加到发件箱
        for (Set<DefaultTypedTuple> tuples : result) {
            if (!ObjectUtils.isEmpty(tuples)) {
                for (DefaultTypedTuple tuple : tuples) {
                    final Long value = Long.parseLong(tuple.getValue().toString());
                    ids.add(value);
                    redisTemplate.opsForZSet().add(t2 + userId, value, tuple.getScore());
                }
            }
        }
        redisTemplate.expire(t2 + userId, RedisKeys.HISTORY_TIME, java.util.concurrent.TimeUnit.SECONDS);
    }

}
