# offerlab-java

「面试圈」社区平台后端，Java 17 + Spring Boot 3 模块化单体。

> 关联文档：`C:\project\社区-PRD\` 下完整设计文档（PRD、模块、数据库、Redis、Feed 流、Kafka、ES、ClickHouse、Netty、API、前端）

## 当前进度（MVP 第一阶段）

已实现：
- 模块化单体骨架（11 个 Maven 模块）+ ArchUnit 架构约束
- 用户：注册、登录、登出、个人主页、求职意向、关注/取关、粉丝/关注列表
- 内容：发帖（4 种类型 + 扩展 JSON）、编辑、删除、详情、列表
- 互动：点赞 / 取消、收藏 / 取消、评论（二级回复）、计数器
- Feed：基于 Spring 本地事件的纯推扩散 + 收件箱（Redis ZSet + Lua 原子裁剪）+ 关注流 / 最新流 / 热门流（暂复用最新）
- 搜索：MySQL LIKE 简化版（二期切换 ES）
- 通知 / 看板：占位接口（二期实现）
- 基础设施：JWT、BCrypt、TraceId、全局异常、限流（Redis Lua 滑动窗口）、雪花 ID

未实现（二期）：
- Kafka 事件总线 + Outbox 事务消息
- 大 V 推拉分流
- ES 全文检索 / 自动补全
- ClickHouse 行为分析 / 看板
- Netty 长连接通知

## 项目结构

```
offerlab-java/
├── community-bootstrap/              # 启动模块
├── community-common/                 # Result、ErrorCode、PageResult、异常
├── community-infrastructure/         # JWT、BCrypt、TraceId、限流、Lua、Redis 配置
├── community-domain-user/            # 用户 / 关注
├── community-domain-post/            # 帖子 / 标签 / 计数器
├── community-domain-interaction/     # 点赞 / 收藏 / 评论
├── community-domain-feed/            # Feed 流 / 收件箱
├── community-domain-search/          # 搜索（MVP MySQL LIKE）
├── community-domain-notification/    # 通知（占位）
├── community-domain-analytics/       # 看板（占位）
├── community-archtest/               # ArchUnit 架构测试
├── db/init/                          # 建表 SQL，docker-compose 启动时自动执行
├── docker-compose.yml                # MySQL + Redis
└── pom.xml
```

## 模块依赖

```
                    bootstrap
                        │
   ┌──── 7 个 domain ────┴────────┐
   │                              │
   ▼                              ▼
infrastructure ───────────────► common
```

domain 之间：feed 依赖 user/post/interaction；interaction 依赖 post；search 依赖 post。其他通过 Facade 隔离。

## 快速启动

### 前置

- JDK 17
- Maven 3.8+
- Docker Desktop / Docker Engine

### 1. 启动 MySQL + Redis

```bash
docker-compose up -d
```

容器启动时会自动执行 `db/init/*.sql`，创建库和表，并写入演示标签。

确认服务：

```bash
docker-compose ps
docker exec -it offerlab-mysql mysql -uoofferlab -pofferlab123 offerlab -e "SHOW TABLES;"
```

### 2. 编译

```bash
mvn -DskipTests clean install
```

### 3. 启动应用

```bash
mvn -pl community-bootstrap spring-boot:run
```

服务在 `http://localhost:8080` 启动。
Swagger UI：`http://localhost:8080/swagger-ui.html`

### 4. 联调示例

```bash
# 注册
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"a@a.com","password":"123456","nickname":"Tom"}'

# 登录
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"a@a.com","password":"123456"}'

# 设置 token 后发帖
TOKEN=...
curl -s -X POST http://localhost:8080/api/v1/posts \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"postType":1,"title":"字节后端二面","content":"# 一面...","extJson":"{\"company\":\"字节跳动\",\"position\":\"Java 后端\",\"yearsOfExp\":2}"}'

# 关注流
curl -s "http://localhost:8080/api/v1/feeds/following" -H "Authorization: Bearer $TOKEN"

# 最新流（公开）
curl -s "http://localhost:8080/api/v1/feeds/latest"
```

## 关键 API

| Method | Path | 说明 | 鉴权 |
|---|---|---|---|
| POST | /api/v1/auth/register | 注册 | 否 |
| POST | /api/v1/auth/login | 登录 | 否 |
| POST | /api/v1/auth/logout | 登出 | 是 |
| GET | /api/v1/users/{uid} | 用户主页 | 否 |
| GET | /api/v1/users/me | 我的资料 | 是 |
| PATCH | /api/v1/users/me | 改资料 | 是 |
| PUT | /api/v1/users/me/intent | 改求职意向 | 是 |
| POST/DELETE | /api/v1/users/{uid}/follow | 关注/取关 | 是 |
| GET | /api/v1/users/{uid}/followers/following | 粉丝/关注列表 | 否 |
| POST | /api/v1/posts | 发帖 | 是 |
| GET | /api/v1/posts/{postId} | 帖子详情 | 否 |
| PUT/DELETE | /api/v1/posts/{postId} | 编辑/删除 | 是 |
| GET | /api/v1/posts | 列表（含 ?authorId=） | 否 |
| POST/DELETE | /api/v1/posts/{postId}/like | 点赞/取消 | 是 |
| POST/DELETE | /api/v1/posts/{postId}/favorite | 收藏/取消 | 是 |
| POST | /api/v1/posts/{postId}/comments | 评论 | 是 |
| GET | /api/v1/posts/{postId}/comments | 评论列表 | 否 |
| GET | /api/v1/feeds/{following,recommend,latest,hot} | 四种 Feed | following 需登录 |
| GET | /api/v1/search/posts?q= | 搜索 | 否 |

详细约定见 `C:\project\社区-PRD\09-API规范.md`。

## 配置

`community-bootstrap/src/main/resources/application.yml`：

| Key | 默认 | 说明 |
|---|---|---|
| spring.datasource.url | jdbc:mysql://localhost:3306/offerlab | DB |
| spring.data.redis.host/port | localhost:6379 | Redis |
| offerlab.jwt.secret | 默认开发密钥 | 生产用环境变量覆盖 |
| offerlab.jwt.ttl-hours | 168 | Token 7 天 |
| offerlab.feed.bigv-threshold | 1000 | 大 V 粉丝数（二期生效） |
| offerlab.feed.inbox-capacity | 1000 | 收件箱保留条数 |
| offerlab.feed.inbox-ttl-seconds | 604800 | 收件箱 TTL 7 天 |

## 开发约定

- 通用响应：`{ "code": 0, "message": "ok", "data": ..., "traceId": "..." }`
- 错误码段：1xxxx 客户端、2xxxx 服务端、3xxxx 业务（按域分段）
- 鉴权：Header `Authorization: Bearer <jwt>`；标 `@PublicApi` 的接口免登录
- 限流：`@RateLimit(key="'biz:'+#uid", rate=N, per=秒)`
- 游标分页：响应含 `nextCursor` 与 `hasMore`
- 软删：表都有 `is_deleted` 字段，唯一索引中加 `is_deleted` 列

## 架构测试

```bash
mvn -pl community-archtest test
```

## 二期路线图

按 `C:\project\社区-PRD\` 文档逐步落地：

1. Outbox 事务消息表 + Kafka Topic（`05-Kafka设计.md`）
2. 大 V 推拉分流 + 多级缓存（`04-Feed流设计.md`、`03-Redis设计.md`）
3. ES 索引 + 同义词 + 自动补全（`06-ES设计.md`）
4. ClickHouse 行为流水 + 看板（`07-ClickHouse设计.md`）
5. Netty 长连接通知（`08-Netty长连接设计.md`）
