package org.hcz.service.recommendation.impl;

import org.hcz.constant.RedisKeys;
import org.hcz.entity.work.Work;
import org.hcz.entity.user.User;
import org.hcz.entity.vo.Model;
import org.hcz.entity.vo.UserModel;
import org.hcz.service.recommendation.InterestPushService;
import org.hcz.service.work.category.WorkCategoryService;
import org.hcz.service.work.interaction.HotRankCache;
import org.hcz.util.RedisCacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisCommands;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;



// 暂时为异步
@Service
public class InterestPushServiceImpl implements InterestPushService {

    private static final byte[] UPDATE_USER_MODEL_SCRIPT = (""
            + "local key = KEYS[1] "
            + "for i = 1, #ARGV, 2 do "
            + "  local label = ARGV[i] "
            + "  local delta = tonumber(ARGV[i + 1]) "
            + "  local value = redis.call('HINCRBYFLOAT', key, label, delta) "
            + "  if tonumber(value) <= 0 then redis.call('HDEL', key, label) end "
            + "end "
            + "local fields = redis.call('HKEYS', key) "
            + "local size = #fields "
            + "if size > 0 then "
            + "  for _, field in ipairs(fields) do "
            + "    local value = tonumber(redis.call('HGET', key, field)) "
            + "    redis.call('HSET', key, field, (value + size) / size) "
            + "  end "
            + "end "
            + "return size").getBytes(StandardCharsets.UTF_8);

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
        // 往系统库中添加
        final List<String> labels = work.buildLabel();
        final Long videoId = work.getId();
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            RedisCommands commands = connection.commands();
            for (String label : labels) {
                commands.sAdd((RedisKeys.SYSTEM_STOCK + label).getBytes(StandardCharsets.UTF_8),String.valueOf(videoId).getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
    }

    @Override
    @Async
    public void pushSystemTypeStockIn(Work work) {
        final Long typeId = work.getTypeId();
        redisCacheUtil.sSet(RedisKeys.SYSTEM_TYPE_STOCK + typeId,work.getId());
    }

    @Override
    public Collection<Long> listWorkIdByTypeId(Long typeId) {
        // 随机推送10个

        final List<Object> list = redisTemplate.opsForSet().randomMembers(RedisKeys.SYSTEM_TYPE_STOCK + typeId, 12);
        // 可能会有null
        final LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (Object aLong : list) {
            if (aLong!=null){
                result.add(Long.parseLong(aLong.toString()));
            }
        }
        return result;
    }

    @Override
    @Async
    public void deleteSystemStockIn(Work work) {
        final List<String> labels = work.buildLabel();
        final Long videoId = work.getId();
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            RedisCommands commands = connection.commands();
            for (String label : labels) {
                commands.sRem((RedisKeys.SYSTEM_STOCK + label).getBytes(StandardCharsets.UTF_8),String.valueOf(videoId).getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
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
        redisTemplate.execute((RedisCallback<Long>) connection ->
                connection.scriptingCommands().eval(
                        UPDATE_USER_MODEL_SCRIPT, ReturnType.INTEGER, 1, concat(key, args)));
    }

    private static byte[][] concat(byte[] key, byte[][] args) {
        byte[][] values = new byte[args.length + 1][];
        values[0] = key;
        System.arraycopy(args, 0, values, 1, args.length);
        return values;
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
                // 组成数组
                final String[] probabilityArray = initProbabilityArray(modelMap);
                final Boolean sex = user.getSex();
                // 获取视频
                final Random randomObject = new Random();
                final ArrayList<String> labelNames = new ArrayList<>();
                // 随机获取X个视频
                for (int i = 0; i < 20; i++) {
                    String labelName = probabilityArray[randomObject.nextInt(probabilityArray.length)];
                    labelNames.add(labelName);
                }
                // 提升性能
                String t = RedisKeys.SYSTEM_STOCK;
                // 随机获取
                List<Object> list = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    RedisCommands commands = connection.commands();
                    for (String labelName : labelNames) {
                        String key = t + labelName;
                        commands.sRandMember(key.getBytes(StandardCharsets.UTF_8));
                    }
                    return null;
                });
                // 获取到的videoIds
                Set<Long> ids = list.stream().filter(id -> id != null)
                        .map(id -> Long.parseLong(id.toString()))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
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
        final ArrayList<String> labelNames = new ArrayList<>();
        int size = labels.size();
        final Random random = new Random();
        // 获取随机的标签
        for (int i = 0; i < 20; i++) {
            final int randomIndex = random.nextInt(size);
            labelNames.add(RedisKeys.SYSTEM_STOCK + labels.get(randomIndex));
        }
        // 获取videoId
        final List<Object> list = redisCacheUtil.sRandom(labelNames);
        if (!ObjectUtils.isEmpty(list)){
            videoIds = list.stream().filter(id -> !ObjectUtils.isEmpty(id))
                    .map(id -> Long.valueOf(id.toString()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        return videoIds;
    }

    @Override
    public Collection<Long> listWorkIdByLabels(List<String> labelNames) {
        final ArrayList<String> labelKeys = new ArrayList<>();
        for (String labelName : labelNames) {
            labelKeys.add(RedisKeys.SYSTEM_STOCK + labelName);
        }
        Set<Long> videoIds = new HashSet<>();
        final List<Object> list = redisCacheUtil.sRandom(labelKeys);
        if (!ObjectUtils.isEmpty(list)){
            videoIds = list.stream().filter(id ->!ObjectUtils.isEmpty(id)).map(id -> Long.valueOf(id.toString())).collect(Collectors.toSet());
        }
        return videoIds;
    }

    @Override
    @Async
    public void deleteSystemTypeStockIn(Work work) {
        final Long typeId = work.getTypeId();
        redisCacheUtil.setRemove(RedisKeys.SYSTEM_TYPE_STOCK + typeId,work.getId());
    }


    public Long randomHotWorkId(){
        return hotRankCache.randomWorkId();
    }

    public Long randomWorkId(Boolean sex) {
        String key = RedisKeys.SYSTEM_STOCK + (sex ? "美女" : "宠物");
        final Object o = redisCacheUtil.sRandom(key);
        if (o!=null){
            return Long.parseLong(o.toString());
        }
        return null;
    }

    // 随机获取视频id
    public Long getWorkId(Random random, String[] probabilityArray) {
        String labelName = probabilityArray[random.nextInt(probabilityArray.length)];
        // 获取对应所有视频
        String key = RedisKeys.SYSTEM_STOCK + labelName;
        final Object o = redisCacheUtil.sRandom(key);
        if (o!=null){
            return Long.parseLong(o.toString()) ;
        }
        return null;
    }

    // 初始化概率数组 -> 保存的元素是标签
    public String[] initProbabilityArray(Map<Object, Object> modelMap) {
        // key: 标签  value：概率
        Map<String, Integer> probabilityMap = new HashMap<>();
        int size = modelMap.size();
        final AtomicInteger n = new AtomicInteger(0);
        modelMap.forEach((k, v) -> {
            // 防止结果为0,每个同等加上标签数
            int probability = (((Double) v).intValue() + size) / size;
            n.getAndAdd(probability);
            probabilityMap.put(k.toString(), probability);
        });
        final String[] probabilityArray = new String[n.get()];

        final AtomicInteger index = new AtomicInteger(0);
        // 初始化数组
        probabilityMap.forEach((labelsId, p) -> {
            int i = index.get();
            int limit = i + p;
            while (i < limit) {
                probabilityArray[i++] = labelsId;
            }
            index.set(limit);
        });
        return probabilityArray;
    }


}
