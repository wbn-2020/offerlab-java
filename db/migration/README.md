# OfferLab Database Migrations

The dated SQL files in this directory are the canonical migration sources.
`sync-flyway-resources.ps1` maps them to deterministic Flyway versions, copies
the byte-identical runtime resources, and writes `flyway-manifest.json` with a
SHA-256 checksum for every source. It also keeps the marked community seed block
in `db/init/99_seed.sql` content-aligned with
`20260712_demo_community_seed.sql`.

The directory contains 56 canonical migrations. The application automatically
scans the 52 production-safe migrations from
`classpath:db/flyway/core`. The four `demo_*` data seeds are tracked in
`classpath:db/flyway/demo`, but are deliberately excluded from application
startup so acceptance and production databases never receive demo content.

When demo seeds are applied intentionally, keep their history isolated from the
core readiness contract:

```powershell
$env:OFFERLAB_FLYWAY_LOCATIONS='classpath:db/flyway/demo'
$env:OFFERLAB_FLYWAY_TABLE='flyway_demo_schema_history'
```

## Existing schemas

Flyway uses baseline version `0`. The `dev` and local profiles allow a one-time
automatic baseline for a non-empty legacy schema, then execute every guarded
versioned migration so `flyway_schema_history` records the checksum of all 52 core files.
Acceptance and production keep `baseline-on-migrate` disabled by
default. For their first adoption:

1. Take and verify a full database backup.
2. Run `scripts/check-migration-safety.ps1`.
3. Run the uniqueness prechecks and stop on any duplicate row.
4. Set `OFFERLAB_FLYWAY_BASELINE_ON_MIGRATE=true` for exactly the first
   deployment.
5. Confirm `scripts/check-schema-readiness.mjs --json` reports 52 successful core migrations,
   no failed rows, no missing checksums, and the expected latest version.
6. Remove the one-time baseline override from later deployments.

Never use `flyway clean` for this project; configuration keeps it disabled.

## Editing workflow

After adding or editing a canonical SQL file:

```powershell
.\db\migration\sync-flyway-resources.ps1 -Write
.\scripts\check-migration-safety.ps1
node .\scripts\test-flyway-migration-lifecycle.mjs
```

Do not edit generated files below
`community-bootstrap/src/main/resources/db/flyway` directly. Checksum drift
causes the guard and Flyway validation to fail.

Do not run schema reset, table drop, truncate, or broad data updates against an
existing database. A future destructive migration needs a separate runbook,
dry-run evidence, backup and restore steps, a maintenance window, and explicit
production approval.
