# OfferLab local middleware runbook

This note records the current Windows middleware layout for OfferLab V2. It is
documentation only: do not turn these commands into startup tasks, services,
PATH edits, registry entries, or Git hooks without explicit confirmation.

## Project configuration

OfferLab reads its local middleware defaults from:

- `community-bootstrap/src/main/resources/application.yml`
- `community-bootstrap/src/main/resources/application-dev.yml`

Effective local defaults:

| Dependency | App setting | Default |
| --- | --- | --- |
| Redis | `spring.data.redis.host`, `spring.data.redis.port` | `localhost:6379` |
| Kafka | `offerlab.kafka.enabled`, `spring.kafka.bootstrap-servers` / `KAFKA_BROKERS` | disabled in `dev`; `localhost:9092` when enabled |
| Elasticsearch | `offerlab.elasticsearch.enabled`, `offerlab.elasticsearch.url` / `ELASTICSEARCH_URL` | disabled in `dev`; `http://127.0.0.1:9200` when enabled |
| Realtime WebSocket | `offerlab.realtime.websocket-enabled` | disabled in `dev` |
| MySQL | `spring.datasource.url` | `jdbc:mysql://localhost:3306/offerlab` |

For a full local Kafka and Elasticsearch chain, set these variables only in the
foreground PowerShell session that starts the backend:

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
| Kafka 3.6.2 | `C:\codeware\kafka_2.13-3.6.2` | KRaft config for OfferLab is `config\kraft\offerlab-server.properties`. |
| Kafka data | `C:\codeware\kafka-data` | Local broker data/log directory. |
| Kafka logs | `C:\codeware\kafka-logs` | Local process logs directory. |

## Manual startup examples

Run these in foreground PowerShell windows when you need a local demo. They are
intentionally manual and session-scoped.

Redis:

```powershell
Set-Location C:\codeware\redis6.2.18
.\redis-server.exe .\redis.conf
```

Kafka:

```powershell
Set-Location C:\codeware\kafka_2.13-3.6.2
.\bin\windows\kafka-server-start.bat .\config\kraft\offerlab-server.properties
```

Elasticsearch:

```powershell
Set-Location C:\codeware\elasticsearch-8.14.3
.\bin\elasticsearch.bat
```

If `config\elasticsearch.yml` still points `path.data` or `path.logs` at an old
folder, start Elasticsearch with explicit session-scoped settings instead of
editing system configuration:

```powershell
Set-Location C:\codeware\elasticsearch-8.14.3
.\bin\elasticsearch.bat -Epath.data=C:\codeware\elasticsearch-8.14.3\data -Epath.logs=C:\codeware\elasticsearch-8.14.3\logs
```

OfferLab backend:

```powershell
Set-Location C:\project\offerlab-java
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

## Health checks

These checks are read-only:

Backend repo preflight. This checks expected `C:\codeware` paths, Kafka/ES
config hints, TCP ports, Elasticsearch health/indexes, Kafka topic list, and the
`offerlab-feed-fanout` consumer group without writing app data:

```powershell
Set-Location C:\project\offerlab-java
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-local-middleware.ps1
```

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

- `/api/v1/health/readiness` returns `UP` when Kafka/Elasticsearch are disabled
  for `dev`, or when they are enabled and reachable. It returns `DEGRADED` when
  an enabled dependency is unavailable or unconfigured.
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

- `C:\codeware\elasticsearch-8.14.3\config\elasticsearch.yml` currently points
  `path.data` and `path.logs` at `C:\my-claude\comware\elasticsearch-8.14.3`.
  That can make Elasticsearch reuse the wrong data directory. Fix it manually
  or approve a targeted config edit before using this installation for OfferLab.
- Kafka's OfferLab KRaft config uses
  `C:/codeware/kafka-data/kraft-combined-logs`, which matches the local
  middleware folder.
- Redis is bound to localhost on port `6379`, has `daemonize no`, and has
  `appendonly no` in the current local config. This is fine for development, but
  not for durable production-like testing.
- Run `powershell -ExecutionPolicy Bypass -File .\scripts\check-local-middleware.ps1`
  from the backend repo for a read-only check of the expected `C:\codeware`
  Redis, Elasticsearch, and Kafka paths.
- The app has MySQL credentials in local development config. Keep any real
  credentials in local-only overrides or environment variables and do not commit
  secrets.

## Database migration note

The existing-database governance migration is:

```text
db/migration/20260524_ops_governance.sql
```

Run it manually after reviewing the SQL in your target database. Do not use the
fresh-init scripts against an existing database unless you intend to rebuild the
database from scratch.
