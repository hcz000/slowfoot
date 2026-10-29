package org.hcz.recommendation.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hcz.common.constant.AuditStatus;
import org.hcz.common.constant.RedisKeys;
import org.hcz.setting.entity.Setting;
import org.hcz.work.entity.Work;
import org.hcz.work.dto.WorkRankItem;
import org.hcz.work.entity.WorkType;
import org.hcz.setting.service.SettingService;
import org.hcz.work.service.WorkService;
import org.hcz.work.service.interaction.HotRankCache;
import org.hcz.common.util.RedisCacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


// 热门视频计算和排行榜定时任务。

@Component
public class HotRankScheduler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HotRankScheduler.class);

    private static final double FRESHNESS_WINDOW_DAYS = 3.0D;
    private static volatile boolean freshnessEnabled = true;
    private static volatile double freshnessBonusPerDay = 5.0D;

    @Autowired
    private WorkService workService;

    @Autowired
    private SettingService settingService;

    @Autowired
    private RedisCacheUtil redisCacheUtil;


    @Autowired
    private HotRankCache hotRankCache;

    @Value("${recommendation.hot.freshness-enabled:true}")
    void setFreshnessEnabled(boolean enabled) {
        freshnessEnabled = enabled;
    }

    @Value("${recommendation.hot.freshness-bonus-per-day:5.0}")
    void setFreshnessBonusPerDay(double bonusPerDay) {
        freshnessBonusPerDay = Math.max(0.0D, bonusPerDay);
    }


// 每小时扫描视频，在 JVM 中使用 TopKSelector 筛选热度最高的 10 个视频。
// 分布式锁避免多实例重复扫描；拿不到锁（含 Redis 异常）时直接跳过，
// 保留上一版热榜，revision 保护仍保证不会覆盖更新的互动结果。

    @Scheduled(cron = "0 0 */1 * * ?")
    public void hotRank() {
        final String token = trySchedulerLock("hot-rank", TimeUnit.MINUTES.toSeconds(50));
        if (token == null) {
            return;
        }
        try {
            doHotRank();
        } finally {
            releaseSchedulerLock("hot-rank", token);
        }
    }

    private void doHotRank() {
        final long revision = hotRankCache.currentRevision();
        final TopKSelector selector = new TopKSelector(10,
                new PriorityQueue<>(10, Comparator.comparing(WorkRankItem::getHot)));
        final long limit = 1000;
        long id = 0;
        List<Work> works = queryRankWorks(id, limit);

        while (!ObjectUtils.isEmpty(works)) {
            for (Work work : works) {
                double weight = value(work.getShareCount())
                        + value(work.getViewCount()) * 0.8D
                        + value(work.getLikeCount())
                        + value(work.getFavoriteCount()) * 1.5D
                        + weightRandom();
                double hot = hot(weight, ageInDays(work.getGmtCreated()));
                selector.add(new WorkRankItem(hot, work.getId(), work.getTitle()));
            }
            id = works.get(works.size() - 1).getId();
            works = queryRankWorks(id, limit);
        }

        final List<WorkRankItem> hotVideos = selector.get();
        hotRankCache.replaceSnapshot(hotVideos, revision);
    }


// work_type = 'VIDEO' 是显式的产品过滤条件：热度榜只统计视频作品。

    private List<Work> queryRankWorks(long id, long limit) {
        return workService.list(new LambdaQueryWrapper<Work>()
                .select(Work::getId, Work::getShareCount, Work::getViewCount,
                        Work::getLikeCount, Work::getFavoriteCount,
                        Work::getGmtCreated, Work::getTitle)
                .gt(Work::getId, id)
                .eq(Work::getAuditStatus, AuditStatus.SUCCESS.code())
                .eq(Work::getWorkType, WorkType.VIDEO.name())
                .eq(Work::getOpenState, 0)
                .last("limit " + limit));
    }


// 每三小时计算最近三天内的热门作品集合。同样受分布式锁保护，避免多实例重复写入。

    @Scheduled(cron = "0 0 */3 * * ?")
    public void hotVideo() {
        final String token = trySchedulerLock("hot-work", TimeUnit.HOURS.toSeconds(2) + TimeUnit.MINUTES.toSeconds(30));
        if (token == null) {
            return;
        }
        try {
            doHotVideo();
        } finally {
            releaseSchedulerLock("hot-work", token);
        }
    }

    private void doHotVideo() {
        final int limit = 1000;
        long id = 1;
        List<Work> works = workService.selectRecentWorks(id, 3, limit);
        List<Setting> settings = settingService.list(new LambdaQueryWrapper<Setting>());
        if (ObjectUtils.isEmpty(settings)) {
            return;
        }
        final Double hotLimit = settings.get(0).getHotLimit();
        final int today = Calendar.getInstance().get(Calendar.DATE);

        while (!ObjectUtils.isEmpty(works)) {
            final ArrayList<Long> hotVideos = new ArrayList<>();
            for (Work work : works) {
                double weight = value(work.getShareCount())
                        + value(work.getViewCount()) * 0.8D
                        + value(work.getLikeCount())
                        + value(work.getFavoriteCount()) * 1.5D;
                if (hot(weight, ageInDays(work.getGmtCreated())) > hotLimit) {
                    hotVideos.add(work.getId());
                }
            }
            if (!ObjectUtils.isEmpty(hotVideos)) {
                String key = RedisKeys.HOT_WORK + today;
                redisCacheUtil.sSet(key, hotVideos.toArray());
                redisCacheUtil.expire(key, TimeUnit.DAYS.toSeconds(3));
            }
            id = works.get(works.size() - 1).getId();
            works = workService.selectRecentWorks(id, 3, limit);
        }
    }


// 热度随视频发布时间增长而衰减。

    public static double hot(double weight, double days) {
        final double safeDays = Double.isFinite(days) ? Math.max(0.0D, days) : 0.0D;
        final double freshness = freshnessEnabled && safeDays < FRESHNESS_WINDOW_DAYS
                ? (FRESHNESS_WINDOW_DAYS - safeDays) * freshnessBonusPerDay : 0.0D;
        return (weight + freshness) * Math.exp(-0.011D * safeDays);
    }


// 为热度相同的视频增加极小随机值，避免排序完全固定。

    public double weightRandom() {
        int value = (int) ((Math.random() * 9 + 1) * 100000);
        return value / 1000000.0D;
    }

    private double value(Number number) {
        return number == null ? 0.0D : number.doubleValue();
    }

    // 获取定时任务分布式锁；锁被占用或 Redis 异常都返回 null（跳过本轮，不重复消耗数据库）。
    private String trySchedulerLock(String task, long ttlSeconds) {
        final String token = UUID.randomUUID().toString();
        try {
            if (redisCacheUtil.tryLock(RedisKeys.SCHEDULER_LOCK + task, token, ttlSeconds)) {
                return token;
            }
            log.debug("Scheduler task {} is already running on another instance", task);
            return null;
        } catch (DataAccessException e) {
            log.warn("Scheduler lock unavailable for task {}, skipping this round", task, e);
            return null;
        }
    }

    private void releaseSchedulerLock(String task, String token) {
        try {
            redisCacheUtil.unlock(RedisKeys.SCHEDULER_LOCK + task, token);
        } catch (DataAccessException e) {
            log.warn("Scheduler lock release failed for task {}, will expire naturally", task, e);
        }
    }

    public static double ageInDays(Date createdAt) {
        if (createdAt == null) {
            return 0.0D;
        }
        long ageMillis = Math.max(0L, System.currentTimeMillis() - createdAt.getTime());
        return ageMillis / (double) TimeUnit.DAYS.toMillis(1);
    }
}
