# OfferLab local middleware runbook

This note records the current Windows middleware layout for OfferLab V2. It is
documentation only: do not turn these commands into startup tasks, services,
PATH edits, registry entries, or Git hooks without explicit confirmation.

## Project configuration

OfferLab reads its local middleware defaults from:

- `community-bootstrap/src/main/resources/application.yml`
- `community-bootstrap/src/main/resources/application-local.yml`
- `community-bootstrap/src/main/resources/application-dev.yml`

Effective `local` profile defaults:

| Dependency | App setting | Default |
| --- | --- | --- |
| Redis | `spring.data.redis.host`, `spring.data.redis.port` | `localhost:6379` |
| Redis Pub/Sub | `offerlab.redis.pubsub-enabled` | disabled |
| Kafka | `offerlab.kafka.enabled`, `spring.kafka.bootstrap-servers` / `KAFKA_BROKERS` | disabled; endpoint defaults to `localhost:9092` when enabled |
| Kafka Admin | `spring.kafka.admin.auto-create` | disabled with the same `OFFERLAB_KAFKA_ENABLED` switch |
| Elasticsearch | `offerlab.elasticsearch.enabled`, `offerlab.elasticsearch.url` / `ELASTICSEARCH_URL` | disabled; endpoint defaults to `http://127.0.0.1:9200` when enabled |
| Realtime WebSocket | `offerlab.realtime.websocket-enabled` | disabled |
| MySQL | `spring.datasource.url` | `jdbc:mysql://localhost:3306/offerlab` |

The `dev` profile keeps Kafka and Elasticsearch enabled for the existing
full-middleware workflow. Prefer the `local` profile for a lightweight run.
Enable optional middleware explicitly in the foreground PowerShell session only
when that integration is under test:

```powershell
$env:OFFERLAB_KAFKA_ENABLED = "true"
$env:KAFKA_BROKERS = "localhost:9092"
$env:ELASTICSEARCH_ENABLED = "true"
$env:ELASTICSEARCH_URL = "http://127.0.0.1:9200"
```

Do not persist these as machine/user environment variables unless that exact
system mutation has been reviewed and confirmed.

## Local paths

Middleware is installed under `C:\codeware`:

| Dependency | Path | Notes |
| --- | --- | --- |
| Redis 6.2.18 | `C:\codeware\redis6.2.18` | Includes `redis-server.exe`, `redis-cli.exe`, and `redis.conf`. |
| Elasticsearch 8.14.3 | `C:\codeware\elasticsearch-8.14.3` | Uses bundled JDK and listens on `127.0.0.1:9200` when config is aligned. |
| Kafka 3.6.2 | `C:\codeware\kafka_2.13-3.6.2` | Project-owned OfferLab KRaft profile is `scripts\kafka\offerlab-server-local.properties`. |
| Kafka data | `C:\codeware\kafka-data` | OfferLab local profile uses independent `offerlab-kraft-combined-logs` and `offerlab-metadata` directories. |
| Kafka logs | `C:\codeware\kafka-logs` | Local process logs directory. |

## Manual startup examples

Run these in foreground PowerShell windows when you need a local demo. They are
intentionally manual and session-scoped.

Redis:

```powershell
Set-Location C:\project\offerlab-java
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local-redis.ps1
```

Kafka:

```powershell
Set-Location C:\project\offerlab-java
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local-kafka.ps1
```

Elasticsearch:

```powershell
Set-Location C:\project\offerlab-java
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local-elasticsearch.ps1
```

If `config\elasticsearch.yml` still points `path.data` or `path.logs` at an old
folder, start Elasticsearch with explicit session-scoped settings instead of
editing system configuration:

```powershell
Set-Location C:\project\offerlab-java
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local-elasticsearch.ps1
```

`start-local-elasticsearch.ps1` clears `CLASSPATH` and `JAVA_TOOL_OPTIONS` only
inside the foreground PowerShell process before launching Elasticsearch, then
restores the previous values when the process exits. It does not persist
environment variables.

`start-local-kafka.ps1` uses
`scripts\kafka\offerlab-server-local.properties`, checks ports `9092/9093`, and
refuses to start when the configured storage directories are missing,
unformatted, or contain read-only `*.checkpoint.deleted` files. It does not run
`kafka-storage.bat format`, delete data, or change file attributes automatically.
For a brand-new clean local Kafka directory, review the configured absolute
paths first, then format it manually after explicit confirmation for that exact
operation.

