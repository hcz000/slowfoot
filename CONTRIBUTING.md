# 慢脚贡献指南

感谢参与慢脚项目。提交代码、修复文档或反馈问题前，请先确认修改范围，保持现有模块边界和部署方式稳定。

## 开发环境

后端：

- JDK 21
- MySQL 8+
- Redis 6+
- RocketMQ NameServer 和 Broker
- Maven Wrapper

前端：

- Node.js 18+
- npm 9+

涉及 OSS、邮件或内容审核的功能，还需要对应的外部服务配置。请使用环境变量或本地配置文件，不要提交真实密钥。

## 开始开发

```bash
git clone <repository-url>
cd lucky-journey

cd slowfoot-backend
./mvnw test

cd ../slowfoot-frontend
npm install
npm run build
```

Windows 使用 `mvnw.cmd` 替代 `mvnw`。

已有数据库需要先执行 `slowfoot-backend/src/main/resources/db/migration/20260918_cache_history.sql`，新数据库执行 `schema.sql`。启用 `sharding` profile 前必须按照[四分表迁移操作手册](slowfoot-backend/docs/sharding-rollout.md)完成建表、回填和校验。

## 修改原则

- 优先复用现有 Service、Mapper、事件和配置模式。
- 保持控制器只负责接口编排，业务规则放在 Service。
- MySQL 主数据和 Redis 派生数据的职责必须清晰，Redis 故障时不能让关键主流程直接失败。
- 修改缓存 Key、序列化格式或消息字段时，必须考虑旧数据、旧消费者和回滚方案。
- 数据库结构变更必须同时更新初始化脚本和增量迁移脚本。
- 涉及事务、异步任务或 RocketMQ 消费时，说明幂等、重试和失败后的恢复方式。
- 事件、消息、发布器和消费者放在 `org.hcz.messaging.<功能>`，由对应功能包维护自己的 Topic 和消费组常量。
- RBAC 鉴权、权限快照和接口限流统一放在 `org.hcz.security.access`。
- 不要为了小功能随意引入新的框架或跨模块全局状态。

## 测试要求

后端至少执行：

```bash
cd slowfoot-backend
./mvnw -DskipTests compile
./mvnw test
```

前端至少执行：

```bash
cd slowfoot-frontend
npm run build
```

涉及缓存或消息的改动，建议补充 Redis 不可用、消息重复、消费者失败重试、数据库回退和并发调用测试。

## 提交规范

提交消息使用简洁的 Conventional Commits 风格：

```text
feat: 增加作品搜索
fix: 修复浏览历史数据库回退
docs: 更新缓存架构说明
refactor: 重构审核任务派发
test: 增加热榜并发测试
chore: 更新构建配置
```

一次提交尽量只解决一个主题，避免把格式化、无关重构和功能修改混在一起。

## Pull Request

提交 PR 时说明：

- 修改目的和影响范围
- 是否有数据库迁移
- 是否改变接口、缓存 Key 或消息格式
- 如何验证，包含执行过的命令和结果
- 已知限制和后续工作

涉及用户数据、权限、文件访问、审核或消息可靠性的改动，需要特别说明失败场景和回滚方式。

## 文档和命名

项目对外名称为“慢脚”。代码包名 `org.hcz`、现有接口前缀 `/luckyjourney` 和内部 RocketMQ 默认 topic 为兼容历史部署暂时保留，除非变更说明明确要求迁移。

架构变更同步更新 [项目总览](README.md) 和 [架构设计文档](docs/架构设计文档.md)。
