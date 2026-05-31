# OfferLab Database Migrations

This folder contains non-destructive SQL for existing databases. `db/init/*`
is used for fresh local initialization only.

Run migration scripts manually after reviewing them. Do not batch-run the folder
blindly; the scripts are ordered by date, but each target environment may already
have a different subset applied.

Before execution:

- Run `scripts/check-migration-safety.ps1` from the repository root.
- Read the header and precheck section of the target script.
- For uniqueness/index migrations, execute the duplicate-data precheck first and
  stop if it returns rows.
- Record backup point, operator, maintenance window, and rollback plan.
- Run one migration at a time and save the console output.

Example manual execution after review:

```sql
SOURCE db/migration/20260524_ops_governance.sql;
```

Do not run schema reset, table drop, truncate, or broad data update commands
against production data from this folder. If a future change truly needs a
destructive operation, create a separate runbook with dry-run evidence, affected
tables, backup/restore procedure, and explicit production approval.
