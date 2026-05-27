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
| Kafka | `spring.kafka.bootstrap-servers` / `KAFKA_BROKERS` | `localhost:9092` |
| Elasticsearch | `offerlab.elasticsearch.url` / `ELASTICSEARCH_URL` | `http://127.0.0.1:9200` |
| MySQL | `spring.datasource.url` | `jdbc:mysql://localhost:3306/offerlab` |

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

OfferLab backend:

```powershell
Set-Location C:\project\offerlab-java
mvn -pl community-bootstrap spring-boot:run
```

## Health checks

These checks are read-only:

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
