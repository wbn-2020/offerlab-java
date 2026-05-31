# OfferLab Java Backend

OfferLab 是一个面向求职者的面经、技术内容和面试题库社区。后端采用 Java 17、Spring Boot 3、MyBatis-Plus、MySQL、Redis、Kafka、Elasticsearch 的模块化单体架构，当前主开发分支为 `dev-v2`。

## 项目能力

- 用户体系：注册、登录、当前用户、个人主页、资料编辑、求职意向、隐私设置、关注、粉丝和关注列表。
- 内容社区：帖子发布、编辑、删除、详情、列表、标签、标签详情、内容举报和审核。
- 互动能力：点赞、收藏、评论、评论点赞、个人点赞和收藏列表。
- Feed 流：关注流、推荐流、最新流、热门流，支持 Kafka `post.published` fanout 写入关注收件箱。
- 通知中心：关注、点赞、评论、收藏、mention 通知，支持列表、未读数、单条已读和全部已读。
- 搜索能力：Elasticsearch 搜索、建议词、热词、MySQL fallback、索引状态、异步索引重建任务。
- 面试题库：面经帖题目提取、题目列表、题目详情、刷题进度、公司备战页、公司别名治理。
- AI 提取：支持可选 DeepSeek 大模型题目提取；未启用或调用失败时回退到本地规则提取。
- 运营治理：运维状态、Outbox 重试、Admin 角色、权限检查、审计日志、内容治理关键词。
- 趋势看板：基于真实公开帖子和扩展字段统计发布趋势、热门公司、高频标签和岗位分布。

## 模块结构

```text
community-bootstrap              启动模块与运行配置
community-common                 Result、错误码、分页、通用异常
community-infrastructure         JWT、Trace、Redis、Kafka、ES、Outbox、权限辅助
community-domain-user            用户、关注、隐私设置
community-domain-post            帖子、标签、计数器、举报
community-domain-interaction     点赞、收藏、评论
community-domain-feed            Feed 收件箱与 Kafka fanout
community-domain-search          搜索、索引、运维状态、内容治理
community-domain-question        面试题库、AI/规则提取、题目治理
community-domain-notification    通知
community-domain-analytics       趋势看板统计
community-archtest               架构与生产安全测试
db/init                          新库初始化 SQL
db/migration                     已有库增量迁移 SQL
scripts                          本地验证、冒烟和中间件检查脚本
```

## 技术栈

- Java 17
- Spring Boot 3.2
- Spring Cloud 2023
- MyBatis-Plus 3.5
- MySQL 8
- Redis 7
- Kafka 3.6
- Elasticsearch 8，可不可用时搜索降级到 MySQL fallback
- Springdoc OpenAPI
- JUnit 5、ArchUnit

## 环境要求

- JDK 17
- Maven 3.8+
- MySQL 8.0
- Redis 7+
- Kafka 3.6+
- Elasticsearch 8.x，可选

默认本地配置：

```yaml
server.port: 8080
spring.datasource.url: jdbc:mysql://localhost:3306/offerlab
spring.datasource.username: offerlab
spring.datasource.password: offerlab123
spring.data.redis.host: localhost
spring.kafka.bootstrap-servers: localhost:9092
offerlab.elasticsearch.url: http://127.0.0.1:9200
```

生产环境不要使用默认 JWT 密钥、数据库密码或本地宽松 Admin 模式。

## 启动方式

安装依赖并编译：

```powershell
mvn -DskipTests clean install
```

启动后端：

```powershell
mvn -pl community-bootstrap -am spring-boot:run
```

`-am` 会同时构建启动模块依赖的当前源码模块，避免只启动
`community-bootstrap` 时复用本地仓库中过期的 `*-SNAPSHOT.jar`。

访问地址：

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

## 数据库说明

`db/init/*.sql` 仅用于新库初始化。已有数据库应优先审阅并按需手动执行 `db/migration/*.sql` 中的非破坏性增量脚本。

已验证的本地迁移入口：

```sql
SOURCE db/migration/20260524_ops_governance.sql;
```

迁移脚本预期只创建缺失表和缺失索引，不应删除表、清空数据或重置 schema。执行前建议先备份数据库，并在测试库验证。

## AI 调用说明

后端存在真实 AI 调用能力，但默认关闭。

实现位置：

