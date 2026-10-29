package org.hcz.recommendation.service.impl;

import org.hcz.common.constant.RedisKeys;
import org.hcz.work.entity.Work;
import org.hcz.user.entity.User;
import org.hcz.common.vo.Model;
import org.hcz.user.dto.UserModel;
import org.hcz.recommendation.service.InterestPushService;
import org.hcz.work.service.category.WorkCategoryService;
import org.hcz.work.service.interaction.HotRankCache;
import org.hcz.common.util.RedisCacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisCommands;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;



// 暂时为异步
@Service
public class InterestPushServiceImpl implements InterestPushService {

    // 兴趣模型更新脚本：直接以 HINCRBYFLOAT 累加原始权重，仅当权重 <=0 时删除该标签。
    // 读取端 sampleLabelsByCdf 会做归一化，这里不再预归一化，以保证相对权重真实反映兴趣强弱、负反馈能衰减。
    private static final byte[] UPDATE_USER_MODEL_SCRIPT = """
            local key = KEYS[1]
            local eventId = ARGV[2]
            if eventId ~= '' and redis.call('EXISTS', KEYS[2]) == 1 then return -1 end
            for i = 3, #ARGV, 2 do
              local label = ARGV[i]
              local delta = tonumber(ARGV[i + 1])
              local value = redis.call('HINCRBYFLOAT', key, label, delta)
              if tonumber(value) <= 0 then redis.call('HDEL', key, label) end
            end
            if eventId ~= '' then redis.call('SET', KEYS[2], '1', 'EX', ARGV[1]) end
            return redis.call('HLEN', key)
            """.getBytes(StandardCharsets.UTF_8);

    private static final long USER_MODEL_EVENT_TTL_SECONDS = 30L * 24L * 60L * 60L;

    // 兴趣抽样平滑指数：<1 会压缩头部标签的优势，让长尾标签也有机会。
    private static final double INTEREST_SMOOTH_ALPHA = 0.5D;
    private static final int RECENT_POOL_DAYS = 7;
    private static final DateTimeFormatter POOL_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    @Autowired
    private RedisCacheUtil redisCacheUtil;

    @Autowired
    private WorkCategoryService workCategoryService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private HotRankCache hotRankCache;

    @Override
    @Async
    public void pushSystemStockIn(Work work) {
        pushSystemStockInSync(work);
    }

    @Override
    public void pushSystemStockInSync(Work work) {
        addWorkToPoolKeys(dailyLabelPoolKeys(work.buildLabel(), LocalDate.now()), work.getId());
    }

    @Override
    @Async
    public void pushSystemTypeStockIn(Work work) {
        pushSystemTypeStockInSync(work);
    }

    @Override
    public void pushSystemTypeStockInSync(Work work) {
        if (work.getTypeId() == null) return;
        addWorkToPoolKeys(dailyTypePoolKeys(work.getTypeId(), LocalDate.now()), work.getId());
    }

    @Override
    public Collection<Long> listWorkIdByTypeId(Long typeId) {
        if (typeId == null) return Collections.emptySet();
        final List<String> keys = new ArrayList<>();
        for (LocalDate date : recentPoolDates()) keys.add(dailyTypePoolKey(typeId, date));
        final List<Long> sampled = new ArrayList<>(sampleWorkIdsFromKeys(keys, 12));
        Collections.shuffle(sampled);
        return new LinkedHashSet<>(sampled.subList(0, Math.min(12, sampled.size())));
    }

    @Override
    @Async
    public void deleteSystemStockIn(Work work) {
        deleteSystemStockInSync(work);
    }

    @Override
    public void deleteSystemStockInSync(Work work) {
        removeWorkFromPoolKeys(dailyLabelPoolKeys(work.buildLabel(), recentPoolDates()), work.getId());
    }

