# 慢脚

慢脚是一个短视频和内容社区项目，包含 Vue 3 前端和 Spring Boot 后端，支持作品发布、内容审核、点赞、收藏、分享、评论、关注 Feed、兴趣推荐、热门视频、热度排行榜和后台管理。

## 仓库结构

```text
慢脚/
├── slowfoot-backend/   Spring Boot 后端
├── slowfoot-frontend/  Vue 3 前端
├── docs/               架构和设计文档
├── image/              项目截图
├── CONTRIBUTING.md     贡献指南
└── README.md           项目总览
```

## 技术栈

后端使用 Java 21、Spring Boot 3、MyBatis-Plus、ShardingSphere-JDBC、MySQL、Redis、RocketMQ、阿里云 OSS 和内容安全服务。前端使用 Vue 3、Vite、Vue Router、Pinia、Element Plus、Axios 和 Video.js。

## 核心能力

- 用户注册、登录、JWT 鉴权和 RBAC 权限管理
- 视频、图片、文字作品发布和管理
- 阿里云 OSS 私有存储与临时访问地址
- 文本、图片、视频内容审核
- 点赞、收藏、分享、评论、关注和通知
- 兴趣推荐、分类推荐、相似作品和关注 Feed
- 热门视频和热度排行榜
- 浏览历史和搜索历史持久化
- RocketMQ 异步处理通知、审核和推荐/热榜派生数据

## 快速开始

### 环境要求

- JDK 21
- Node.js 18+
- MySQL 8+
- Redis 6+
- RocketMQ NameServer 和 Broker
- 使用 OSS、邮件或内容审核功能时，需要配置对应的阿里云和 SMTP 服务

### 初始化数据库

新环境执行：

```bash
mysql -uroot -p lucky-journey < slowfoot-backend/src/main/resources/db/schema.sql
```

已有数据库执行增量迁移：

```bash
mysql -uroot -p lucky-journey < slowfoot-backend/src/main/resources/db/migration/20260918_cache_history.sql
```

该迁移新增 `work_view_history` 和 `user_search_history`，用于在 Redis 不可用时恢复浏览历史和搜索历史。

需要启用 4 路水平分表时，按照[四分表迁移操作手册](slowfoot-backend/docs/sharding-rollout.md)依次建表、回填和校验，再启用 `sharding` profile。未启用该 profile 时仍使用原逻辑表。

### 启动后端

```powershell
cd slowfoot-backend
./mvnw.cmd spring-boot:run -Dspring-boot.run.jvmArguments=-Xmx256m
```

默认端口为 `8882`。也可以执行 `./mvnw.cmd clean package` 后运行 `target/slowfoot-backend-0.0.1-SNAPSHOT.jar`。

### 启动前端

```powershell
cd slowfoot-frontend
npm install
npm run dev
```

## 配置

后端配置位于 `slowfoot-backend/src/main/resources/application.yml`。生产环境应通过环境变量配置数据库、Redis、RocketMQ、JWT、OSS 和邮件服务，不要提交真实密钥。

后端访问控制统一放在 `org.hcz.security.access`，包含 JWT/RBAC 权限切面和基于 Redis 的接口限流。安全拒绝和限流事件通过结构化日志输出，可由 OpenObserve 集中检索和告警。

RocketMQ 默认使用互动事件、通知、审核提交和审核完成主题。互动事件由独立消费组更新热榜和推荐模型。

Redis Key 当前使用 `v2:` 命名空间。作品、互动关系、评论、通知、浏览历史和搜索历史以 MySQL 为主数据源；推荐候选、用户模型、Feed 和热榜属于 Redis 派生数据。Redis 异常时，推荐、分类、相似作品、热榜和 Feed 会使用受限的 MySQL 查询降级。

## 测试和构建

```powershell
cd slowfoot-backend
./mvnw.cmd -DskipTests compile
./mvnw.cmd test

cd ..\slowfoot-frontend
npm run build
```

## 文档

- [架构设计文档](docs/架构设计文档.md)
- [后端 README](slowfoot-backend/README.md)
- [四分表迁移操作手册](slowfoot-backend/docs/sharding-rollout.md)
- [可观测性与 OpenObserve](docs/observability.md)
- [前端项目说明](slowfoot-frontend/README.md)
- [贡献指南](CONTRIBUTING.md)

## 当前边界

互动主数据和统计在数据库事务中完成，提交后的通知、热榜和推荐更新通过 RocketMQ 异步执行。当前没有 Outbox，因此数据库提交成功到消息发送之间仍存在极短投递窗口；严格可靠投递需要后续增加 Outbox 或持久化重放任务。
