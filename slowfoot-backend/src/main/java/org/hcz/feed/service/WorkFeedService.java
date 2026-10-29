package org.hcz.feed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hcz.common.constant.RedisKeys;
import org.hcz.common.constant.AuditStatus;
import org.hcz.work.dto.WorkVO;
import org.hcz.work.entity.Work;
import org.hcz.work.mapper.WorkMapper;
import org.hcz.feed.service.FeedService;
import org.hcz.user.service.FollowService;
import org.hcz.work.service.query.WorkViewSupport;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.List;

@Service
public class WorkFeedService {

    private final WorkMapper workMapper;
    private final FollowService followService;
    private final FeedService feedService;
    private final RedisTemplate redisTemplate;
    private final WorkViewSupport viewSupport;

    public WorkFeedService(WorkMapper workMapper,
                               FollowService followService,
                               FeedService feedService,
                               RedisTemplate redisTemplate,
                               WorkViewSupport viewSupport) {
        this.workMapper = workMapper;
        this.followService = followService;
        this.feedService = feedService;
        this.redisTemplate = redisTemplate;
        this.viewSupport = viewSupport;
    }

    public Collection<WorkVO> followFeed(Long userId, Long lastTime) {
        Set<Long> ids;
        try {
            ids = redisTemplate.opsForZSet().reverseRangeByScore(
                    RedisKeys.IN_FOLLOW + userId,
                    0,
                    lastTime == null ? new Date().getTime() : lastTime,
                    lastTime == null ? 0 : 1,
                    5);
        } catch (Exception ignored) {
            ids = Collections.emptySet();
        }
        Collection<Work> works;
        if (ObjectUtils.isEmpty(ids)) {
            Collection<Long> followIds = followService.getFollow(userId, null);
            if (ObjectUtils.isEmpty(followIds)) return Collections.emptyList();
            var query = new LambdaQueryWrapper<Work>().in(Work::getUserId, followIds)
                    .eq(Work::getAuditStatus, AuditStatus.SUCCESS.code())
                    .eq(Work::getOpenState, 0)
                    .orderByDesc(Work::getGmtCreated)
                    .last("limit 5");
            if (lastTime != null) query.lt(Work::getGmtCreated, new Date(lastTime));
            works = workMapper.selectList(query);
        } else {
            works = workMapper.selectList(new LambdaQueryWrapper<Work>()
                    .in(Work::getId, ids)
                    .eq(Work::getAuditStatus, AuditStatus.SUCCESS.code())
                    .eq(Work::getOpenState, false)
                    .orderByDesc(Work::getGmtCreated));
        }
        if (ObjectUtils.isEmpty(works)) {
            Collection<Long> followIds = followService.getFollow(userId, null);
            if (ObjectUtils.isEmpty(followIds)) return Collections.emptyList();
            var query = new LambdaQueryWrapper<Work>().in(Work::getUserId, followIds)
                    .eq(Work::getAuditStatus, AuditStatus.SUCCESS.code())
                    .eq(Work::getOpenState, 0)
                    .orderByDesc(Work::getGmtCreated)
                    .last("limit 5");
            if (lastTime != null) query.lt(Work::getGmtCreated, new Date(lastTime));
            works = workMapper.selectList(query);
        }
        return viewSupport.toWorkVos(works);
    }

    public void initFollowFeed(Long userId) {
        Collection<Long> followIds = followService.getFollow(userId, null);
        feedService.initFollowFeed(userId, followIds);
    }
}