    @Override
    @Async
    public void initUserModel(Long userId, List<String> labels) {

        final String key = RedisKeys.USER_MODEL + userId;
        Map<Object, Object> modelMap = new HashMap<>();
        if (!ObjectUtils.isEmpty(labels)) {
            final int size = labels.size();
            // 将标签分为等分概率,不可能超过100个分类
            double probabilityValue = 100 / size;
            for (String labelName : labels) {
                modelMap.put(labelName, probabilityValue);
            }
        }
        redisCacheUtil.del(key);
        redisCacheUtil.hmset(key, modelMap);
        // 为用户模型设置ttl

    }


    @Override
    public void updateUserModel(UserModel userModel) {
        updateUserModel(userModel, null);
    }

    @Override
    public void updateUserModel(UserModel userModel, String eventId) {
        final Long userId = userModel.getUserId();
        if (userId == null || userModel.getModels() == null) {
            return;
        }
        final List<Model> models = userModel.getModels();
        final byte[][] args = new byte[models.size() * 2][];
        for (int i = 0; i < models.size(); i++) {
            Model model = models.get(i);
            args[i * 2] = String.valueOf(model.getLabel()).getBytes(StandardCharsets.UTF_8);
            args[i * 2 + 1] = Double.toString(model.getScore()).getBytes(StandardCharsets.UTF_8);
        }
        final byte[] key = (RedisKeys.USER_MODEL + userId).getBytes(StandardCharsets.UTF_8);
        final String stableEventId = eventId == null ? "" : eventId;
        final byte[] eventKey = (RedisKeys.USER_MODEL_EVENT + userId + ":" + stableEventId)
                .getBytes(StandardCharsets.UTF_8);
        final byte[][] scriptArgs = new byte[args.length + 2][];
        scriptArgs[0] = Long.toString(USER_MODEL_EVENT_TTL_SECONDS).getBytes(StandardCharsets.UTF_8);
        scriptArgs[1] = stableEventId.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(args, 0, scriptArgs, 2, args.length);
        final byte[][] redisKeys = eventId == null
                ? new byte[][]{key} : new byte[][]{key, eventKey};
        final byte[][] keysAndArgs = Arrays.copyOf(redisKeys, redisKeys.length + scriptArgs.length);
        System.arraycopy(scriptArgs, 0, keysAndArgs, redisKeys.length, scriptArgs.length);
        redisTemplate.execute((RedisCallback<Long>) connection ->
                connection.scriptingCommands().eval(
                        UPDATE_USER_MODEL_SCRIPT, ReturnType.INTEGER,
                        redisKeys.length, keysAndArgs));
    }

