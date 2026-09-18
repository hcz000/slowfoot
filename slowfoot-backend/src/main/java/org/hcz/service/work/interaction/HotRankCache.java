package org.hcz.service.work.interaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.hcz.constant.RedisKeys;
import org.hcz.entity.vo.WorkRankItem;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.connection.zset.Tuple;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;


// 访问以原始 WorkRankItem JSON 形式写入的热榜成员。

@Component
public class HotRankCache {

    private static final int HOT_RANK_LIMIT = 10;
    private static final byte[] HOT_RANK_REVISION_KEY = RedisKeys.HOT_RANK_REVISION.getBytes(StandardCharsets.UTF_8);
    private static final byte[] UPSERT_SCRIPT = (""
            + "local members = redis.call('ZRANGE', KEYS[1], 0, -1) "
            + "local old = nil "
            + "for _, member in ipairs(members) do "
            + "  local ok, item = pcall(cjson.decode, member) "
            + "  if ok and item['workId'] and tostring(item['workId']) == ARGV[3] then "
            + "    local existing = redis.call('ZSCORE', KEYS[1], member) "
            + "    if not old or tonumber(existing) > tonumber(old) then old = existing end "
            + "  end "
            + "end "
            + "if old and tonumber(ARGV[1]) < tonumber(old) then return 0 end "
            + "for _, member in ipairs(members) do "
            + "  local ok, item = pcall(cjson.decode, member) "
            + "  if ok and item['workId'] and tostring(item['workId']) == ARGV[3] then redis.call('ZREM', KEYS[1], member) end "
            + "end "
            + "redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2]) "
            + "local size = redis.call('ZCARD', KEYS[1]) "
            + "local limit = tonumber(ARGV[4]) "
            + "if size > limit then redis.call('ZREMRANGEBYRANK', KEYS[1], 0, size - limit - 1) end "
            + "redis.call('INCR', KEYS[2]) "
            + "return 1").getBytes(StandardCharsets.UTF_8);


// 仅当没有更新的互动改变热榜时，才发布定时快照。

    private static final byte[] REPLACE_SNAPSHOT_SCRIPT = (""
            + "local current = redis.call('GET', KEYS[2]) "
            + "if not current then current = '0' end "
            + "if tonumber(current) ~= tonumber(ARGV[1]) then return 0 end "
            + "redis.call('DEL', KEYS[1]) "
            + "for i = 2, #ARGV, 2 do "
            + "  redis.call('ZADD', KEYS[1], ARGV[i], ARGV[i + 1]) "
            + "end "
            + "redis.call('INCR', KEYS[2]) "
            + "return 1").getBytes(StandardCharsets.UTF_8);

    private final RedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public HotRankCache(RedisTemplate redisTemplate) {
        this(redisTemplate, new ObjectMapper());
    }

    public HotRankCache(RedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }


// 原子地保留成员的最新分数，并裁剪最低排名。

    public void upsert(WorkRankItem rankItem, double score) {
        final byte[] key = RedisKeys.HOT_RANK.getBytes(StandardCharsets.UTF_8);
        final byte[] member;
        try {
            member = objectMapper.writeValueAsString(rankItem).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("热榜序列化失败", e);
        }
        byte[] scoreBytes = Double.toString(score).getBytes(StandardCharsets.UTF_8);
        byte[] workIdBytes = String.valueOf(rankItem.getWorkId()).getBytes(StandardCharsets.UTF_8);
        byte[] limitBytes = Integer.toString(HOT_RANK_LIMIT).getBytes(StandardCharsets.UTF_8);
        redisTemplate.execute((RedisCallback<Long>) connection ->
                connection.scriptingCommands().eval(
                        UPSERT_SCRIPT, ReturnType.INTEGER, 2, key, HOT_RANK_REVISION_KEY,
                        scoreBytes, member, workIdBytes, limitBytes));
    }

