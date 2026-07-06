# OfferLab acceptance runbook

This runbook is a static handoff note for service-style acceptance. It does not
authorize starting services from the repository.

## Environment boundary

- Use `SPRING_PROFILES_ACTIVE=acceptance` or an explicitly reviewed production-like profile.
- Do not rely on the default `dev` profile for acceptance.
- Do not reuse `docker-compose.yml` as an acceptance, staging, or production template.
- Do not load `db/init` into an existing database. Pick one schema source and record it.
- Keep real secrets outside the repository. Use environment variables or a secret manager.

## Required env freeze

Before service-style acceptance, record the effective values or secret references
for DB, Redis, Kafka, Elasticsearch, JWT, CORS, admin access, demo/test data, and
feature degradation toggles. The template starts from `.env.acceptance.example`.

## Safe defaults

- `application-acceptance.yml` disables Kafka, Elasticsearch, Redis pub/sub, and
  WebSocket realtime by default unless the corresponding environment variables
  explicitly enable them.
- Swagger and OpenAPI UI are disabled.
- Admin local-open bootstrap is disabled.
- JWT and CORS have no local fallback values.

## Database source

For a fresh disposable local schema, use `db/init` only after the empty-database
guard. For an existing or shared acceptance schema, use reviewed scripts from
`db/migration` and record the ledger in `db/schema-ledger.md`.

## Service acceptance items

The static repair does not prove runtime behavior. Service-style acceptance still
needs to confirm:

- fallback/demo/test data is not written into formal public paths;
- deleted or offline content is hidden from feed, search, topics, and knowledge views;
- outbox, feed, search, and retry queues behave under backlog;
- topic publish, offline, rollback, and archive actions are idempotent under repeated submits;
- DB, Redis, Kafka, and Elasticsearch endpoints are the intended acceptance services;
- browser UI paths render and preserve context across navigation.
