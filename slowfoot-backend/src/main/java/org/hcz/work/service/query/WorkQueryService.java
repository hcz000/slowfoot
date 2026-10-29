package org.hcz.work.service.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.hcz.common.constant.AuditStatus;
import org.hcz.common.constant.RedisKeys;
import org.hcz.history.entity.WorkViewHistory;
import org.hcz.user.entity.User;
import org.hcz.common.vo.BasePage;
import org.hcz.work.dto.WorkRankItem;
import org.hcz.work.dto.WorkVO;
import org.hcz.work.entity.Work;
import org.hcz.common.exception.AppException;
import org.hcz.common.exception.ErrorCode;
import org.hcz.work.mapper.WorkMapper;
import org.hcz.history.mapper.WorkViewHistoryMapper;
import org.hcz.recommendation.scheduler.HotRankScheduler;
import org.hcz.recommendation.service.InterestPushService;
import org.hcz.user.service.FavoritesService;
import org.hcz.user.service.FollowService;
import org.hcz.user.service.UserService;
import org.hcz.work.service.category.WorkCategoryService;
import org.hcz.work.service.interaction.WorkLikeService;
import org.hcz.work.service.interaction.HotRankCache;
import org.hcz.common.util.RedisCacheUtil;
import org.hcz.common.util.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WorkQueryService {

    private final WorkMapper workMapper;
    private final WorkCategoryService workCategoryService;
    private final UserService userService;
    private final InterestPushService interestPushService;
    private final FavoritesService favoritesService;
    private final WorkLikeService workLikeService;
    private final FollowService followService;
    private final RedisCacheUtil redisCacheUtil;
    private final HotRankCache hotRankCache;
    private final WorkViewSupport viewSupport;
    private final WorkViewHistoryMapper workViewHistoryMapper;

    public WorkQueryService(WorkMapper workMapper,
                                WorkCategoryService workCategoryService,
                                UserService userService,
                                InterestPushService interestPushService,
                                FavoritesService favoritesService,
                                WorkLikeService workLikeService,
                                FollowService followService,
                                RedisCacheUtil redisCacheUtil,
                                HotRankCache hotRankCache,
                                WorkViewSupport viewSupport,
                                WorkViewHistoryMapper workViewHistoryMapper) {
        this.workMapper = workMapper;
        this.workCategoryService = workCategoryService;
        this.userService = userService;
        this.interestPushService = interestPushService;
        this.favoritesService = favoritesService;
        this.workLikeService = workLikeService;
        this.followService = followService;
        this.redisCacheUtil = redisCacheUtil;
        this.hotRankCache = hotRankCache;
        this.viewSupport = viewSupport;
        this.workViewHistoryMapper = workViewHistoryMapper;
    }

    public Work getMine(Long workId, Long userId) {
        Work work = workMapper.selectById(workId);
        if (work == null) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "作品不存在");
        if (!userId.equals(work.getUserId())) throw new AppException(ErrorCode.FORBIDDEN, "无权访问该作品");
        viewSupport.loadDetails(work);
        return work;
    }

    public IPage<Work> pageMine(BasePage basePage, Long userId) {
        IPage<Work> page = workMapper.selectPage(basePage.page(), new LambdaQueryWrapper<Work>()
                .eq(Work::getUserId, userId).orderByDesc(Work::getGmtCreated));
        page.getRecords().forEach(viewSupport::loadDetails);
        return page;
    }

    public WorkVO getPublicWork(Long id, Long userId) {
        final Work work = viewSupport.getWorkRecord(id);
        if (work == null) throw new AppException(ErrorCode.WORK_NOT_FOUND);
        // 公开详情/播放一律只放行审核通过的作品：未过审（审核中、审核失败、待人工复审）
        // 对所有人（包括作者本人）都视为不存在，作者只能在自己的"我的稿件"管理页查看未过审内容。
        final Integer auditStatus = work.getAuditStatus();
        if (auditStatus == null || auditStatus != AuditStatus.SUCCESS.code()) {
            throw new AppException(ErrorCode.WORK_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(work.getOpenState())) return new WorkVO();
        viewSupport.loadDetails(work);
        final WorkVO workVO = WorkVO.of(work);
        viewSupport.populateUserAndUrl(Collections.singleton(workVO));
        workVO.setLikeState(workLikeService.likeState(id, userId));
        workVO.setFavoriteState(favoritesService.favoritesState(id, userId));
        workVO.setFollowState(followService.isFollows(work.getUserId(), userId));
        return workVO;
    }

    public Collection<WorkVO> pushWorks(Long userId) {
        User user = userId == null ? null : userService.getById(userId);
        List<Work> recommendations = Collections.emptyList();
        try {
            Collection<Long> workIds = interestPushService.listWorkIdByUserModel(user);
            if (!ObjectUtils.isEmpty(workIds)) {
                recommendations = viewSupport.listWorksByIds(workIds).stream()
                        .filter(this::isPublicApprovedWork)
                        .collect(Collectors.toList());
            }
        } catch (RuntimeException ignored) {
            recommendations = Collections.emptyList();
        }

        List<Work> recentWorks = workMapper.selectList(new LambdaQueryWrapper<Work>()
                .eq(Work::getAuditStatus, AuditStatus.SUCCESS.code())
                .eq(Work::getOpenState, false)
                .orderByDesc(Work::getGmtCreated)
                .last("limit 24"));
        LinkedHashMap<Long, Work> mixedFeed = new LinkedHashMap<>();
        int size = Math.max(recentWorks.size(), recommendations.size());
        for (int index = 0; index < size; index++) {
            if (index < recentWorks.size()) {
                Work recent = recentWorks.get(index);
                if (isPublicApprovedWork(recent)) mixedFeed.putIfAbsent(recent.getId(), recent);
            }
            if (index < recommendations.size()) {
                Work recommendation = recommendations.get(index);
                if (isPublicApprovedWork(recommendation)) mixedFeed.putIfAbsent(recommendation.getId(), recommendation);
            }
        }

        List<WorkVO> candidates = viewSupport.toWorkVos(mixedFeed.values()).stream().collect(Collectors.toList());
        for (WorkVO candidate : candidates) {
            candidate.setLikeState(workLikeService.likeState(candidate.getId(), userId));
            candidate.setFavoriteState(favoritesService.favoritesState(candidate.getId(), userId));
            candidate.setFollowState(followService.isFollows(candidate.getUserId(), userId));
        }
        return diversifyByAuthor(candidates, 3, 24);
    }

    private boolean isPublicApprovedWork(Work work) {
        return work != null
                && !Boolean.TRUE.equals(work.getIsDeleted())
                && work.getAuditStatus() != null
                && work.getAuditStatus() == AuditStatus.SUCCESS.code()
                && !Boolean.TRUE.equals(work.getOpenState());
    }


// 保留候选顺序，同时按创作者上限和结果数量做约束。

    public static List<WorkVO> diversifyByAuthor(Collection<WorkVO> candidates,
                                                  int maxPerAuthor, int resultLimit) {
        if (ObjectUtils.isEmpty(candidates) || maxPerAuthor <= 0 || resultLimit <= 0) {
            return Collections.emptyList();
        }
        final Map<Long, Integer> authorCount = new HashMap<>();
        final List<WorkVO> result = new ArrayList<>(Math.min(resultLimit, candidates.size()));
        for (WorkVO candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            final Long authorId = candidate.getUserId();
            final int count = authorCount.getOrDefault(authorId, 0);
            if (count >= maxPerAuthor) {
                continue;
            }
            result.add(candidate);
            authorCount.put(authorId, count + 1);
            if (result.size() == resultLimit) {
                break;
            }
        }
        return result;
    }

    public Collection<WorkVO> listByCategory(Long categoryId) {
        if (categoryId == null || workCategoryService.getById(categoryId) == null) return Collections.emptyList();
        Collection<Long> workIds;
        try {
            workIds = interestPushService.listWorkIdByTypeId(categoryId);
        } catch (RuntimeException ignored) {
            workIds = Collections.emptyList();
        }
        if (ObjectUtils.isEmpty(workIds)) {
            List<Work> fallback = workMapper.selectList(new LambdaQueryWrapper<Work>()
                    .eq(Work::getTypeId, categoryId)
                    .eq(Work::getAuditStatus, AuditStatus.SUCCESS.code())
                    .eq(Work::getOpenState, 0)
                    .orderByDesc(Work::getGmtCreated)
                    .last("limit 12"));
            return viewSupport.toWorkVos(fallback);
        }
        Collection<WorkVO> cached = viewSupport.toWorkVos(viewSupport.listWorksByIds(workIds).stream()
                .filter(work -> Objects.equals(categoryId, work.getTypeId()))
                .filter(this::isPublicApprovedWork).toList());
        if (!cached.isEmpty()) return cached;
        List<Work> fallback = workMapper.selectList(new LambdaQueryWrapper<Work>()
                .eq(Work::getTypeId, categoryId)
                .eq(Work::getAuditStatus, AuditStatus.SUCCESS.code())
                .eq(Work::getOpenState, 0)
                .orderByDesc(Work::getGmtCreated)
                .last("limit 12"));
        return viewSupport.toWorkVos(fallback);
    }

    public IPage<WorkVO> searchWorks(String search, BasePage basePage, Long userId) {
        LambdaQueryWrapper<Work> wrapper = new LambdaQueryWrapper<Work>()
                .eq(Work::getAuditStatus, AuditStatus.SUCCESS.code())
                .eq(Work::getOpenState, false);
        if (!ObjectUtils.isEmpty(search)) wrapper.and(w -> w.like(Work::getTitle, search).or().eq(Work::getWorkNo, search));
        IPage<Work> page = workMapper.selectPage(basePage.page(), wrapper);
        page.getRecords().forEach(viewSupport::loadDetails);
        IPage<WorkVO> result = page.convert(WorkVO::of);
        viewSupport.populateUserAndUrl(result.getRecords());
        userService.addSearchHistory(userId, search);
        return result;
    }

    public LinkedHashMap<String, List<WorkVO>> getHistory(BasePage basePage) {
        final Long userId = UserHolder.get();
        Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<Object>> tuples = null;
        try {
            tuples = redisCacheUtil.zSetGetByPage(RedisKeys.USER_HISTORY_WORK + userId,
                    basePage.getPage(), basePage.getLimit());
        } catch (RuntimeException ignored) {
            // Fall through to the durable history table.
        }
        LinkedHashMap<String, List<WorkVO>> cached = historyFromRedis(tuples);
        return cached.isEmpty() ? historyFromDatabase(userId, basePage) : cached;
    }

    private LinkedHashMap<String, List<WorkVO>> historyFromRedis(
            Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<Object>> tuples) {
        if (ObjectUtils.isEmpty(tuples)) return new LinkedHashMap<>();
        List<Long> workIds = new ArrayList<>();
        Map<Long, Date> viewedAt = new LinkedHashMap<>();
        for (org.springframework.data.redis.core.ZSetOperations.TypedTuple<Object> tuple : tuples) {
            try {
                Long workId = Long.valueOf(String.valueOf(tuple.getValue()));
                workIds.add(workId);
                viewedAt.put(workId, new Date(tuple.getScore().longValue()));
            } catch (RuntimeException ignored) {
                return new LinkedHashMap<>();
            }
        }
        return groupHistory(workIds, viewedAt);
    }

    private LinkedHashMap<String, List<WorkVO>> historyFromDatabase(Long userId, BasePage basePage) {
        long page = basePage.getPage() == null ? 1L : basePage.getPage();
        long limit = basePage.getLimit() == null ? 15L : basePage.getLimit();
        List<WorkViewHistory> histories = workViewHistoryMapper.listRecent(userId, (page - 1) * limit, limit);
        List<Long> workIds = histories.stream().map(WorkViewHistory::getWorkId).toList();
        Map<Long, Date> viewedAt = histories.stream().collect(Collectors.toMap(
                WorkViewHistory::getWorkId, WorkViewHistory::getViewedAt, (first, ignored) -> first,
                LinkedHashMap::new));
        return groupHistory(workIds, viewedAt);
    }

    private LinkedHashMap<String, List<WorkVO>> groupHistory(List<Long> workIds, Map<Long, Date> viewedAt) {
        if (ObjectUtils.isEmpty(workIds)) return new LinkedHashMap<>();
        Map<Long, WorkVO> workMap = viewSupport.toWorkVos(viewSupport.listWorksByIds(workIds)).stream()
                .collect(Collectors.toMap(WorkVO::getId, value -> value, (first, ignored) -> first,
                        LinkedHashMap::new));
        LinkedHashMap<String, List<WorkVO>> result = new LinkedHashMap<>();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        for (Long workId : workIds) {
            WorkVO work = workMap.get(workId);
            Date date = viewedAt.get(workId);
            if (work != null && date != null) {
                result.computeIfAbsent(format.format(date), ignored -> new ArrayList<>()).add(work);
            }
        }
        return result;
    }

    public Collection<WorkVO> listByFavorites(Long favoritesId) {
        List<Long> workIds = favoritesService.listWorkIds(favoritesId, UserHolder.get());
        return ObjectUtils.isEmpty(workIds) ? Collections.emptyList() : viewSupport.toWorkVos(viewSupport.listWorksByIds(workIds));
    }

    public List<WorkRankItem> hotRank() {
        try {
            List<WorkRankItem> cached = hotRankCache.readAll();
            if (!cached.isEmpty()) return cached;
        } catch (RuntimeException ignored) {
            // Fall through to a database snapshot.
        }
        List<Work> works = workMapper.selectList(new LambdaQueryWrapper<Work>()
                .eq(Work::getAuditStatus, AuditStatus.SUCCESS.code())
                .eq(Work::getOpenState, 0)
                .orderByDesc(Work::getLikeCount)
                .last("limit 10"));
        return works.stream().map(work -> {
            double weight = value(work.getShareCount()) + value(work.getViewCount()) * 0.8D
                    + value(work.getLikeCount()) + value(work.getFavoriteCount()) * 1.5D;
            return new WorkRankItem(HotRankScheduler.hot(weight,
                    HotRankScheduler.ageInDays(work.getGmtCreated())), work.getId(), work.getTitle());
        }).toList();
    }

    private double value(Number number) {
        return number == null ? 0.0D : number.doubleValue();
    }

    public Collection<WorkVO> listSimilar(Long workId) {
        Work work = viewSupport.getWorkRecord(workId);
        if (ObjectUtils.isEmpty(work) || ObjectUtils.isEmpty(work.getLabelNames())) return Collections.emptyList();
        List<String> labels = work.buildLabel();
        List<String> labelNames = new ArrayList<>(labels);
        labelNames.addAll(labels);
        Set<Long> workIds;
        try {
            workIds = new LinkedHashSet<>(interestPushService.listWorkIdByLabels(labelNames));
        } catch (RuntimeException ignored) {
            workIds = new LinkedHashSet<>();
        }
        workIds.remove(work.getId());
        if (!workIds.isEmpty()) {
            Collection<WorkVO> cached = viewSupport.toWorkVos(viewSupport.listWorksByIds(workIds).stream()
                    .filter(this::isPublicApprovedWork).toList());
            if (!cached.isEmpty()) return cached;
        }
        LambdaQueryWrapper<Work> fallback = new LambdaQueryWrapper<Work>()
                .eq(Work::getAuditStatus, AuditStatus.SUCCESS.code())
                .eq(Work::getOpenState, 0)
                .and(query -> {
                    query.like(Work::getLabelNames, labels.get(0));
                    for (int i = 1; i < labels.size(); i++) {
                        query.or().like(Work::getLabelNames, labels.get(i));
                    }
                })
                .orderByDesc(Work::getGmtCreated)
                .last("limit 12");
        return viewSupport.toWorkVos(workMapper.selectList(fallback));
    }

    public IPage<WorkVO> listPublicByUserId(Long userId, BasePage basePage) {
        if (userId == null) return new Page<>();
        IPage<Work> page = workMapper.selectPage(basePage.page(), new LambdaQueryWrapper<Work>()
                .eq(Work::getUserId, userId).eq(Work::getAuditStatus, AuditStatus.SUCCESS.code())
                .eq(Work::getOpenState, false)
                .orderByDesc(Work::getGmtCreated));
        page.getRecords().forEach(viewSupport::loadDetails);
        IPage<WorkVO> result = page.convert(WorkVO::of);
        viewSupport.populateUserAndUrl(result.getRecords());
        return result;
    }

    public List<Work> selectRecentWorks(long id, int days, int limit) {
        return workMapper.selectRecentWorks(id, days, limit);
    }

    public Collection<WorkVO> listHotWork() {
        Calendar calendar = Calendar.getInstance();
        int today = calendar.get(Calendar.DATE);
        Map<String, Integer> keys = new HashMap<>();
        keys.put(RedisKeys.HOT_WORK + today, 10);
        keys.put(RedisKeys.HOT_WORK + (today - 1), 3);
        keys.put(RedisKeys.HOT_WORK + (today - 2), 2);
        try {
            List<Long> values = redisCacheUtil.pipeline(connection -> {
                org.springframework.data.redis.connection.RedisCommands commands = connection.commands();
                keys.forEach((key, count) -> commands.sRandMember(key.getBytes(StandardCharsets.UTF_8), count));
                return null;
            });
            List<Long> workIds = new ArrayList<>();
            if (!ObjectUtils.isEmpty(values)) {
                for (Object value : values) if (!ObjectUtils.isEmpty(value)) workIds.addAll((List<Long>) value);
            }
            if (!workIds.isEmpty()) {
                Collection<WorkVO> cached = viewSupport.toWorkVos(viewSupport.listWorksByIds(workIds).stream()
                        .filter(this::isPublicApprovedWork).toList());
                if (!cached.isEmpty()) return cached;
            }
        } catch (RuntimeException ignored) {
            // Fall through to the database snapshot.
        }
        return viewSupport.toWorkVos(workMapper.selectList(new LambdaQueryWrapper<Work>()
                .eq(Work::getAuditStatus, AuditStatus.SUCCESS.code())
                .eq(Work::getOpenState, 0)
                .orderByDesc(Work::getLikeCount)
                .last("limit 10")));
    }
}
