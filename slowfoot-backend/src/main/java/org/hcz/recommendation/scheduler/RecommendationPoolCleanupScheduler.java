package org.hcz.recommendation.scheduler;

import org.hcz.common.constant.RedisKeys;
import org.hcz.common.util.RedisCacheUtil;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 移除七天阅读窗口外的推荐池桶。 */
@Component
public class RecommendationPoolCleanupScheduler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RecommendationPoolCleanupScheduler.class);
    private static final String TASK_NAME = "recommendation-pool-cleanup";
    private static final int SCAN_COUNT = 500;
    private static final int UNLINK_BATCH_SIZE = 500;
    private static final DateTimeFormatter POOL_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisCacheUtil redisCacheUtil;

    public RecommendationPoolCleanupScheduler(RedisTemplate<String, Object> redisTemplate,
                                              RedisCacheUtil redisCacheUtil) {
        this.redisTemplate = redisTemplate;
        this.redisCacheUtil = redisCacheUtil;
    }

    @Scheduled(cron = "${recommendation.pool.cleanup-cron:0 10 0 * * ?}")
    public void cleanupOldPools() {
        final String lockKey = RedisKeys.SCHEDULER_LOCK + TASK_NAME;
        final String token = UUID.randomUUID().toString();
        boolean locked = false;
        try {
            locked = redisCacheUtil.tryLock(lockKey, token, 2 * 60 * 60);
            if (!locked) {
                log.debug("Recommendation pool cleanup is already running on another instance");
                return;
            }

            // The read path keeps today through today-6. Today-7 and older are outside that window.
            final LocalDate cutoff = LocalDate.now().minusDays(7);
            final long removed = scanAndUnlink(RedisKeys.SYSTEM_STOCK, cutoff)
                    + scanAndUnlink(RedisKeys.SYSTEM_TYPE_STOCK, cutoff);
            log.info("Recommendation pool cleanup unlinked {} keys older than the seven-day window", removed);
        } catch (DataAccessException e) {
            log.warn("Recommendation pool cleanup failed; expired buckets will be retried on the next run", e);
        } finally {
            if (locked) {
                try {
                    redisCacheUtil.unlock(lockKey, token);
                } catch (DataAccessException e) {
                    log.warn("Recommendation pool cleanup lock release failed; the lock will expire naturally", e);
                }
            }
        }
    }

    private long scanAndUnlink(String basePrefix, LocalDate cutoff) {
        final List<String> batch = new ArrayList<>(UNLINK_BATCH_SIZE);
        long removed = 0;
        final ScanOptions options = ScanOptions.scanOptions()
                .match(basePrefix + "*")
                .count(SCAN_COUNT)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                final String key = cursor.next();
                if (isExpiredPoolKey(basePrefix, key, cutoff)) {
                    batch.add(key);
                    if (batch.size() >= UNLINK_BATCH_SIZE) {
                        removed += unlinkBatch(batch);
                        batch.clear();
                    }
                }
            }
        }

        if (!batch.isEmpty()) removed += unlinkBatch(batch);
        return removed;
    }

    private boolean isExpiredPoolKey(String basePrefix, String key, LocalDate cutoff) {
        final String dailyPrefix = basePrefix.equals(RedisKeys.SYSTEM_STOCK)
                ? RedisKeys.SYSTEM_STOCK_DAILY : RedisKeys.SYSTEM_TYPE_STOCK_DAILY;

        // Keys from the previous, unbucketed format are no longer read; remove them during migration.
        if (!key.startsWith(dailyPrefix)) return true;

        final int dateSeparator = key.lastIndexOf(':');
        if (dateSeparator < dailyPrefix.length() || dateSeparator == key.length() - 1) return false;
        try {
            final LocalDate bucketDate = LocalDate.parse(key.substring(dateSeparator + 1), POOL_DATE_FORMAT);
            return !bucketDate.isAfter(cutoff);
        } catch (DateTimeParseException ignored) {
            // Leave malformed keys for inspection instead of deleting data based on an unknown suffix.
            log.warn("Skipping recommendation pool key with an invalid date suffix: {}", key);
            return false;
        }
    }

    private long unlinkBatch(List<String> keys) {
        final List<String> batch = List.copyOf(keys);
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            final RedisCommands commands = connection.commands();
            for (String key : batch) {
                commands.unlink(key.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
        return batch.size();
    }
}
