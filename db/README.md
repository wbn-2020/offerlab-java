# OfferLab database scripts

Use the folders differently:

- `db/migration`: canonical additive sources for existing databases. The
  application applies the generated core Flyway resources automatically. Run a
  source file manually only under an explicit migration runbook, and never
  manually apply a version that Flyway will later try to execute.
- `db/init`: fresh local initialization scripts. These files may contain
  `DROP TABLE IF EXISTS` and must not be run against an existing database unless
  you intentionally want to rebuild it from scratch. Run
  `db/init/00_empty_database_guard.sql` first when applying the init folder; it
  aborts if the current schema already contains tables.
- The `local` Spring profile uses this fresh-init path and therefore disables
  Flyway by default. Use the `dev` profile for migration rehearsal against a
  legacy schema; production-like profiles require Flyway and must not load
  `db/init`.
- `db/init/14_collaboration.sql`, `db/init/15_incentive.sql`, and
  `db/init/16_database_integrity_hardening.sql` mirror the Stage 2-5 canonical
  schema and its integrity hardening so a fresh Docker database exposes the
  same collaboration, non-cash incentive, virtual benefit, and community-role
  structures as the Flyway migration path.
- `db/schema-ledger.md`: static acceptance ledger for recording which schema
  source and migration scripts were reviewed for a target environment.

## Fresh database only

The `db/init` folder is destructive by design. It is meant for a new local schema
or a throwaway demo database. Do not run an individual init file against a schema
that may contain user, post, notification, audit, or task data. If a full rebuild
is intentionally needed, record the target JDBC URL, the operator, and the reason
before running it, then run `00_empty_database_guard.sql` first and keep its
output with the change record.

## Existing database migrations

Use `db/migration` for existing environments. Before executing any migration:

1. Review the SQL and confirm the target schema name.
2. Run the safety scanner below and save the output.
3. For scripts that alter indexes or uniqueness rules, run the precheck queries
   in the script first and confirm the result set is empty or expected.
4. Record the backup point, maintenance window, operator, and rollback plan.
5. Deploy through Flyway and record success or failure. A manual execution must
   also record how the corresponding Flyway history row will be established.

Before running an existing-database migration, use:

```powershell
.\scripts\check-migration-safety.ps1
```

The check is intentionally conservative. It scans `db/migration` for destructive
DDL/DML patterns and fails fast when it sees statements such as `DROP TABLE`,
`TRUNCATE`, or broad `DELETE`/`UPDATE` statements without an obvious `WHERE`.

## Acceptance schema ledger

Before service-style acceptance, choose one database schema source and record it
in `db/schema-ledger.md`. For a shared or existing acceptance database, prefer the
reviewed migration path and record each applied script with a checksum or file
revision. Do not treat local `docker-compose.yml` plus `db/init` as proof that an
existing acceptance database is aligned.

## Testcontainers integration checks

The project also keeps selected real-middleware checks under `src/it/java`. They
are not part of ordinary `mvn test`; run them explicitly on a machine with Docker
available:

```powershell
cd C:\project\offerlab-java
mvn -P integration-tests verify
```

Current integration coverage starts with MySQL follow cursor pagination and Redis
feed inbox Lua behavior. Add new `*IT` classes for mapper, cache, Kafka outbox, or
Elasticsearch consistency bugs that cannot be proven by guard tests alone.
