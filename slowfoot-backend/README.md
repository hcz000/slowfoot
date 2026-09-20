# 慢脚后端

慢脚后端是基于 Spring Boot 的短视频和内容社区服务，提供用户认证、作品发布、作品审核、点赞、收藏、分享、评论、关注 Feed、兴趣推荐、热门视频、热度排行榜和后台管理等功能。

## 技术栈

- Java 21
- Spring Boot 3.5
- Spring MVC、Spring Validation、Spring AOP
- MyBatis-Plus
- Apache ShardingSphere-JDBC
- MySQL 8+
- Redis
- Apache RocketMQ
- 阿里云 OSS 和内容安全服务
- Maven Wrapper

## 项目结构

```text
src/main/java/org/hcz/
├── controller/       Web 接口
├── service/          业务服务
├── entity/           数据库实体和接口对象
├── mapper/           MyBatis-Plus Mapper
├── messaging/        按功能组织的事件、消息、发布器和消费者
│   ├── workinteraction/  作品互动、统计、推荐和热榜更新
│   ├── notification/     通知创建
│   ├── workaudit/        作品审核提交和完成事件
│   └── commentaudit/     评论审核提交事件
├── security/
│   ├── access/       RBAC 鉴权和 Redis 接口限流
│   └── SecurityAuditLogger.java  结构化安全审计日志
├── schedul/          审核派发、热榜重建等定时任务
├── config/            Spring、Redis、OSS、邮件等配置
└── util/              JWT、Redis、分页等通用工具

src/main/resources/
├── application.yml   应用配置
├── application-sharding.yml  分表 profile 数据源入口
├── application-observability.yml  ECS JSON 日志 profile
├── sharding.yml      4 路水平分表规则
├── db/schema.sql      新环境初始化脚本
├── db/migration/      已有数据库的增量迁移脚本
└── static/            后端托管的静态资源
```

## 环境要求

启动前需要准备：

1. JDK 21
2. Maven Wrapper 所需的网络或本地 Maven 缓存
3. MySQL 8+ 数据库
4. Redis 6+
5. RocketMQ NameServer 和 Broker
6. 需要上传或审核内容时配置阿里云 OSS、内容安全和邮件服务

## 数据库初始化

新数据库执行：

```bash
mysql -uroot -p lucky-journey < src/main/resources/db/schema.sql
```

已有数据库执行：

```bash
mysql -uroot -p lucky-journey < src/main/resources/db/migration/20260918_cache_history.sql
```

该迁移会新增以下两张表，用于保证 Redis 不可用时仍可恢复历史数据：

- `work_view_history`：用户浏览历史
- `user_search_history`：用户搜索历史

项目支持可选的单库 4 路水平分表。已有环境必须先建物理表、回填并校验数据，再启用 `sharding` profile；详细步骤、校验和回滚约束见[四分表迁移操作手册](docs/sharding-rollout.md)。

## 配置启动

默认服务端口为 `8882`。生产环境建议通过环境变量覆盖数据库、Redis、RocketMQ、JWT、OSS 和邮件配置，不要把真实密钥提交到仓库。

最小本地启动示例：

```powershell
$env:DATASOURCE_HOST = "127.0.0.1:3306"
$env:DATASOURCE_NAME = "lucky-journey"
$env:DATASOURCE_USER = "root"
$env:DATASOURCE_PASSWORD = "your-password"
$env:REDIS_HOST = "127.0.0.1"
$env:ROCKETMQ_NAME_SERVER = "127.0.0.1:9876"
./mvnw.cmd spring-boot:run
```

也可以先打包再运行：

```bash
./mvnw clean package
java -jar target/slowfoot-backend-0.0.1-SNAPSHOT.jar
```

Windows 使用：

```powershell
./mvnw.cmd clean package
java -jar target/slowfoot-backend-0.0.1-SNAPSHOT.jar
```

## 访问控制和安全审计

访问控制能力统一位于 `org.hcz.security.access`：

- `Auth`、`AuthAspect` 和 `AuthStore`：校验访问令牌和 RBAC 权限，并在角色或权限变更后使权限快照失效。
- `Limit` 和 `LimiterAop`：使用 Redis 计数窗口限制接口调用频率，优先按用户 ID、未登录时按来源 IP 隔离限流桶。
- `SecurityAuditLogger`：记录登录失败、鉴权拒绝和触发限流等结构化安全事件。

启用 `observability` profile 后，可以在 OpenObserve 中集中查询安全事件并配置告警。字段、查询示例和部署参数见[可观测性文档](../docs/observability.md)。

## 消息和缓存设计

### RocketMQ

RocketMQ 用于处理提交后的派生逻辑：

- 互动事件：统计以数据库事务为准，热榜和推荐模型异步更新
- 通知事件：异步创建通知
- 审核事件：异步执行作品和评论审核
- 审核完成事件：异步刷新审核后的派生数据

消息代码统一放在 `org.hcz.messaging`，并按 `workinteraction`、`notification`、`workaudit` 和 `commentaudit` 功能分包。每个功能包自行维护 Spring 事件、RocketMQ 消息、发布器、消费者及 Topic/消费组常量，避免跨功能共享发布器或常量容器。

互动事件使用独立消费组处理热榜和推荐，消费者失败时由 RocketMQ 重投。当前项目没有引入 Outbox；如果业务需要严格保证数据库提交和消息投递的一致性，需要后续增加 Outbox 或持久化重放任务。

### Redis

Redis 主要保存推荐候选、用户推荐模型、Feed、热榜和历史查询加速数据。当前 Key 使用 `v2:` 命名空间，用于隔离旧版本的序列化对象。

以下数据有 MySQL 保底：

- 浏览历史
- 搜索历史
- 作品、互动关系、评论和通知
- 热榜重建所需的作品统计数据

Redis 不可用时，推荐、分类、相似作品、热榜和关注 Feed 会使用受限的 MySQL 查询降级。Redis 恢复后，相关派生数据由后续事件或定时任务重建。

旧版本 Redis Key 不会被新代码读取。确认新版本运行正常后，可按业务窗口清理旧 Key 以释放空间。

## 常用命令

```bash
# 编译
./mvnw -DskipTests compile

# 运行全部测试
./mvnw test

# 运行推荐相关测试
./mvnw -Dtest=RecommendationUpgradeTests test
```

Windows 将 `./mvnw` 替换为 `./mvnw.cmd`。

## 运行说明

- 作品文件存储在阿里云 OSS，播放地址由后端按权限签发临时地址。
- 作品发布和评论会进入审核流程，审核任务落库后由调度器派发。
- 热榜每小时扫描作品统计重建，热门视频集合每三小时刷新。
- 浏览历史按用户和作品去重，并保存最近浏览时间；历史展示会按作品 ID 回查 MySQL，因此作品标题和作者信息变化后可以反映最新数据。
- 搜索历史同步写入 MySQL，再尽力写入 Redis；删除搜索历史时先删除 MySQL，再删除 Redis 缓存。

## 相关文档

- [项目总览](../README.md)
- [架构设计文档](../docs/架构设计文档.md)
- [四分表迁移操作手册](docs/sharding-rollout.md)
- [可观测性与 OpenObserve](../docs/observability.md)
- [前端项目说明](../slowfoot-frontend/README.md)
- [贡献指南](../CONTRIBUTING.md)
