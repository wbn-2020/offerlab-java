# OfferLab schema ledger

This file records the static schema source for service-style acceptance. It is a
manual ledger, not an automatic migration runner.

## Source boundary

- `db/init` is for fresh local or disposable demo schemas only. It contains
  destructive guards and table creation scripts.
- `db/migration` is for existing databases. Review and execute scripts one at a
  time, then record the operator, timestamp, database, script name, checksum or
  reviewed file revision, and result.
- A single acceptance database must use one chosen source path. Do not mix an
  already initialized schema with selected init files.

## Current static baseline

| Area | Fresh init source | Existing DB migration source | Notes |
|---|---|---|---|
| Outbox claim lease | `db/init/06_infra.sql` | `db/migration/20260530_outbox_claim_lock.sql` | Adds lock owner and lease columns. |
| Outbox claim ordering index | `db/init/06_infra.sql` | `db/migration/20260706_outbox_static_acceptance_indexes.sql` | Covers `(msg_status,next_retry_time,create_time)` for due claim ordering. |
| Operation curation | Not in init baseline | `db/migration/20260703_operation_curation.sql` | Topic supports `DRAFT/PREVIEW/PUBLISHED/OFFLINE/ARCHIVED`; slot supports `DRAFT/PREVIEW/PUBLISHED/OFFLINE`. |
| V3 home featured slot | Not in init baseline | `db/migration/20260705_v3_home_featured_slot.sql` | Requires explicit ledger entry before shared acceptance. |

## Soft-delete unique key policy

Existing tables include unique keys such as `(business_key,is_deleted)`. Do not
retrofit them blindly in shared databases. For new tables or future migrations,
prefer an active-row guard such as a generated `active_guard` column or a delete
token strategy that only enforces uniqueness for active rows. Existing-table
changes need a duplicate-data precheck, restore/recreate policy, and rollback
plan before execution.

## Status field policy

Operation topic status values are `DRAFT`, `PREVIEW`, `PUBLISHED`, `OFFLINE`,
and `ARCHIVED`. Operation slot status values are `DRAFT`, `PREVIEW`,
`PUBLISHED`, and `OFFLINE`; slots do not currently expose an archive action.
Database CHECK constraints are not added in this static repair because target
MySQL compatibility and existing data quality must be confirmed first.