OfferLab backend:

```powershell
Set-Location C:\project\offerlab-java
$env:SPRING_PROFILES_ACTIVE = "local"
$env:DB_PASSWORD = "offerlab-local-db-change-me"
$env:REDIS_PASSWORD = "offerlab-local-redis-change-me"
$env:OFFERLAB_KAFKA_ENABLED = "true"
$env:KAFKA_BROKERS = "localhost:9092"
$env:ELASTICSEARCH_ENABLED = "true"
$env:ELASTICSEARCH_URL = "http://127.0.0.1:9200"
mvn -pl community-bootstrap -am spring-boot:run
```

Use `-am` so Maven builds the current reactor modules that `community-bootstrap`
depends on. Running only `-pl community-bootstrap` can reuse older local
`*-SNAPSHOT.jar` files and make routes that exist in source disappear at
runtime.

OfferLab frontend:

```powershell
Set-Location C:\project\offerlab-vue
npm run dev
```

Start the frontend after the backend readiness endpoint responds. If the
frontend is opened first, the Vite proxy returns a throttled HTTP 503 JSON
response for `/api/**` instead of repeatedly flooding the terminal with raw
`ECONNREFUSED` messages.

## Health checks

These checks are read-only:

Backend repo preflight. This checks expected `C:\codeware` paths, Kafka/ES
config hints, TCP ports, Elasticsearch health/indexes, Kafka topic list, and the
`offerlab-feed-fanout` consumer group without writing app data. It also detects
read-only Kafka `*.checkpoint.deleted` files that can reproduce broker startup
`AccessDeniedException`; the check is read-only and does not change file
attributes or delete broker data:

```powershell
Set-Location C:\project\offerlab-java
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-local-middleware.ps1
```

Strict local verification should be used only when you expect the app and core
middleware to be ready. It runs migration safety, schema readiness, middleware
readiness, and public API probes:

```powershell
Set-Location C:\project\offerlab-java
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1 -StrictMiddleware
```

If this fails at schema readiness, do not keep retrying the app. Review the
missing migrations first.

```powershell
.\redis-cli.exe -h 127.0.0.1 -p 6379 ping
```

```powershell
Invoke-WebRequest http://127.0.0.1:9200 -UseBasicParsing
```

```powershell
Set-Location C:\codeware\kafka_2.13-3.6.2
.\bin\windows\kafka-topics.bat --bootstrap-server localhost:9092 --list
```

```powershell
Invoke-WebRequest http://localhost:8080/swagger-ui.html -UseBasicParsing
```

Application readiness and middleware probe without creating users, posts,
notifications, retry rows, or report files:

```powershell
Set-Location C:\project\offerlab-java
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-offerlab.ps1 -ReadOnlyProbe -NoWriteReport
```

Full smoke creates synthetic users, posts, comments, reports, notifications,
outbox/search/notification observations, and a JSON report. Run it only against
local/test data:

```powershell
Set-Location C:\project\offerlab-java
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-offerlab.ps1 -ReportPath C:\codeware\offerlab-smoke-report.json
```

## Acceptance criteria

- `/api/v1/health/readiness` returns `UP` only when required runtime components
  are healthy and schema readiness is complete. It returns `DEGRADED` when an
  enabled dependency is unavailable or when schema is `BLOCKED_BY_SCHEMA`.
- `/api/v1/health/readiness/strict` is the sign-off endpoint for CI and local
  demos. It returns HTTP 200 only when overall readiness is `UP`, and HTTP 503
  when any core component is degraded, disabled, blocked by schema, down, or
  unknown.
- Kafka/Elasticsearch disabled-by-config states must be explicit in readiness
  and the Ops UI. They should not look like fully available search, event,
  notification, or fanout chains.
- `node .\scripts\check-schema-readiness.mjs --json` returns `ready=true`
  before a full publish/search/admin demo is signed off.
- Elasticsearch `/_cluster/health` returns `green` or `yellow`, and both
  `post_idx` and `question_idx` exist.
