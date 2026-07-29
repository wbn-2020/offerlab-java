# OfferLab Java Backend

闻野（OfferLab）是围绕真实经验、有用见闻、公共讨论和长期内容沉淀的综合内容社区。科技数码、学习成长、职场经验、生活方式和投资理财是并列频道；技术、职场和求职相关内容仍然重要，但不代表整个平台的默认心智。后端采用 Java 17、Spring Boot 3、MyBatis-Plus、MySQL、Redis、Kafka、Elasticsearch 的模块化单体架构，当前主开发分支为 `dev-v2`。

## 项目能力

- 用户体系：注册、登录、当前用户、个人主页、资料编辑、学习偏好、隐私设置、关注、粉丝和关注列表。
- 内容社区：跨频道帖子发布、编辑、删除、详情、列表、话题、标签、内容举报和审核。
- 互动能力：点赞、收藏、评论、评论点赞、个人点赞和收藏列表。
- Feed 流：关注流、推荐流、最新流、热门流，支持 Kafka `post.published` fanout 写入关注收件箱。
- 通知中心：关注、点赞、评论、收藏、mention 通知，支持列表、未读数、单条已读和全部已读。
- 搜索能力：Elasticsearch 搜索、建议词、热词、MySQL fallback、索引状态、异步索引重建任务。
- 历史知识工具：公开内容结构化、知识卡、学习进度、主题学习包和实体别名治理，作为兼容能力而非公共社区主入口。
- AI 知识沉淀：支持可选 DeepSeek 大模型结构化提取；未启用或调用失败时回退到本地规则提取。
- 运营治理：运维状态、Outbox 重试、Admin 角色、权限检查、审计日志、内容治理关键词。
- 趋势看板：基于真实公开帖子和扩展字段统计发布趋势、热门实体、高频标签和场景分布。
- 公共共建：内容需求、协作合集、主题共创、频道策展和结构化讨论。
- 社区成长：影子声望与野点账本、有限虚拟权益、感谢票、平台配额悬赏和受治理社区角色；不涉及真实资金或用户间转账。

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
community-domain-question        知识库、AI/规则提取、知识卡治理
community-domain-notification    通知
community-domain-analytics       趋势看板统计
community-domain-incentive       声望、野点、虚拟权益、平台悬赏和社区角色治理
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
- MySQL 8.0.16+
- Redis 7+
- Kafka 3.6+
- Elasticsearch 8.x，可选

默认本地配置：

```yaml
server.port: 8080
server.address: 127.0.0.1
spring.datasource.url: jdbc:mysql://localhost:3306/offerlab
spring.datasource.username: offerlab
spring.datasource.password: offerlab-local-db-change-me
spring.data.redis.host: localhost
spring.data.redis.password: offerlab-local-redis-change-me
spring.kafka.bootstrap-servers: localhost:9092
offerlab.elasticsearch.url: http://127.0.0.1:9200
```

`local` profile 默认只监听回环地址；只有在同步设置独立随机 `JWT_SECRET`
并确认网络访问边界后，才应通过 `SERVER_ADDRESS` 扩大监听范围。生产环境不要
使用默认 JWT 密钥、数据库密码或本地宽松 Admin 模式。

## 启动方式

安装依赖并编译：

```powershell
mvn -DskipTests clean install
```

