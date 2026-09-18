package org.hcz.schedul;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hcz.constant.AuditStatus;
import org.hcz.constant.RedisKeys;
import org.hcz.entity.Setting;
import org.hcz.entity.work.Work;
import org.hcz.entity.vo.WorkRankItem;
import org.hcz.entity.work.WorkType;
import org.hcz.service.setting.SettingService;
import org.hcz.service.work.WorkService;
import org.hcz.service.work.interaction.HotRankCache;
import org.hcz.util.RedisCacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;


// 热门视频计算和排行榜定时任务。

@Component
public class HotRankScheduler {

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

    @Scheduled(cron = "0 0 */1 * * ?")
    public void hotRank() {
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


// 每三小时计算最近三天内的热门视频集合。

    @Scheduled(cron = "0 0 */3 * * ?")
    public void hotVideo() {
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

    public static double ageInDays(Date createdAt) {
        if (createdAt == null) {
            return 0.0D;
        }
        long ageMillis = Math.max(0L, System.currentTimeMillis() - createdAt.getTime());
        return ageMillis / (double) TimeUnit.DAYS.toMillis(1);
    }
}