    public long currentRevision() {
        final byte[] value = (byte[]) redisTemplate.execute((RedisCallback<byte[]>) connection ->
                connection.get(HOT_RANK_REVISION_KEY));
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(new String(value, StandardCharsets.UTF_8));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    // 热榜成员使用底层连接读取，因为写入的是不带 RedisTemplate 类型头的 JSON。
    public List<WorkRankItem> readAll() {
        final byte[] key = RedisKeys.HOT_RANK.getBytes(StandardCharsets.UTF_8);
        final Set<Tuple> tuples =
                (Set<Tuple>) redisTemplate.execute(
                        (RedisCallback<Set<Tuple>>) connection ->
                                connection.zRevRangeWithScores(key, 0, -1));
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }
        final List<WorkRankItem> result = new ArrayList<>(tuples.size());
        for (Tuple tuple : tuples) {
            try {
                WorkRankItem item = objectMapper.readValue(tuple.getValue(), WorkRankItem.class);
                item.setHot(tuple.getScore());
                item.hotFormat();
                result.add(item);
            } catch (IOException ignored) {
                // Ignore malformed legacy ranking entries.
            }
        }
        return result;
    }

    public Long randomWorkId() {
        final List<WorkRankItem> rankItems = readAll();
        if (rankItems.isEmpty()) {
            return null;
        }
        return rankItems.get((int) (Math.random() * rankItems.size())).getWorkId();
    }


// 原子地发布定时快照。当互动事件在扫描运行时改变了版本号，过期的扫描结果会被丢弃。

    public boolean replaceSnapshot(Collection<WorkRankItem> rankItems, long expectedRevision) {
        final byte[] rankKey = RedisKeys.HOT_RANK.getBytes(StandardCharsets.UTF_8);
        final byte[][] args = new byte[1 + rankItems.size() * 2][];
        args[0] = Long.toString(expectedRevision).getBytes(StandardCharsets.UTF_8);
        int index = 1;
        for (WorkRankItem rankItem : rankItems) {
            final Double score = rankItem.getHot();
            if (score == null || !Double.isFinite(score)) {
                continue;
            }
            rankItem.setHot(null);
            try {
                args[index++] = score.toString().getBytes(StandardCharsets.UTF_8);
                args[index++] = objectMapper.writeValueAsBytes(rankItem);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("热榜序列化失败", e);
            }
        }
        final byte[][] actualArgs = index == args.length ? args : Arrays.copyOf(args, index);
        final Long result = (Long) redisTemplate.execute((RedisCallback<Long>) connection ->
                connection.scriptingCommands().eval(
                        REPLACE_SNAPSHOT_SCRIPT, ReturnType.INTEGER, 2,
                        concat(rankKey, HOT_RANK_REVISION_KEY, actualArgs)));
        return Long.valueOf(1L).equals(result);
    }


// 移除属于某作品的所有热榜成员。JSON 故意通过低级连接读取，
// 因为热榜写入绕过 RedisTemplate 的值序列化器。

    public void removeWork(Long workId) {
        if (workId == null) {
            return;
        }
        byte[] key = RedisKeys.HOT_RANK.getBytes(StandardCharsets.UTF_8);
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            Set<byte[]> members = connection.zRange(key, 0, -1);
            if (members == null) {
                return null;
            }
            boolean removed = false;
            for (byte[] member : members) {
                if (belongsTo(member, workId)) {
                    connection.zRem(key, member);
                    removed = true;
                }
            }
            if (removed) {
                connection.incr(HOT_RANK_REVISION_KEY);
            }
            return null;
        });
    }

    private boolean belongsTo(byte[] member, Long workId) {
        try {
            WorkRankItem rankItem = objectMapper.readValue(member, WorkRankItem.class);
            return workId.equals(rankItem.getWorkId());
        } catch (IOException ignored) {
            // 保留格式错误或遗留成员；此操作必须只移除确认匹配的项目。
            return false;
        }
    }

    private static byte[][] concat(byte[] first, byte[] second, byte[][] args) {
        final byte[][] values = new byte[args.length + 2][];
        values[0] = first;
        values[1] = second;
        System.arraycopy(args, 0, values, 2, args.length);
        return values;
    }
}