启动后端：

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
$env:DB_PASSWORD = "offerlab-local-db-change-me"
$env:REDIS_PASSWORD = "offerlab-local-redis-change-me"
mvn -pl community-bootstrap -am spring-boot:run
```

`-am` 会同时构建启动模块依赖的当前源码模块，避免只启动
`community-bootstrap` 时复用本地仓库中过期的 `*-SNAPSHOT.jar`。
`local` profile 默认关闭 Kafka 和 Elasticsearch；需要验证完整中间件链路时，
再按 `docs/middleware-local-runbook.md` 显式开启对应环境变量。

访问地址：

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

## 数据库说明

`db/init/*.sql` 仅用于新库初始化。已有数据库应优先审阅并按需手动执行 `db/migration/*.sql` 中的非破坏性增量脚本。

历史 migration 保持内容哈希不变；请按 `db/migration/README.md` 将已有库 schema
升级到当前版本，不要把旧的演示 migration 当作最终数据刷新入口。

如果你是刷新已有本地库的演示数据，请先备份数据库并完成当前 schema
migration，再从仓库根目录执行 V22 的本地专用入口。该脚本的失败回滚保护
依赖 MySQL 客户端在首个断言错误时终止批处理，因此只支持下面给出的
`--execute="SOURCE ..."` 调用；不要在交互式客户端中手工 `SOURCE`，也不要
使用 `--force`：

```powershell
mysql --skip-force --skip-reconnect -h 127.0.0.1 -P 3306 -u offerlab -p offerlab --execute="SOURCE db/demo/refresh-existing-local-demo.sql;"
```

该入口复用 `db/init/99_seed.sql` 的当前幂等演示数据，不修改历史 migration，
也不会被生产 Flyway 自动执行。个性化题单、进度和模拟面试始终绑定到专用
演示身份 `990000000000000001`，不会自动写入真实 `admin` 或任意活跃用户。
命令显式覆盖 option file 中可能存在的 `force` / 自动重连设置，避免断言失败
或连接中断后继续执行到提交语句。`99_seed.sql` 在 fresh-init 路径会自行关闭
并恢复 `autocommit`，因此新库演示数据也以单个事务写入。
预检或后置条件失败时，批处理会在 `COMMIT` 前终止，连接关闭后回滚事务；
请先处理命令输出中的身份、自然键或数据完整性冲突，再重新执行。
如果该身份已由 `db/local/seed_local_demo_admin.sql` 显式启用为
`demo.admin@offerlab.local`，刷新会保留现有邮箱、密码、状态、资料和管理员角色。

首次需要登录专用演示账号时，请打开一个全新的专用 MySQL 会话，不要复用
带有其他未提交事务的连接：

```powershell
mysql --skip-force --skip-reconnect -h 127.0.0.1 -P 3306 -u offerlab -p offerlab
```

然后在该会话中提供你自行生成的 60 位 bcrypt hash，再显式执行本地管理员脚本：

```sql
SET @enable_local_demo_admin_seed := 1;
SET @local_demo_admin_bcrypt_hash := '<60-character-bcrypt-hash>';
SOURCE db/local/seed_local_demo_admin.sql;
```

脚本只接受保留的 `demo.author` / `demo.admin` 身份，发现 UID、邮箱或软删除
身份冲突时会中止，不会覆盖其他本地用户。完成后可让验证脚本自动定位
确定性的题单拥有者，也可以用 `--admin-email` 强制检查指定账号：

```powershell
node scripts/verify-demo-question-data.mjs
```

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

当前 AI 主要用于从社区内容中提炼知识卡、摘要、标签建议和结构化线索。建议线上启用前补齐限流、成本统计、调用审计、敏感内容脱敏和更细的失败告警。

## 常用接口

| Method | Path | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/api/v1/auth/register` | 注册 | 否 |
| POST | `/api/v1/auth/login` | 登录 | 否 |
| GET | `/api/v1/users/me` | 当前用户 | 是 |
| PATCH | `/api/v1/users/me` | 修改资料 | 是 |
| PUT | `/api/v1/users/me/intent` | 修改学习偏好 | 是 |
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
| GET | `/api/v1/questions` | 知识卡列表 | 否 |
| GET | `/api/v1/questions/{id}` | 知识卡详情 | 否 |
| POST | `/api/v1/admin/posts/{postId}/extract-questions` | 从帖子提取知识卡 | 是，admin |
| GET | `/api/v1/admin/ai-tasks` | AI 结构化任务列表 | 是，admin |
| POST | `/api/v1/admin/ai-tasks/{id}/retry` | 重试 AI 结构化任务 | 是，admin |
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
