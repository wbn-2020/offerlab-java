# OfferLab Database Migrations

The dated SQL files in this directory are the canonical migration sources.
`sync-flyway-resources.ps1` maps them to deterministic Flyway versions, copies
the byte-identical runtime resources, and writes `flyway-manifest.json` with a
SHA-256 checksum for every source. It also keeps the marked community seed block
in `db/init/99_seed.sql` content-aligned with
`20260712_demo_community_seed.sql`.

`flyway-manifest.json` is the source of truth for migration counts, versions,
checksums, and source-to-resource mappings. Existing mappings are immutable:
adding another file on the same date receives the next free sequence instead of
renumbering published migrations. The application automatically scans the
production-safe migrations from `classpath:db/flyway/core`. The seven demo migrations
are tracked in
`classpath:db/flyway/demo`, but are deliberately excluded from application
startup. A startup guard rejects demo locations and the demo history table in
the `prod`, `production`, and `acceptance` profiles, and requires the core
classpath location to be the only configured migration source.

When demo seeds are applied intentionally, keep their history isolated from the
core readiness contract:

```powershell
$env:OFFERLAB_FLYWAY_LOCATIONS='classpath:db/flyway/demo'
$env:OFFERLAB_FLYWAY_TABLE='flyway_demo_schema_history'
```

## Existing schemas

Flyway uses baseline version `0`. The `dev` profile allows a one-time automatic
baseline for a non-empty legacy schema; the local profile defaults to the
Flyway-disabled fresh-init mode unless explicitly overridden. Acceptance and production
require `baseline-on-migrate=false`, enforced before the application context
starts. For their first adoption:

1. Take and verify a full database backup.
2. Run `scripts/check-migration-safety.ps1`.
3. Run the uniqueness prechecks and stop on any duplicate row.
4. Establish baseline version `0` before application deployment with an
   independently reviewed Flyway baseline operation and retain its audit output.
5. Deploy with `baseline-on-migrate=false`.
6. Confirm `scripts/check-schema-readiness.mjs --json` reports every manifest
   core migration as successful, with no failed rows, missing checksums,
   mismatches, or unexpected versions.

Never use `flyway clean` for this project; configuration keeps it disabled.

## Editing workflow

After adding or editing a canonical SQL file:

```powershell
.\db\migration\sync-flyway-resources.ps1 -Write
.\scripts\check-migration-safety.ps1
node .\scripts\test-flyway-migration-lifecycle.mjs
```

Once a source appears in `flyway-manifest.json`, ordinary synchronization treats
its bytes and Flyway checksum as immutable. Rewriting a known-unreleased
migration requires the explicit `-AllowTrackedMigrationRewrite` switch and a
recorded reason. Never use that switch for a migration applied to any database.

Do not edit generated files below
`community-bootstrap/src/main/resources/db/flyway` directly. Checksum drift
causes the guard and Flyway validation to fail.

Do not run schema reset, table drop, truncate, or broad data updates against an
existing database. A future destructive migration needs a separate runbook,
dry-run evidence, backup and restore steps, a maintenance window, and explicit
production approval.
