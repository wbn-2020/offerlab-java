# OfferLab database scripts

Use the folders differently:

- `db/migration`: additive scripts for existing databases. Review and run these
  manually in the target environment.
- `db/init`: fresh local initialization scripts. These files may contain
  `DROP TABLE IF EXISTS` and must not be run against an existing database unless
  you intentionally want to rebuild it from scratch. Run
  `db/init/00_empty_database_guard.sql` first when applying the init folder; it
  aborts if the current schema already contains tables.

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
5. Execute one script at a time and record success or failure.

Before running an existing-database migration, use:

```powershell
.\scripts\check-migration-safety.ps1
```

The check is intentionally conservative. It scans `db/migration` for destructive
DDL/DML patterns and fails fast when it sees statements such as `DROP TABLE`,
`TRUNCATE`, or broad `DELETE`/`UPDATE` statements without an obvious `WHERE`.

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
