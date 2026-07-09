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
| Operation curation | `db/init/13_recent_features.sql` | `db/migration/20260703_operation_curation.sql` + `db/migration/20260708_operation_soft_delete_unique_guard.sql` | Topic supports `DRAFT/PREVIEW/PUBLISHED/OFFLINE/ARCHIVED`; slot supports `DRAFT/PREVIEW/PUBLISHED/OFFLINE`; soft-delete unique guards use `active_guard` on curation and slot items. |
| V3 home featured slot | `db/init/13_recent_features.sql` | `db/migration/20260705_v3_home_featured_slot.sql` | Adds slot snapshot columns `current_version`, `published_snapshot_json`, and `rollback_snapshot_json`. |
| New creator support stats | `db/init/05_analytics.sql` | `db/migration/20260624_new_creator_support_stats.sql` | Records recommend-feed support delivery and hit counts per response page. |
| Creator representative posts | `db/init/13_recent_features.sql` | `db/migration/20260703_creator_representative_post.sql` | Stores manually curated representative post links for creator profiles. |
| Contact requests | `db/init/03_interaction.sql` | `db/migration/20260707_contact_request.sql` | Existing DB migration must include `idx_contact_request_requester_day (requester_uid,is_deleted,create_time)` for daily-limit counts. |
| Contact request settings | `db/init/08_privacy.sql` | `db/migration/20260707_contact_request_settings.sql` | Adds contact-request policy, accept flag, daily limit, and policy index. |
| Favorite folders | `db/init/03_interaction.sql` | `db/migration/20260707_favorite_folder.sql` | Existing DB migration includes dry-run prechecks, folder backfill, duplicate default repair, and active-default guard. |
| Discussion follows | `db/init/03_interaction.sql` | `db/migration/20260707_discussion_follow.sql` | Adds discussion follow state, notification cursors, and full `(uid,post_id)` duplicate precheck before the unique key. |
| Public read indexes | `db/init` baseline review required | `db/migration/20260708_public_read_indexes.sql` | Adds read-heavy public listing and detail indexes; confirm against production query plans before execution. |
| Retry task claim indexes | `db/init/04_notification.sql`, `db/init/06_infra.sql`, `db/init/10_question.sql` | `db/migration/20260709_retry_task_claim_indexes.sql` | Idempotently ensures notification, search-index, and question-index retry claim indexes for existing databases; some fresh init baselines already include equivalent indexes. |

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
Contact request status values are owned by `ContactRequestService`:
`PENDING`, `ACCEPTED`, `REJECTED`, `IGNORED`, `REPORTED`, `CANCELLED`, and
`EXPIRED`. Contact request policy values are owned by
`ContactRequestSettingsService`: `all`, `following`, `mutual`, and `off`.
Database CHECK constraints are not added in this static repair because target
MySQL compatibility and existing data quality must be confirmed first. The
20260707 contact request migrations add column comments as DB-level dictionary
documentation and keep enforcement in the application dictionaries.
