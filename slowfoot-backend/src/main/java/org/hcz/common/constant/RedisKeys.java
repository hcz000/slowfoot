package org.hcz.common.constant;


public final class RedisKeys {

    private static final String CACHE_NAMESPACE = "v2:";

    private RedisKeys() {
        // 工具类禁止实例化
    }

    public static final String USER_MODEL = CACHE_NAMESPACE + "user:model:";
    public static final String USER_MODEL_EVENT = CACHE_NAMESPACE + "user:model:event:";

    // 用户观看评分幂等键：user:watch:score:{userId}:{workId}:{sessionId}
    public static final String USER_WATCH_SCORE = CACHE_NAMESPACE + "user:watch:score:";

    // 用于兴趣推送时去重   和下面的浏览记录存储数据结构不同  这里只需要存id
    public static final String HISTORY_WORK = CACHE_NAMESPACE + "history:work:";

    // 用于用户浏览记录  这里需要存work
    public static final String USER_HISTORY_WORK = CACHE_NAMESPACE + "user:history:work:";

    // 系统视频库,每个公开的都会存在这
    public static final String SYSTEM_STOCK = CACHE_NAMESPACE + "system:stock:";
    public static final String SYSTEM_STOCK_DAILY = SYSTEM_STOCK + "daily:";

    // 注册逻辑
    public static final String EMAIL_CODE = CACHE_NAMESPACE + "email:code:";

    // 热门排行榜
    // member 是 WorkRankItem 的 JSON（热度 / 作品 ID / 标题），不是裸的作品 ID ——
    // 写入方 HotRankScheduler、WorkHotEventListener 和读取方 hotRank()、randomHotWorkId() 都是按 JSON 处理的。
    public static final String HOT_RANK = CACHE_NAMESPACE + "hot:rank";

    // 热榜刷新版本；定时全量刷新使用它避免覆盖并发到达的互动事件。
    public static final String HOT_RANK_REVISION = CACHE_NAMESPACE + "hot:rank:revision";

    // 热门作品
    public static final String HOT_WORK = CACHE_NAMESPACE + "hot:work:";

    public static final long HISTORY_TIME = 432000L;

    public static final long EMAIL_CODE_TIME = 300L;

    public static final long USER_SEARCH_HISTORY_TIME = 432000L;

    // 系统分类库，用于查询分类下的视频随机获取
    public static final String SYSTEM_TYPE_STOCK = CACHE_NAMESPACE + "system:type:stock:";
    public static final String SYSTEM_TYPE_STOCK_DAILY = SYSTEM_TYPE_STOCK + "daily:";

    // 发件箱
    public static final String OUT_FOLLOW = CACHE_NAMESPACE + "out:follow:feed:";

    // 收件箱
    public static final String IN_FOLLOW = CACHE_NAMESPACE + "in:follow:feed:";

    // 用户搜索记录
    public static final String USER_SEARCH_HISTORY = CACHE_NAMESPACE + "user:search:history:";

    // 用户关注人
    public static final String USER_FOLLOW = CACHE_NAMESPACE + "user:follow:";

    // 用户粉丝
    public static final String USER_FANS = CACHE_NAMESPACE + "user:fans:";

    // 发布作品限流
    public static final String WORK_LIMIT = CACHE_NAMESPACE + "work:limit";

    // 登录失败计数：login:fail:{email}
    public static final String LOGIN_FAIL = CACHE_NAMESPACE + "login:fail:";

    // 定时任务分布式锁：lock:scheduler:{task}，避免多实例重复执行重建任务
    public static final String SCHEDULER_LOCK = CACHE_NAMESPACE + "lock:scheduler:";

    // 权限快照全局版本号；权限变更时递增，各实例据此失效本地 AuthStore 缓存
    public static final String AUTH_PERMISSION_VERSION = CACHE_NAMESPACE + "auth:permission:version";

    // 登录账号锁定：login:lock:{email}
    public static final String LOGIN_LOCK = CACHE_NAMESPACE + "login:lock:";

    // 登录失败最大次数
    public static final int LOGIN_MAX_FAILURES = 5;

    // 账号锁定时长（秒），15分钟
    public static final long LOGIN_LOCK_DURATION = 900L;
}