    @Override
    public Collection<Long> listWorkIdByUserModel(User user) {
        // 创建结果集
        Set<Long> videoIds = new LinkedHashSet<>(20);

        if (user != null) {
            final Long userId = user.getId();
            // 从模型中拿概率
            final Map<Object, Object> modelMap = redisCacheUtil.hmget(RedisKeys.USER_MODEL + userId);
            if (!ObjectUtils.isEmpty(modelMap)) {
                final Boolean sex = user.getSex();
                // 按兴趣权重做 CDF 精确加权抽样（幂次平滑抑制头部垄断）
                final List<String> labelNames = new ArrayList<>(new LinkedHashSet<>(sampleLabelsByCdf(modelMap, 20)));
                // 每个兴趣标签从最近七天的桶中各抽样，候选仅在本次请求内存中保留。
                Set<Long> ids = sampleWorkIdsForLabels(labelNames, 1);
                String key2 = RedisKeys.HISTORY_WORK;

                // 去重
                List simpIds = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    RedisCommands commands = connection.commands();
                    for (Long id : ids) {
                        commands.get((key2 + id + ":" + userId).getBytes(StandardCharsets.UTF_8));
                    }
                    return null;
                });
                simpIds = (List) simpIds.stream().filter(o->!ObjectUtils.isEmpty(o)).collect(Collectors.toList());;
                if (!ObjectUtils.isEmpty(simpIds)){
                    for (Object simpId : simpIds) {
                        final Long l = Long.valueOf(simpId.toString());
                        if (ids.contains(l)){
                            ids.remove(l);
                        }
                    }
                }


                videoIds.addAll(ids);

                // 随机挑选一个视频,根据性别: 男：美女 女：宠物
                final Long aLong = randomWorkId(sex);
                if (aLong!=null){
                    videoIds.add(aLong);
                }


                return videoIds;
            }
        }
        // 游客
        // 随机获取足够的候选标签，后续还要按作者做多样性筛选。
        final List<String> labels = workCategoryService.random10Labels();
        if (ObjectUtils.isEmpty(labels)) return videoIds;
        final Set<String> labelNames = new LinkedHashSet<>();
        final int size = labels.size();
        final Random random = new Random();
        // 获取随机的标签
        for (int i = 0; i < 20; i++) {
            final int randomIndex = random.nextInt(size);
            labelNames.add(labels.get(randomIndex));
        }
        videoIds.addAll(sampleWorkIdsForLabels(labelNames, 1));

        return videoIds;
    }

    @Override
    public Collection<Long> listWorkIdByLabels(List<String> labelNames) {
        return sampleWorkIdsForLabels(labelNames, 1);
    }

    @Override
    @Async
    public void deleteSystemTypeStockIn(Work work) {
        deleteSystemTypeStockInSync(work);
    }

    @Override
    public void deleteSystemTypeStockInSync(Work work) {
        if (work.getTypeId() == null) return;
        removeWorkFromPoolKeys(dailyTypePoolKeys(work.getTypeId(), recentPoolDates()), work.getId());
    }


    private List<LocalDate> recentPoolDates() {
        final LocalDate today = LocalDate.now();
        final List<LocalDate> dates = new ArrayList<>(RECENT_POOL_DAYS);
        for (int offset = 0; offset < RECENT_POOL_DAYS; offset++) {
            dates.add(today.minusDays(offset));
        }
        return dates;
    }

    private String dailyLabelPoolKey(String label, LocalDate date) {
        return RedisKeys.SYSTEM_STOCK_DAILY + label + ":" + POOL_DATE_FORMAT.format(date);
    }

    private String dailyTypePoolKey(Long typeId, LocalDate date) {
        return RedisKeys.SYSTEM_TYPE_STOCK_DAILY + typeId + ":" + POOL_DATE_FORMAT.format(date);
    }

    private List<String> dailyLabelPoolKeys(Collection<String> labels, LocalDate date) {
        return dailyLabelPoolKeys(labels, Collections.singletonList(date));
    }

    private List<String> dailyLabelPoolKeys(Collection<String> labels, Collection<LocalDate> dates) {
        if (ObjectUtils.isEmpty(labels) || ObjectUtils.isEmpty(dates)) return Collections.emptyList();
        final LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String label : labels) {
            if (label == null || label.isBlank()) continue;
            for (LocalDate date : dates) keys.add(dailyLabelPoolKey(label, date));
        }
        return new ArrayList<>(keys);
    }

    private List<String> dailyTypePoolKeys(Long typeId, LocalDate date) {
        return dailyTypePoolKeys(typeId, Collections.singletonList(date));
    }

    private List<String> dailyTypePoolKeys(Long typeId, Collection<LocalDate> dates) {
        if (typeId == null || ObjectUtils.isEmpty(dates)) return Collections.emptyList();
        return dates.stream().map(date -> dailyTypePoolKey(typeId, date)).toList();
    }

    private Set<Long> sampleWorkIdsForLabels(Collection<String> labels, long countPerBucket) {
        return sampleWorkIdsFromKeys(dailyLabelPoolKeys(labels, recentPoolDates()), countPerBucket);
    }

    private Set<Long> sampleWorkIdsFromKeys(Collection<String> keys, long countPerKey) {
        if (ObjectUtils.isEmpty(keys) || countPerKey <= 0) return new LinkedHashSet<>();
        final List<Object> replies = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            RedisCommands commands = connection.commands();
            for (String key : keys) {
                commands.sRandMember(key.getBytes(StandardCharsets.UTF_8), countPerKey);
            }
            return null;
        });
        final LinkedHashSet<Long> workIds = new LinkedHashSet<>();
        for (Object reply : replies) addWorkIds(reply, workIds);
        return workIds;
    }

    private void addWorkIds(Object reply, Set<Long> workIds) {
        if (reply == null) return;
        if (reply instanceof Collection<?> values) {
            for (Object value : values) addWorkIds(value, workIds);
            return;
        }
        final String value = reply instanceof byte[] bytes
                ? new String(bytes, StandardCharsets.UTF_8) : reply.toString();
        try {
            workIds.add(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            // Ignore malformed historical members; valid pool members are numeric work IDs.
        }
    }

    private void addWorkToPoolKeys(Collection<String> keys, Long workId) {
        if (workId == null || ObjectUtils.isEmpty(keys)) return;
        final byte[] member = String.valueOf(workId).getBytes(StandardCharsets.UTF_8);
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            RedisCommands commands = connection.commands();
            for (String key : keys) commands.sAdd(key.getBytes(StandardCharsets.UTF_8), member);
            return null;
        });
    }

    private void removeWorkFromPoolKeys(Collection<String> keys, Long workId) {
        if (workId == null || ObjectUtils.isEmpty(keys)) return;
        final byte[] member = String.valueOf(workId).getBytes(StandardCharsets.UTF_8);
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            RedisCommands commands = connection.commands();
            for (String key : keys) commands.sRem(key.getBytes(StandardCharsets.UTF_8), member);
            return null;
        });
    }

    public Long randomHotWorkId(){
        return hotRankCache.randomWorkId();
    }

    public Long randomWorkId(Boolean sex) {
        if (sex == null) return null;
        final Set<Long> candidates = sampleWorkIdsForLabels(
                Collections.singleton(sex ? "美女" : "宠物"), 1);
        if (candidates.isEmpty()) return null;
        return candidates.stream().skip(new Random().nextInt(candidates.size())).findFirst().orElse(null);
    }

    /**
     * CDF 精确加权抽样，替换原来靠“数组占格子数”的近似做法。
     * 步骤：权重幂次平滑 -> 归一化成累积分布 -> 每次抽样二分命中区间。
     */
    private List<String> sampleLabelsByCdf(Map<Object, Object> modelMap, int count) {
        final int size = modelMap.size();
        final List<String> labels = new ArrayList<>(size);
        final double[] cdf = new double[size];
        double total = 0.0D;
        // 1. 平滑并求总和
        for (Map.Entry<Object, Object> entry : modelMap.entrySet()) {
            final double raw = ((Number) entry.getValue()).doubleValue();
            final double smoothed = Math.pow(Math.max(raw, 0.0D), INTEREST_SMOOTH_ALPHA);
            labels.add(String.valueOf(entry.getKey()));
            cdf[labels.size() - 1] = smoothed; // 先存平滑权重，下面原地转累积
            total += smoothed;
        }
        if (labels.isEmpty() || total <= 0.0D) {
            return Collections.emptyList();
        }
        // 2. 归一化并转累积分布
        double acc = 0.0D;
        for (int i = 0; i < size; i++) {
            acc += cdf[i] / total;
            cdf[i] = acc;
        }
        cdf[size - 1] = 1.0D; // 消除浮点累加误差，末位强制闭合到 1
        // 3. 抽样 count 次，二分找第一个 >= r 的区间
        final Random random = new Random();
        final List<String> sampled = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final double r = random.nextDouble();
            final int idx = upperBound(cdf, r);
            sampled.add(labels.get(Math.min(idx, size - 1)));
        }
        return sampled;
    }

    // 返回第一个 arr[i] >= target 的下标（CDF 命中）
    private static int upperBound(double[] arr, double target) {
        int lo = 0;
        int hi = arr.length - 1;
        while (lo < hi) {
            final int mid = (lo + hi) >>> 1;
            if (arr[mid] < target) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }


}