- Kafka topic `post.published` exists.
- Kafka consumer group `offerlab-feed-fanout` has a row for `post.published`;
  after the full smoke publishes a post, lag should return to `0`.
- Ops endpoints return successfully:
  `/api/v1/ops/search-index-retry-tasks?limit=10` and
  `/api/v1/ops/notification-retry-tasks?limit=10`.
- Batch retry/replay checks are scoped to retry rows created by the app; do not
  reset, truncate, or broadly delete Kafka, Elasticsearch, Redis, or MySQL data.

## Fault injection

Use only manual, non-destructive checks. For example, close a foreground Kafka
or Elasticsearch terminal window, rerun the read-only probe, restart the
dependency, then confirm readiness returns to `UP` and retry rows can be
previewed/replayed from the Ops UI. Do not add scripts that kill processes,
delete data directories, format Kafka storage, reset indexes, or truncate tables
without a separate reviewed command and explicit confirmation.

## Current config risks

- `C:\codeware\elasticsearch-8.14.3\config\elasticsearch.yml` should keep
  `path.data` and `path.logs` under `C:\codeware\elasticsearch-8.14.3`. The
  preflight script warns if it still sees old `comware` or `commonware` paths.
  If that warning appears, fix it manually or approve a targeted config edit
  before using this installation for OfferLab.
- Kafka's OfferLab KRaft config uses
  `C:/codeware/kafka-data/offerlab-kraft-combined-logs` plus
  `C:/codeware/kafka-data/offerlab-metadata`, which keeps OfferLab demo data
  separate from other Kafka experiments.
- The OfferLab local Kafka profile sets `log.retention.ms=-1` and
  `log.retention.bytes=-1` to avoid Windows demo failures caused by retention
  deleting locked `.timeindex` files. Use a throwaway formatted data directory
  when you need to test retention behavior.
- If the preflight reports read-only files under
  `C:\codeware\kafka-data\kraft-combined-logs\__cluster_metadata-0\*.checkpoint.deleted`,
  Kafka may fail during startup with `AccessDeniedException`. Do not delete
  those files or change attributes from an automated script. First confirm Kafka
  is stopped, list the affected absolute paths, choose either a targeted
  attribute fix or a clean test data directory, and obtain explicit confirmation
  for that exact operation.
- Redis is bound to localhost on port `6379`, has `daemonize no`, and has
  `appendonly no` in the current local config. This is fine for development, but
  not for durable production-like testing.
- Run `.\scripts\check-local-middleware.ps1` from the backend repo when local
  script execution policy allows it, or inspect the same config files manually,
  for a read-only check of the expected `C:\codeware` Redis, Elasticsearch, and
  Kafka paths.
- The app has MySQL credentials in local development config. Keep any real
  credentials in local-only overrides or environment variables and do not commit
  secrets.

## Database migration note

For an existing local database, always start with read-only checks:

```powershell
Set-Location C:\project\offerlab-java
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-migration-safety.ps1
node .\scripts\check-schema-readiness.mjs --json
```

The current community upgrade depends on these reviewed incremental migrations:

```text
db/migration/20260608_community_topics.sql
db/migration/20260608_review_queue.sql
db/migration/20260608_tag_governance.sql
db/migration/20260605_ai_extract_task_metrics.sql
db/migration/20260608_mock_interview_ai_review_transparency.sql
```

They create community topic tables, the unified review queue, tag governance
columns/indexes, AI extraction task metrics columns, and mock-interview AI review transparency columns. These are
intended to be non-destructive, but `CREATE TABLE`, `ALTER TABLE`, and temporary
procedure DDL are persistent database changes. Run them only after reviewing the
target database and receiving explicit confirmation for the exact operation.

Suggested manual order after confirmation:

```sql
SOURCE db/migration/20260608_community_topics.sql;
SOURCE db/migration/20260608_review_queue.sql;
SOURCE db/migration/20260608_tag_governance.sql;
SOURCE db/migration/20260605_ai_extract_task_metrics.sql;
SOURCE db/migration/20260608_mock_interview_ai_review_transparency.sql;
```

After applying them, rerun:

```powershell
node .\scripts\check-schema-readiness.mjs --json
```

Do not use the fresh-init scripts against an existing database unless you intend
to rebuild the database from scratch.
