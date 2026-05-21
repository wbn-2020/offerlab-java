# OfferLab Java Backend

OfferLab 是一个面向求职者的面经与技术内容社区。后端采用 Java 17、Spring Boot 3、MyBatis-Plus、Redis、Kafka、Elasticsearch 和模块化单体架构，当前开发分支为 `dev-v1`。

## 当前状态

已完成 P0/P1/P2 主体能力：

- 用户：注册、登录、登出、当前用户、个人主页、资料编辑、求职意向、隐私设置、关注、粉丝和关注列表。
- 内容：帖子发布、编辑、删除、详情、列表、标签、标签详情页帖子。
- 互动：帖子点赞、收藏、评论、评论点赞、个人点赞/收藏列表。
- Feed：关注流、推荐流、最新流、热门流，Kafka `post.published` fanout 写入关注收件箱。
- 通知：关注、点赞、评论、收藏、mention 通知，支持列表、未读数、单条已读、全部已读。
- 搜索：ES 搜索、建议词、热词、MySQL fallback、索引状态、异步索引重建任务。
- 运维：`/api/v1/ops/status` 查看 ES 与 Outbox 状态，支持数据库 Admin 角色、白名单和本地开发模式。
- 趋势看板：基于真实公开帖子、扩展字段和标签统计发布趋势、热门公司、高频标签、岗位分布。

## 模块结构

```text
community-bootstrap              启动模块
community-common                 通用 Result、错误码、分页、异常
community-infrastructure         JWT、Trace、Redis、Kafka、ES、Outbox、权限辅助
community-domain-user            用户与关注
community-domain-post            帖子、标签、计数器
community-domain-interaction     点赞、收藏、评论
community-domain-feed            Feed 收件箱与 Kafka fanout
community-domain-search          搜索、索引、运维状态
community-domain-notification    通知
community-domain-analytics       趋势看板统计
community-archtest               架构测试
db/init                          初始化 SQL
```

## 环境要求

- JDK 17
- Maven 3.8+
- MySQL 8.0
- Redis 7+
- Kafka 3.6+
- Elasticsearch 8.x 可选；不可用时搜索会降级到 MySQL fallback

本机默认配置：

```yaml
server.port: 8080
spring.datasource.url: jdbc:mysql://localhost:3306/offerlab
spring.datasource.username: offerlab
spring.datasource.password: offerlab123
spring.data.redis.host: localhost
spring.kafka.bootstrap-servers: localhost:9092
offerlab.elasticsearch.url: http://127.0.0.1:9200
```

如本机使用 root 账号，也可以通过本地配置或环境变量覆盖数据源配置。

## 启动方式

可使用仓库内 Docker Compose 启动 MySQL、Redis、Kafka：

```bash
docker compose up -d mysql redis kafka
```

编译：

```bash
mvn -DskipTests clean install
```

启动后端：

```bash
mvn -pl community-bootstrap spring-boot:run
```

访问：

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

## 常用接口

认证：

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@example.com","password":"password123","nickname":"Demo"}'

curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@example.com","password":"password123"}'
```

核心接口：

| Method | Path | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/api/v1/auth/register` | 注册 | 否 |
| POST | `/api/v1/auth/login` | 登录 | 否 |
| GET | `/api/v1/users/me` | 当前用户 | 是 |
| PATCH | `/api/v1/users/me` | 修改资料 | 是 |
| PUT | `/api/v1/users/me/intent` | 修改求职意向 | 是 |
| GET | `/api/v1/users/me/privacy-settings` | 查询隐私设置 | 是 |
| PUT | `/api/v1/users/me/privacy-settings` | 保存隐私设置 | 是 |
| GET | `/api/v1/users/{uid}` | 用户主页 | 否 |
| POST/DELETE | `/api/v1/users/{uid}/follow` | 关注/取关 | 是 |
| GET | `/api/v1/posts` | 帖子列表 | 否 |
| POST | `/api/v1/posts` | 发布帖子 | 是 |
| GET | `/api/v1/posts/{postId}` | 帖子详情 | 否 |
| POST/DELETE | `/api/v1/posts/{postId}/like` | 点赞/取消 | 是 |
| POST/DELETE | `/api/v1/posts/{postId}/favorite` | 收藏/取消 | 是 |
| POST | `/api/v1/posts/{postId}/comments` | 评论 | 是 |
| GET | `/api/v1/tags` | 标签列表 | 否 |
| GET | `/api/v1/feeds/latest` | 最新流 | 否 |
| GET | `/api/v1/feeds/following` | 关注流 | 是 |
| GET | `/api/v1/notifications` | 通知列表 | 是 |
| GET | `/api/v1/search/posts` | 搜索帖子 | 否 |
| GET | `/api/v1/search/status` | 搜索状态 | 否 |
| POST | `/api/v1/search/admin/rebuild` | 异步重建索引 | 是，admin |
| GET | `/api/v1/search/admin/tasks/{taskId}` | 查询重建任务 | 是，admin |
| GET | `/api/v1/ops/status` | 运维状态 | 是，admin |
| GET | `/api/v1/ops/outbox` | Outbox 最近消息 | 是，admin |
| POST | `/api/v1/ops/outbox/{id}/retry` | 单条失败消息重试 | 是，admin |
| GET | `/api/v1/ops/admins` | Admin 角色列表 | 是，admin |
| POST | `/api/v1/ops/admins` | 添加或启用 Admin | 是，admin |
| POST | `/api/v1/ops/admins/{uid}/status` | 启用/禁用 Admin | 是，admin |
| GET | `/api/v1/dashboard/trend` | 趋势看板 | 否 |

## Admin 权限

运维接口优先使用数据库 Admin 角色表 `t_user_admin`，可通过 `/api/v1/ops/admins` 维护。也支持 UID 白名单：

```bash
OFFERLAB_ADMIN_UIDS=10001,10002
```

或配置：

```yaml
offerlab:
  admin:
    uid-whitelist: 10001,10002
```

白名单为空且 `t_user_admin` 没有任何 Admin 记录时，本地开发环境允许已登录用户访问运维接口，避免开发时锁死。一旦表中已配置过 Admin 记录，则按 RBAC 校验，不再自动回退到本地宽松模式。生产环境应显式配置数据库 Admin 或白名单。

## 验证记录

最近一次本机验证：

- `mvn -DskipTests clean install` 通过。
- `GET/PUT /api/v1/users/me/privacy-settings` 通过，本机可持久化个人隐私偏好。
- `GET /api/v1/dashboard/trend?range=30d` 返回 `code=0`，本机 `totalPosts=8`。
- `POST /api/v1/search/admin/rebuild` 返回任务，轮询后 `SUCCEEDED`，本机 `indexed=8, failed=0, total=8`。

全链路 smoke 脚本：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-offerlab.ps1
```

如果本地已启用 RBAC，可指定管理员账号执行运维 smoke：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-offerlab.ps1 -AdminEmail admin@example.com
```

默认输出报告到 `C:\codeware\offerlab-smoke-report.json`。

## 后续可选增强

- Outbox 批量运维操作。
- Docker Compose 补 Elasticsearch 服务与生产化 profile。
