
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `lucky-journey`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `lucky-journey`;

-- 建表顺序已按依赖排列（work 先于三张子表），这里再关一次外键检查保证顺序无关。
SET FOREIGN_KEY_CHECKS = 0;


-- =============================================================================
-- 一、用户与权限
-- =============================================================================

-- -----------------------------------------------------------------------------
-- user：用户表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT,
    `email`                VARCHAR(50)  NOT NULL                COMMENT '登录邮箱，注册时唯一',
    `nick_name`            VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '昵称，展示用',
    `password`             VARCHAR(255) NULL                    COMMENT 'BCrypt 哈希（$2a$ 前缀），绝不存明文',
    `description`          VARCHAR(255) NULL DEFAULT ''         COMMENT '个人简介',
    `sex`                  TINYINT(1)   NULL DEFAULT 1          COMMENT '性别，实体是 Boolean：1 男 / 0 女',
    `avatar`               BIGINT       NULL                    COMMENT '头像，存 file.id（旧导出是 varchar(255)，已按实体改为 bigint）',
    `default_favorites_id` BIGINT       NULL                    COMMENT '默认收藏夹 ID，注册时创建',
    `is_deleted`           TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常，1 删除',
    `gmt_created`          DATETIME     NULL                    COMMENT '创建时间，自动填充',
    `gmt_updated`          DATETIME     NULL                    COMMENT '更新时间，自动填充',
    PRIMARY KEY (`id`),
    KEY `idx_user_email` (`email`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户';


-- -----------------------------------------------------------------------------
-- role：角色表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `role` (
    `id`     BIGINT       NOT NULL AUTO_INCREMENT,
    `name`   VARCHAR(255) NULL COMMENT '角色名，如「超级管理员」「审核员」',
    `remark` VARCHAR(255) NULL COMMENT '备注',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色';


-- -----------------------------------------------------------------------------
-- permission：权限 / 后台菜单表
--   同一张表既表达「菜单树」又表达「接口权限」：
--     is_menu = -1  目录节点（只用于菜单树分组，不对应接口）
--     is_menu =  0  菜单项（path 指向 Layui 页面）
--     is_menu =  1  按钮/接口级权限，其 name 字段即 @Authority 里的权限串
--   ⚠️ BaseAuthority 会逐个校验 @Authority 声明的权限串，没有对应行就会 403。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `permission` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `p_id`       BIGINT       NULL DEFAULT 0   COMMENT '父权限 ID，0 为顶级',
    `path`       VARCHAR(255) NULL             COMMENT '菜单页面路径（is_menu <= 0 时有意义）',
    `href`       VARCHAR(255) NULL             COMMENT '前端路由 / 链接',
    `icon`       VARCHAR(255) NULL             COMMENT '菜单图标 class',
    `name`       VARCHAR(255) NULL             COMMENT '名称；is_menu=1 时该字段就是权限串本身',
    `is_menu`    TINYINT(2)   NULL             COMMENT '-1 目录，0 菜单，1 按钮/接口权限',
    `target`     VARCHAR(255) NULL             COMMENT '链接打开方式',
    `sort`       INT          NULL DEFAULT 1   COMMENT '同层排序',
    `state`      TINYINT      NULL DEFAULT 0   COMMENT '状态：0 启用，1 停用',
    `is_deleted` TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
    `gmt_created` DATETIME    NULL COMMENT '创建时间，自动填充',
    `gmt_updated` DATETIME    NULL COMMENT '更新时间，自动填充',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '权限与后台菜单';


-- -----------------------------------------------------------------------------
-- user_role：用户 - 角色关联
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_role` (
    `id`      BIGINT NOT NULL AUTO_INCREMENT,
    `role_id` BIGINT NULL COMMENT 'role.id',
    `user_id` BIGINT NULL COMMENT 'user.id',
    PRIMARY KEY (`id`),
    KEY `idx_user_role_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户角色关联';


-- -----------------------------------------------------------------------------
-- role_permission：角色 - 权限关联
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `role_permission` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT,
    `permission_id` BIGINT NULL COMMENT 'permission.id',
    `role_id`       BIGINT NULL COMMENT 'role.id',
    PRIMARY KEY (`id`),
    KEY `idx_role_permission_role` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色权限关联';


-- -----------------------------------------------------------------------------
-- user_subscribe：用户订阅的分类（用于兴趣推送）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_subscribe` (
    `id`      BIGINT NOT NULL AUTO_INCREMENT,
    `type_id` BIGINT NULL COMMENT 'type.id',
    `user_id` BIGINT NULL COMMENT 'user.id',
    PRIMARY KEY (`id`),
    KEY `idx_user_subscribe_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户订阅分类';


-- -----------------------------------------------------------------------------
-- follow：关注关系
--   不继承 BaseEntity，因此没有 is_deleted；取消关注是物理删除。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `follow` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT   NOT NULL COMMENT '关注发起方 user.id',
    `follow_id`   BIGINT   NOT NULL COMMENT '被关注方 user.id',
    `gmt_created` DATETIME NULL     COMMENT '关注时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `index_user_id_follow_id` (`user_id`, `follow_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '关注关系';


-- =============================================================================
-- 二、文件、分类与系统配置
-- =============================================================================

-- -----------------------------------------------------------------------------
-- file：上传文件登记表（对象存储里的文件元信息）
--   file_key 是 OSS 的 object key；访问地址不落库，每次由后端签发临时预签名 URL，
--   因此前端拿到的是会过期的链接。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `file` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `file_key`    VARCHAR(255) NULL COMMENT 'OSS object key，如 uploads/xxx.jpg（可能带 x-oss-process 参数）',
    `format`      VARCHAR(20)  NULL COMMENT 'MIME 类型，如 image/png、video/mp4；校验时按前缀匹配',
    `type`        VARCHAR(20)  NULL COMMENT '展示用中文类型：视频 / 图片',
    `size`        BIGINT       NULL COMMENT '字节数',
    `user_id`     BIGINT       NULL COMMENT '归属用户 user.id，鉴权时校验',
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
    `gmt_created` DATETIME     NULL COMMENT '创建时间，自动填充',
    `gmt_updated` DATETIME     NULL COMMENT '更新时间，自动填充',
    PRIMARY KEY (`id`),
    KEY `idx_file_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '上传文件';


-- -----------------------------------------------------------------------------
-- type：内容分类（作品分类）
--   不是 video.type / 也不是作品类型枚举；是用户发布时选择的「频道/分区」。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `type` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(255) NULL COMMENT '分类名',
    `description` VARCHAR(255) NULL COMMENT '描述',
    `icon`        VARCHAR(255) NULL COMMENT '图标',
    `open`        TINYINT(1)   NULL DEFAULT 0 COMMENT '是否开放：0 开放，1 关闭',
    `sort`        INT          NULL DEFAULT 0 COMMENT '排序',
    `label_names` VARCHAR(255) NULL COMMENT '逗号分隔的标签',
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
    `gmt_created` DATETIME     NULL COMMENT '创建时间，自动填充',
    `gmt_updated` DATETIME     NULL COMMENT '更新时间，自动填充',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '内容分类';


-- -----------------------------------------------------------------------------
-- captcha：图形验证码
--   主键是 uuid 而不是自增 id（实体 Captcha 上没有任何 @TableId，代码全部走
--   wrapper 按 uuid 查询，因此这里显式声明 uuid 为主键）。
--   校验通过后会立刻删除该行，属于一次性凭证。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `captcha` (
    `uuid`        CHAR(36) NOT NULL COMMENT '前端生成的 uuid',
    `code`        VARCHAR(6) NOT NULL COMMENT '验证码文本',
    `expire_time` DATETIME NULL COMMENT '过期时间，签发时 +5 分钟',
    PRIMARY KEY (`uuid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '图形验证码';


-- -----------------------------------------------------------------------------
-- sys_setting：系统配置（整表只有一行，id 固定为 1）
--   audit_open = 0 时内容审核整体短路，全部直接判定为「通过」。
--   audit_policy 是 JSON 字符串，内容见实体 SettingScoreJson。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_setting` (
    `id`           INT    NOT NULL AUTO_INCREMENT,
    `audit_policy` LONGTEXT NULL         COMMENT '审核策略 JSON（successScore / manualScore / passScore）',
    `hot_limit`    DOUBLE NOT NULL DEFAULT 1000 COMMENT '热度榜阈值',
    `audit_open`   TINYINT NULL DEFAULT 0 COMMENT '内容审核总开关：0 关，1 开',
    `allow_ip`     VARCHAR(255) NULL      COMMENT '允许上传的 IP，逗号分隔',
    `auth`         TINYINT NULL DEFAULT 1 COMMENT '回源鉴权开关：0 关，1 开',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统配置（单行）';


-- =============================================================================
-- 三、作品模型（核心）
--
-- 统一作品模型的表结构关系：
--
--   work ──1:1──> video_work   仅存视频特有字段（主文件/封面/时长）
--        ──1:1──> image_work   仅作标记，图片列表全在 work_asset
--        ──1:1──> text_work    仅存正文
--        ──1:N──> work_asset   作品引用的全部文件资源，sort_order 决定展示顺序
--
--   work_type 取值 VIDEO / IMAGE / TEXT，三种作品共存在 work 表里。
--   work.primary_file_id / cover_file_id / duration 三列是 video_work 行的冗余副本，
--   只为列表查询免去一次 join，权威值仍以 video_work 为准。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- work：统一作品主表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `work` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `is_deleted`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
    `gmt_created`      DATETIME     NOT NULL COMMENT '创建时间，自动填充',
    `gmt_updated`      DATETIME     NOT NULL COMMENT '更新时间，自动填充',
    `work_no`          VARCHAR(64)  NOT NULL COMMENT '作品业务编号，格式 W/UUID 或 YV+随机串',
    `user_id`          BIGINT       NOT NULL COMMENT '作者 user.id',
    `work_type`        VARCHAR(16)  NOT NULL COMMENT '作品类型：VIDEO / IMAGE / TEXT',
    `primary_file_id`  BIGINT       NULL COMMENT '主资源 file.id（图片取第一张，视频取主文件）',
    `title`            VARCHAR(255) NOT NULL COMMENT '标题',
    `description`      VARCHAR(1000) NULL COMMENT '简介',
    `type_id`          BIGINT       NULL COMMENT '分类 type.id',
    `label_names`      VARCHAR(255) NULL COMMENT '逗号分隔的标签',
    `cover_file_id`    BIGINT       NULL COMMENT '封面 file.id',
    `open_state`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '⚠️ 0 公开，1 私密（实体是 Boolean，命名与直觉相反）',
    `audit_status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '审核状态：0 通过，1 审核中，2 不通过，3 人工审核',
    `audit_msg`        VARCHAR(500) NULL COMMENT '审核结论说明；失败时会折叠为前 10 条 + 「另有 N 项」',
    `audit_notification_sent` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '审核结果是否已通知作者：0 否，1 是（发通知前用条件更新做幂等）',
    `duration`         VARCHAR(32)  NULL COMMENT '视频时长；图片/文字作品为空',
    `like_count`       BIGINT       NOT NULL DEFAULT 0 COMMENT '点赞数',
    `share_count`      BIGINT       NOT NULL DEFAULT 0 COMMENT '分享数',
    `view_count`       BIGINT       NOT NULL DEFAULT 0 COMMENT '浏览数',
    `favorite_count`   BIGINT       NOT NULL DEFAULT 0 COMMENT '收藏数',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_work_work_no` (`work_no`),
    KEY `idx_work_user_created` (`user_id`, `gmt_created`),
    -- 管理端审核队列的索引：按类型 + 状态筛「待人工审核」。注意它以 work_type 打头，
    -- 因此只传 audit_status 而不同时传 work_type 的话用不上这个索引。
    KEY `idx_work_type_status` (`work_type`, `audit_status`, `open_state`, `gmt_created`),
    KEY `idx_work_category` (`type_id`, `audit_status`, `open_state`, `gmt_created`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '统一作品主表';


-- -----------------------------------------------------------------------------
-- work_asset：作品资源关联表
--   图片作品的图片列表、视频作品的主文件与封面都在这里。
--   asset_type 取值 VIDEO / IMAGE / COVER。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `work_asset` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `is_deleted`  TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
    `gmt_created` DATETIME    NOT NULL COMMENT '创建时间，自动填充',
    `gmt_updated` DATETIME    NOT NULL COMMENT '更新时间，自动填充',
    `work_id`     BIGINT      NOT NULL COMMENT 'work.id',
    `file_id`     BIGINT      NOT NULL COMMENT 'file.id',
    `asset_type`  VARCHAR(16) NOT NULL COMMENT '资源角色：VIDEO / IMAGE / COVER',
    `sort_order`  INT         NOT NULL DEFAULT 0 COMMENT '展示顺序，从 0 开始',
    PRIMARY KEY (`id`),
    -- 「同一文件在同一作品中只出现一次」——发布时会先做重复校验，避免撞这个唯一键
    UNIQUE KEY `uk_work_asset_resource` (`work_id`, `file_id`, `asset_type`),
    -- 「同一角色下顺序不重复」
    UNIQUE KEY `uk_work_asset_order` (`work_id`, `asset_type`, `sort_order`),
    KEY `idx_work_asset_work_order` (`work_id`, `sort_order`, `id`),
    KEY `idx_work_asset_file` (`file_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作品资源关联';


-- -----------------------------------------------------------------------------
-- video_work：视频作品特有字段
--   id 不是自增，与 work.id 同值（IdType.INPUT）。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `video_work` (
    `id`              BIGINT      NOT NULL COMMENT '与 work.id 同值，非自增',
    `primary_file_id` BIGINT      NULL COMMENT '主视频文件 file.id',
    `cover_file_id`   BIGINT      NULL COMMENT '封面 file.id',
    `duration`        VARCHAR(32) NULL COMMENT '视频时长',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_video_work_work` FOREIGN KEY (`id`) REFERENCES `work` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '视频作品扩展';


-- -----------------------------------------------------------------------------
-- image_work：图片作品标记表
--   除了「这是一张图片作品」之外没有别的字段，图片列表全在 work_asset。
--   id 不是自增，与 work.id 同值。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `image_work` (
    `id` BIGINT NOT NULL COMMENT '与 work.id 同值，非自增',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_image_work_work` FOREIGN KEY (`id`) REFERENCES `work` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '图片作品标记';


-- -----------------------------------------------------------------------------
-- text_work：文字作品正文
--   ⚠️ 正文只存在这里：work 表没有 content 列（实体 Work 的 content 字段标了
--   @TableField(exist = false)，只在详情查询时从本表回填），审核时要单独查这张表。
--   id 不是自增，与 work.id 同值。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `text_work` (
    `id`      BIGINT     NOT NULL COMMENT '与 work.id 同值，非自增',
    `content` MEDIUMTEXT NOT NULL COMMENT '正文',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_text_work_work` FOREIGN KEY (`id`) REFERENCES `work` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文字作品正文';


-- =============================================================================
-- 四、作品互动
--   点赞 / 分享 / 分类关联 / 收藏夹内容四张表（work_like / work_share /
--   work_category_relation / favorites_work）统一用 work_id 指向 work.id。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- work_comment：作品评论（含回复）
--   用 root_id 把整条讨论串归到根评论下，一次查询就能取出某个根下的全部回复。
--   parent_id = 0 表示顶层评论。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `work_comment` (
    `id`        BIGINT        NOT NULL AUTO_INCREMENT,
    `is_deleted` TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
    `gmt_created` DATETIME    NOT NULL COMMENT '创建时间，自动填充',
    `gmt_updated` DATETIME    NOT NULL COMMENT '更新时间，自动填充',
    `work_id`   BIGINT        NOT NULL COMMENT '作品 work.id',
    `user_id`   BIGINT        NOT NULL COMMENT '评论者 user.id',
    `parent_id` BIGINT        NOT NULL DEFAULT 0 COMMENT '直接父评论；0 表示顶层评论',
    `root_id`   BIGINT        NOT NULL DEFAULT 0 COMMENT '所属讨论串的根评论 ID；顶层评论指向自己',
    `reply_to_user_id` BIGINT NULL COMMENT '被回复者 user.id',
    `content`   VARCHAR(1000) NOT NULL COMMENT '评论内容',
    `audit_status` TINYINT    NOT NULL DEFAULT 1 COMMENT '审核状态：0 通过，1 审核中，2 不通过，3 人工审核',
    `audit_msg` VARCHAR(255)  NULL COMMENT '审核结论说明',
    `notification_sent` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '互动通知是否已发：0 否，1 是（防审核重试后重复通知）',
    `audit_notification_sent` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '审核结果通知是否已发：0 否，1 是',
    PRIMARY KEY (`id`),
    KEY `idx_work_comment_work_status` (`work_id`, `audit_status`, `parent_id`, `gmt_created`),
    KEY `idx_work_comment_root_status` (`root_id`, `audit_status`, `gmt_created`),
    KEY `idx_work_comment_user` (`user_id`, `gmt_created`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作品评论';


-- -----------------------------------------------------------------------------
-- user_notification：站内通知（按 user_id 分为 4 张物理表）
--   (user_id, event_id) 唯一，是通知自身的幂等键：persist 时靠 INSERT 撞唯一键来去重，
--   所以同一条业务事件重复投递不会产生两条通知。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_notification_0` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
    `gmt_created` DATETIME     NOT NULL COMMENT '创建时间，自动填充',
    `gmt_updated` DATETIME     NOT NULL COMMENT '更新时间，自动填充',
    `event_id`    VARCHAR(36)  NOT NULL COMMENT '幂等键，唯一',
    `user_id`     BIGINT       NOT NULL COMMENT '接收者 user.id',
    `actor_id`    BIGINT       NULL COMMENT '触发者 user.id；系统通知为 NULL',
    `type`        VARCHAR(32)  NOT NULL COMMENT 'LIKE/FAVORITE/FOLLOW/COMMENT/REPLY/COMMENT_AUDIT/WORK_AUDIT',
    `target_id`   BIGINT       NULL COMMENT '目标对象 ID（评论 ID、作品 ID 等）',
    `work_id`     BIGINT       NULL COMMENT '关联作品 work.id',
    `content`     VARCHAR(500) NOT NULL COMMENT '通知文案',
    `read_state`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '已读：0 未读，1 已读',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_notification_event` (`user_id`, `event_id`),
    KEY `idx_user_notification_user_read` (`user_id`, `read_state`, `gmt_created`),
    KEY `idx_user_notification_target` (`target_id`, `type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内通知';
CREATE TABLE IF NOT EXISTS `user_notification_1` LIKE `user_notification_0`;
CREATE TABLE IF NOT EXISTS `user_notification_2` LIKE `user_notification_0`;
CREATE TABLE IF NOT EXISTS `user_notification_3` LIKE `user_notification_0`;


-- -----------------------------------------------------------------------------
-- work_like：作品点赞（按 work_id 分为 4 张物理表）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `work_like_0` (
    `id`          BIGINT     NOT NULL AUTO_INCREMENT,
    `work_id`     BIGINT     NOT NULL COMMENT '作品 work.id',
    `user_id`     BIGINT     NULL COMMENT '点赞者 user.id',
    `is_deleted`  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
    `gmt_created` DATETIME   NULL COMMENT '创建时间，自动填充',
    `gmt_updated` DATETIME   NULL COMMENT '更新时间，自动填充',
    PRIMARY KEY (`id`),
    UNIQUE KEY `index_work_id_user_id` (`work_id`, `user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作品点赞';
CREATE TABLE IF NOT EXISTS `work_like_1` LIKE `work_like_0`;
CREATE TABLE IF NOT EXISTS `work_like_2` LIKE `work_like_0`;
CREATE TABLE IF NOT EXISTS `work_like_3` LIKE `work_like_0`;


-- -----------------------------------------------------------------------------
-- work_share：作品分享记录（按 work_id 分为 4 张物理表）
--   唯一键是 (work_id, ip)：同一 IP 对同一作品只计一次分享。
--   ⚠️ 旧导出漏了 is_deleted，而对应实体继承 BaseEntity，
--      MyBatis-Plus 会自动追加 is_deleted = 0，缺列会直接报错。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `work_share_0` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `work_id`     BIGINT       NULL COMMENT '作品 work.id',
    `ip`          VARCHAR(255) NULL COMMENT '分享者 IP',
    `user_id`     BIGINT       NULL COMMENT '分享者 user.id',
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
    `gmt_created` DATETIME     NULL COMMENT '创建时间，自动填充',
    `gmt_updated` DATETIME     NULL COMMENT '更新时间，自动填充',
    PRIMARY KEY (`id`),
    UNIQUE KEY `index_work_id_ip` (`work_id`, `ip`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作品分享记录';
CREATE TABLE IF NOT EXISTS `work_share_1` LIKE `work_share_0`;
CREATE TABLE IF NOT EXISTS `work_share_2` LIKE `work_share_0`;
CREATE TABLE IF NOT EXISTS `work_share_3` LIKE `work_share_0`;


-- -----------------------------------------------------------------------------
-- work_category_relation：作品 - 分类关联
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `work_category_relation` (
    `id`          BIGINT     NOT NULL AUTO_INCREMENT,
    `work_id`     BIGINT     NOT NULL COMMENT '作品 work.id',
    `type_id`     BIGINT     NOT NULL COMMENT '分类 type.id',
    `is_deleted`  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
    `gmt_created` DATETIME   NULL COMMENT '创建时间，自动填充',
    `gmt_updated` DATETIME   NULL COMMENT '更新时间，自动填充',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作品分类关联';


-- -----------------------------------------------------------------------------
-- favorites：收藏夹
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `favorites` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(255) NULL COMMENT '收藏夹名称',
    `description` VARCHAR(255) NULL COMMENT '描述',
    `user_id`     BIGINT       NULL COMMENT '归属用户 user.id',
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
    `gmt_created` DATETIME     NULL COMMENT '创建时间，自动填充',
    `gmt_updated` DATETIME     NULL COMMENT '更新时间，自动填充',
    PRIMARY KEY (`id`),
    KEY `idx_favorites_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收藏夹';


-- -----------------------------------------------------------------------------
-- favorites_work：收藏夹内容（按 user_id 分为 4 张物理表）
--   不继承 BaseEntity，没有 is_deleted；取消收藏是物理删除。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `favorites_work_0` (
    `id`           BIGINT NOT NULL AUTO_INCREMENT,
    `favorites_id` BIGINT NOT NULL COMMENT 'favorites.id',
    `work_id`      BIGINT NULL     COMMENT '作品 work.id',
    `user_id`      BIGINT NOT NULL COMMENT '收藏者 user.id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `index_f_id_w_id_u_id` (`favorites_id`, `work_id`, `user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收藏夹内容';
CREATE TABLE IF NOT EXISTS `favorites_work_1` LIKE `favorites_work_0`;
CREATE TABLE IF NOT EXISTS `favorites_work_2` LIKE `favorites_work_0`;
CREATE TABLE IF NOT EXISTS `favorites_work_3` LIKE `favorites_work_0`;


-- =============================================================================
-- 五、审核发件箱（outbox）
--
--   两条审核链路（作品 / 评论）结构完全一致：
--     业务事务内写一行 status=0 的任务（与业务行同事务，保证不丢）
--     -> @Scheduled 每秒轮询，claim() 用条件更新抢占（status=1，retry_count+1，
--        locked_until=now+10min），抢到才发事件
--     -> @Async 监听器调阿里云内容安全，回写业务行（条件更新 audit_status=1），
--        成功 markSuccess，失败 markFailure 排下次重试
--   重试 5 次仍失败：任务置 FAILED，业务行从「审核中」推进到「人工审核」。
--
--   event_consume 是消费端幂等表：主键 (event_id, consumer)，
--   用 INSERT IGNORE 的返回值判断该事件是否已被该消费者处理过。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- work_audit_task：作品审核任务
--   存提交快照（is_add / old_state / new_state / old_title / old_description）：
--   作品支持编辑，审核方需要知道哪些字段变了，且改前的值无法从业务行恢复。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `work_audit_task` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT,
    `is_deleted`   TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
    `gmt_created`  DATETIME    NOT NULL COMMENT '创建时间，自动填充',
    `gmt_updated`  DATETIME    NOT NULL COMMENT '更新时间，自动填充',
    `event_id`     VARCHAR(36) NOT NULL COMMENT '事件 ID，幂等键，唯一',
    `work_id`      BIGINT      NOT NULL COMMENT '作品 work.id',
    `is_add`       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否新增作品（0 修改，1 新增）',
    `old_state`    TINYINT(1)   NULL               COMMENT '修改前的开放状态快照',
    `new_state`    TINYINT(1)   NULL               COMMENT '修改后的开放状态快照',
    `old_title`    VARCHAR(255) NULL               COMMENT '修改前的标题快照',
    `old_description` VARCHAR(1000) NULL           COMMENT '修改前的简介快照',
    `status`       TINYINT     NOT NULL DEFAULT 0 COMMENT '任务状态：0 待处理，1 处理中，2 成功，3 失败',
    `retry_count`  INT         NOT NULL DEFAULT 0 COMMENT '已重试次数；在 claim 时自增（不是失败时）',
    `next_retry_at` DATETIME   NOT NULL COMMENT '下次可执行时间，退避 5/10/20/40/80 秒',
    `locked_until` DATETIME    NULL COMMENT '处理中锁的到期时间，10 分钟后可被重新抢占',
    `last_error`   VARCHAR(500) NULL COMMENT '最近一次失败原因，超长截断到 500',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_work_audit_task_event` (`event_id`),
    KEY `idx_work_audit_task_dispatch` (`status`, `next_retry_at`, `locked_until`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作品审核发件箱';


-- -----------------------------------------------------------------------------
-- comment_audit_task：评论审核任务
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `comment_audit_task` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `is_deleted`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
    `gmt_created`   DATETIME     NOT NULL COMMENT '创建时间，自动填充',
    `gmt_updated`   DATETIME     NOT NULL COMMENT '更新时间，自动填充',
    `event_id`      VARCHAR(36)  NOT NULL COMMENT '事件 ID，幂等键，唯一',
    `comment_id`    BIGINT       NOT NULL COMMENT 'work_comment.id',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '任务状态：0 待处理，1 处理中，2 成功，3 失败',
    `retry_count`   INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `next_retry_at` DATETIME     NOT NULL COMMENT '下次可执行时间',
    `locked_until`  DATETIME     NULL COMMENT '处理中锁的到期时间',
    `last_error`    VARCHAR(500) NULL COMMENT '最近一次失败原因',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_comment_audit_task_event` (`event_id`),
    KEY `idx_comment_audit_task_dispatch` (`status`, `next_retry_at`, `locked_until`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '评论审核发件箱';


-- -----------------------------------------------------------------------------
-- event_consume：消费端幂等表
--   复合主键 (event_id, consumer)；INSERT IGNORE 返回 0 行表示该事件已被
--   该消费者处理过，直接跳过。consumer 取值：comment-audit / work-audit。
--   没有自增 id、没有逻辑删除，属于纯技术表。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `event_consume` (
    `event_id`   VARCHAR(36)  NOT NULL COMMENT '事件 ID',
    `consumer`   VARCHAR(100) NOT NULL COMMENT '消费者标识',
    `created_at` DATETIME     NOT NULL COMMENT '首次消费时间',
    PRIMARY KEY (`event_id`, `consumer`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '事件消费幂等表';


-- work_view_history：浏览历史（按 user_id 分为 4 张物理表）
CREATE TABLE IF NOT EXISTS `work_view_history_0` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT   NOT NULL COMMENT '用户 ID',
    `work_id`    BIGINT   NOT NULL COMMENT '作品 ID',
    `viewed_at`  DATETIME NOT NULL COMMENT '最近一次浏览时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_work_view_history_user_work` (`user_id`, `work_id`),
    KEY `idx_work_view_history_user_time` (`user_id`, `viewed_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作品浏览历史';
CREATE TABLE IF NOT EXISTS `work_view_history_1` LIKE `work_view_history_0`;
CREATE TABLE IF NOT EXISTS `work_view_history_2` LIKE `work_view_history_0`;
CREATE TABLE IF NOT EXISTS `work_view_history_3` LIKE `work_view_history_0`;


-- user_search_history：搜索历史（按 user_id 分为 4 张物理表）
CREATE TABLE IF NOT EXISTS `user_search_history_0` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL COMMENT '用户 ID',
    `keyword`     VARCHAR(255) NOT NULL COMMENT '搜索关键词',
    `searched_at` DATETIME     NOT NULL COMMENT '最近一次搜索时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_search_history_user_keyword` (`user_id`, `keyword`),
    KEY `idx_user_search_history_user_time` (`user_id`, `searched_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户搜索历史';
CREATE TABLE IF NOT EXISTS `user_search_history_1` LIKE `user_search_history_0`;
CREATE TABLE IF NOT EXISTS `user_search_history_2` LIKE `user_search_history_0`;
CREATE TABLE IF NOT EXISTS `user_search_history_3` LIKE `user_search_history_0`;


SET FOREIGN_KEY_CHECKS = 1;


-- =============================================================================
-- 六、最小初始化数据
--
--   ⚠️⚠️ 下面的管理员账号是公开的默认口令，任何对外可访问的部署都必须立刻改掉。
--        改密码：把 password 换成一个新的 BCrypt 哈希（$2a$ / $2b$ 均可），
--        生成方式见文末「如何生成 BCrypt 哈希」。
--
--   不想要默认账号的话，删掉「1) 管理员账号」这一段，
--   然后自行注册用户并把 user.id 填进 user_role 即可。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) 管理员账号
--    邮箱 admin@xhyovo.cn / 口令 123456（BCrypt 哈希，对应明文见下方注释）
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `user` (`id`, `email`, `nick_name`, `password`, `description`, `sex`, `avatar`, `default_favorites_id`) VALUES
    (1, 'admin@xhyovo.cn', 'admin', '$2a$10$CT4UDPdt.cwcManfivJ.kOyzaExbNFXscg6T6NVaAfdHdM9kqUqNW', '默认管理员，请尽快修改密码', 1, NULL, NULL);
-- 上面的哈希对应的明文是 123456

INSERT IGNORE INTO `favorites` (`id`, `name`, `description`, `user_id`) VALUES
    (1, '默认收藏夹', '默认管理员账号的收藏夹', 1);

UPDATE `user` SET `default_favorites_id` = 1 WHERE `id` = 1 AND `default_favorites_id` IS NULL;

-- -----------------------------------------------------------------------------
-- 2) 角色
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `role` (`id`, `name`, `remark`) VALUES
    (1,  '超级管理员', '拥有全部权限'),
    (16, '审核员',     '只负责内容审核');

INSERT IGNORE INTO `user_role` (`id`, `role_id`, `user_id`) VALUES
    (1, 1, 1);


-- -----------------------------------------------------------------------------
-- 3) 权限 / 后台菜单
--    目录节点：1 业务管理、3 用户管理、32 系统设置、73 权限管理
--    ⚠️ name 字段就是 @Authority 的权限串，代码里有的权限这里必须都有，否则接口一律 403。
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `permission` (`id`, `p_id`, `path`, `href`, `icon`, `name`, `is_menu`, `target`, `sort`, `state`) VALUES
    -- 目录
    (1,   0,   NULL,                      NULL, 'fa fa-align-justify', '业务管理',  -1, '_self', 5, 0),
    (3,   1,   '',                        '',   'fa fa-user',          '用户管理',  -1, '_self', 5, 0),
    (32,  0,   '',                        '',   'fa fa-gears',         '系统设置',  -1, '_self', 5, 0),
    (73,  32,  '',                        '',   'fa fa-shield',        '权限管理',  -1, '_self', 5, 0),

    -- 用户管理
    (4,   3,   '',                        'page/user-table.html', 'fa fa-user-o',   '用户',      0,  '_self', 5, 0),
    (92,  4,   '',                        '',                     'fa fa-gear',     'user:update',    1, '_self', 5, 0),
    (93,  4,   '',                        '',                     'fa fa-remove',   'user:delete',    1, '_self', 5, 0),
    (109, 4,   '',                        '',                     'fa fa-500px',    'admin:user:list', 1, NULL, 5, 0),
    (110, 4,   '',                        '',                     'fa fa-500px',    'admin:user:page', 1, NULL, 5, 0),

    -- 分类
    (115, 1,   'page/video/type.html',    '',                     'fa fa-align-center', '分类',               -1, NULL, 1, 0),
    (116, 115, '',                        '',                     'fa ',                'admin:type:get',     1, NULL, 1, 0),
    (118, 115, '',                        '',                     'fa ',                'admin:type:add',     1, NULL, 1, 0),
    (119, 115, '',                        '',                     'fa ',                'admin:type:update',  1, NULL, 1, 0),
    (120, 115, '',                        '',                     'fa ',                'admin:type:delete',  1, NULL, 1, 0),
    (121, 115, '',                        '',                     'fa fa-align-center', 'admin:type:page',    1, NULL, 1, 0),

    -- 作品（视频 / 图片 / 文字作品的人工复审入口）
    (130, 1,   '',                        '',                     'fa fa-image',        '作品',                -1, NULL, 3, 0),
    (131, 130, 'page/work/work.html',     '',                     'fa fa-image',        'admin:work:page',     0, NULL, 3, 0),
    (113, 131, '',                        '',                     'fa fa-remove',       'admin:work:delete', 1, NULL, 1, 0),
    (114, 131, '',                        '',                     'fa fa-info',         'admin:work:get', 1, NULL, 1, 0),
    (125, 131, '',                        '',                     'fa ',                'admin:work:violations', 1, NULL, 1, 0),
    (127, 131, '',                        '',                     'fa fa-check',        'admin:work:audit', 1, NULL, 1, 0),

    -- 评论
    (135, 1,   '',                        '',                     'fa fa-comment',      '评论',                -1, NULL, 4, 0),
    (136, 135, 'page/comment/comment.html', '',                   'fa fa-comment',      'admin:comment:page',  0, NULL, 4, 0),
    (137, 136, '',                        '',                     'fa fa-check',        'admin:comment:audit', 1, NULL, 1, 0),

    -- 首页统计
    (126, 32,  '',                        '',                     'fa ',                'admin:work:statistics', 1, NULL, 1, 0),

    -- 权限管理
    (74,  73,  'page/permission.html',    '',                     'fa fa-shield',       'permission:list',       0, '_self', 5, 0),
    (75,  74,  '',                        '',                     'fa fa-save',         'permission:add',        1, '_self', 5, 0),
    (76,  74,  '',                        '',                     'fa fa-gear',         'permission:update',     1, '_self', 5, 0),
    (77,  74,  '',                        '',                     'fa fa-remove',       'permission:delete',     1, '_self', 5, 0),
    (78,  74,  '',                        '',                     'fa fa-list',         'permission:treeSelect', 1, '_self', 5, 0),
    (79,  74,  '',                        '',                     'fa fa-tree',         'permission:treeList',   1, '_self', 5, 0),

    -- 角色管理
    (80,  73,  'page/role.html',          '',                     'fa fa-user-circle',  'role:list',          0, '_self', 5, 0),
    (81,  80,  '',                        '',                     'fa fa-save',         'role:add',           1, '_self', 5, 0),
    (82,  80,  '',                        '',                     'fa fa-gear',         'role:update',        1, '_self', 5, 0),
    (84,  80,  '',                        '',                     'fa fa-remove',       'role:delete',        1, '_self', 5, 0),
    (85,  80,  '',                        '',                     'fa fa-user-secret',  'role:authority',     1, '_self', 5, 0),
    (86,  80,  '',                        '',                     'fa fa-info',         'role:getPermission', 1, '_self', 5, 0),

    -- 分配角色
    (89,  73,  'page/user-role.html',     '',                     'fa fa-user-o',       'user:list',          0, '_self', 5, 0),
    (87,  89,  '',                        '',                     'fa fa-info',         'role:getRole',       1, '_self', 5, 0),
    (88,  89,  '',                        '',                     'fa fa-user-o',       'role:initRole',      1, '_self', 5, 0),
    (91,  89,  '',                        '',                     'fa fa-info',         'user:assignRole',    1, '_self', 5, 0),

    -- 系统配置
    (122, 32,  'page/setting.html',       '',                     'fa fa-cog',          '系统配置',                -1, NULL, 1, 0),
    (123, 122, '',                        '',                     'fa ',                'admin:setting:update',     1, NULL, 1, 0),
    (124, 122, '',                        '',                     'fa ',                'admin:setting:get',        1, NULL, 1, 0);


-- -----------------------------------------------------------------------------
-- 4) 角色 - 权限：超级管理员(1) 拿全部，审核员(16) 只拿审核相关
--    id 从 2000 起手工分配，保证脚本可重复执行（INSERT IGNORE 靠主键去重）。
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `role_permission` (`id`, `permission_id`, `role_id`)
SELECT 2000 + `id`, `id`, 1 FROM `permission`;

INSERT IGNORE INTO `role_permission` (`id`, `permission_id`, `role_id`) VALUES
    (3001, 1,   16),  -- 业务管理
    (3004, 113, 16),  -- admin:work:delete
    (3005, 114, 16),  -- admin:work:get
    (3006, 125, 16),  -- admin:work:violations
    (3007, 127, 16),  -- admin:work:audit
    (3008, 130, 16),  -- 作品
    (3009, 131, 16),  -- admin:work:page
    (3011, 135, 16),  -- 评论
    (3012, 136, 16),  -- admin:comment:page
    (3013, 137, 16);  -- admin:comment:audit


-- -----------------------------------------------------------------------------
-- 5) 系统配置（单行，id=1）
--    audit_open = 0 时内容审核整体短路；上线前记得按需打开。
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_setting` (`id`, `audit_policy`, `hot_limit`, `audit_open`, `allow_ip`, `auth`) VALUES
    (1, '{"successScore":{"minPulp":"0.1","maxPulp":"0.9999","minTerror":"0.1","maxTerror":"0.9999","minPolitician":"0.1","maxPolitician":"0.9999","auditStatus":"0"},"manualScore":{"minPulp":"0","maxPulp":"0","minTerror":"0","maxTerror":"0","minPolitician":"0","maxPolitician":"0","auditStatus":"3"},"passScore":{"minPulp":"0","maxPulp":"0","minTerror":"0","maxTerror":"0","minPolitician":"0","maxPolitician":"0","auditStatus":"2"}}',
     500, 0, '', 1);


-- -----------------------------------------------------------------------------
-- 6) 示例分类（可选，发布作品时必须有至少一个分类，否则前端选不出来）
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `type` (`id`, `name`, `description`, `open`, `sort`, `label_names`) VALUES
    (1, '生活',   '日常记录',       0, 1, '日常,vlog'),
    (2, '科技',   '数码与技术',     0, 2, '数码,编程'),
    (3, '游戏',   '游戏内容',       0, 3, '单机,手游'),
    (4, '音乐',   '音乐相关',       0, 4, '翻唱,原创'),
    (5, '知识',   '科普与学习',     0, 5, '科普,教程');


-- =============================================================================
-- 附录：如何生成 BCrypt 哈希
-- =============================================================================
-- 方式一（Java，与项目同款实现）：
--   引入 spring-security-crypto，然后
--     new BCryptPasswordEncoder().encode("你的新密码")
--
-- 方式二（Python）：
--   python -c "import bcrypt; print(bcrypt.hashpw(b'你的新密码', bcrypt.gensalt(rounds=10, prefix=b'2a')).decode())"
--
-- 拿到 $2a$10$... 之后替换 user 表对应行的 password 即可。
-- =============================================================================