- `community-domain-question/src/main/java/com/offerlab/community/question/application/QuestionExtractor.java`
- `community-domain-question/src/main/java/com/offerlab/community/question/application/DeepseekQuestionExtractor.java`
- `community-domain-question/src/main/java/com/offerlab/community/question/application/RuleBasedQuestionExtractor.java`

默认行为：

- `offerlab.ai.deepseek.enabled=false`
- `offerlab.ai.deepseek.api-key` 为空
- 未启用、未配置密钥或调用失败时，自动回退到本地规则提取。

启用示例：

```yaml
offerlab:
  ai:
    deepseek:
      enabled: true
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
      model: deepseek-chat
      timeout-millis: 15000
```

当前 AI 主要用于从面经帖子中提取面试题。建议线上启用前补齐限流、成本统计、调用审计、敏感内容脱敏和更细的失败告警。

## 常用接口

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
| POST/DELETE | `/api/v1/users/{uid}/follow` | 关注或取关 | 是 |
| GET | `/api/v1/posts` | 帖子列表 | 否 |
| POST | `/api/v1/posts` | 发布帖子 | 是 |
| GET | `/api/v1/posts/{postId}` | 帖子详情 | 否 |
| POST | `/api/v1/posts/{postId}/reports` | 举报帖子 | 是 |
| POST/DELETE | `/api/v1/posts/{postId}/like` | 点赞或取消 | 是 |
| POST/DELETE | `/api/v1/posts/{postId}/favorite` | 收藏或取消 | 是 |
| POST | `/api/v1/posts/{postId}/comments` | 评论 | 是 |
| GET | `/api/v1/tags` | 标签列表 | 否 |
| GET | `/api/v1/feeds/latest` | 最新流 | 否 |
| GET | `/api/v1/feeds/following` | 关注流 | 是 |
| GET | `/api/v1/notifications` | 通知列表 | 是 |
| GET | `/api/v1/search/posts` | 搜索帖子 | 否 |
| GET | `/api/v1/search/status` | 搜索状态 | 否 |
| POST | `/api/v1/search/admin/rebuild` | 异步重建索引 | 是，admin |
| GET | `/api/v1/search/admin/tasks/{taskId}` | 查询重建任务 | 是，admin |
| GET | `/api/v1/questions` | 面试题列表 | 否 |
| GET | `/api/v1/questions/{id}` | 面试题详情 | 否 |
| POST | `/api/v1/admin/posts/{postId}/extract-questions` | 从帖子提取题目 | 是，admin |
| GET | `/api/v1/admin/ai-tasks` | AI 提取任务列表 | 是，admin |
| POST | `/api/v1/admin/ai-tasks/{id}/retry` | 重试 AI 提取任务 | 是，admin |
| GET | `/api/v1/ops/status` | 运维状态 | 是，admin |
| GET | `/api/v1/ops/outbox` | Outbox 最近消息 | 是，admin |
| POST | `/api/v1/ops/outbox/{id}/retry` | 单条失败消息重试 | 是，admin |
| GET | `/api/v1/dashboard/trend` | 趋势看板 | 否 |

## Admin 权限

后台接口优先使用数据库 Admin 角色表 `t_user_admin`。也支持 UID 白名单：

```powershell
$env:OFFERLAB_ADMIN_UIDS = "10001,10002"
```

或配置：

```yaml
offerlab:
  admin:
    uid-whitelist: 10001,10002
```

`ADMIN` 拥有全部后台权限，也可授权更小角色：

- `OPS`：运维状态、Outbox 重试、审计日志。
- `CONTENT_MODERATOR`：帖子、评论、举报审核。
- `QUESTION_OPERATOR`：题目提取、题目审核、公司别名维护。

后台写操作会在 `t_admin_audit_log` 存在时记录审计日志。

## 本地验证

完整本地验证：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1
```

本地中间件路径检查：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-local-middleware.ps1
```

全链路 smoke：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-offerlab.ps1
```

如果本地已启用 RBAC，可指定管理员账号：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-offerlab.ps1 -AdminEmail admin@example.com
```

默认报告输出到 `C:\codeware\offerlab-smoke-report.json`。

## 相关文档

- 本地中间件运行手册：[docs/middleware-local-runbook.md](docs/middleware-local-runbook.md)
- 数据库脚本说明：[db/README.md](db/README.md)
