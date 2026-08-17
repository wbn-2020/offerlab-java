# Post Content Environment Governance Runbook

Status: prepared only. None of the SQL or operational steps in this directory
were executed on August 14, 2026.

## Contract

- Allowed stored values: `COMMUNITY`, `TEST`, `INTERNAL`, `UNCLASSIFIED`.
- The schema default is `UNCLASSIFIED`.
- Only `COMMUNITY` is eligible for public reads and Elasticsearch.
- Missing, unknown, or unreadable values fail closed.
- Text keywords are review signals only. They never classify or delete a post.
- Post `741177956554252288` is a pending review candidate. This package does not
  assign it an environment or delete it.

## Required Evidence

Before any write, record:

1. Environment name, database server identity, schema name, deployment SHA, and
   maintenance-window approval.
2. A verified full database backup and restore check with owner, checksum,
   location, RPO, and RTO.
3. Output of `01-dry-run.sql`, including total counts and the SHA-256 of the
   exported candidate file.
4. A completed `approval-template.csv`. `COMMUNITY`, `TEST`, and `INTERNAL`
   decisions require two independent approvers. Uncertain rows stay
   `UNCLASSIFIED`.
5. Expected update count, expected count by old/new value, and the governance
   batch ID used by the apply and rollback scripts. The materialized apply
   script must set `@expected_approved_count` to that exact value and every
   inserted decision must be `APPROVED`.

## Controlled Execution Order

1. Drain public traffic or enter the approved maintenance window.
2. Verify the full database backup and restore evidence.
3. Apply Flyway migration `V20260814.01__post_content_environment.sql`.
4. Separately review and execute `00-provision-backup-table.sql`, then record
   its schema and execution evidence. The apply script will fail before its
   transaction when that exact backup table is absent.
5. Run `01-dry-run.sql` read-only and compare its counts with the approved file.
6. Materialize `02-apply-approved-template.sql` with only approved exact IDs.
7. Run the materialized script first with its final `ROLLBACK`. Its SQL
   preflight assertions stop before writes when the CSV is incomplete, stale,
   self-approved, unapproved, partially classified, or differs from the exact
   expected count. Its post-write assertions stop before commit when backup,
   update, or verification counts differ, or historical `UNCLASSIFIED` is not
   zero. If a post-write assertion fails, issue `ROLLBACK` in the same session
   immediately and archive the failure output.
8. Replace the final `ROLLBACK` with `COMMIT` only in the reviewed execution
   copy, execute once, and archive the SQL plus output.
9. Deploy the application version that requires `COMMUNITY` in every public
   read. Do not reopen traffic on an older application build.
10. Call the audited exact-ID endpoint
    `POST /api/v1/admin/content-environment/cache-invalidation` with the complete
    approved ID list, a non-empty governance remark, and confirmation phrase
    `CONFIRM`. Archive the operation ID, request ID set, response counts, and
    the matching `CONTENT_ENV_CACHE_INVALIDATE` audit records. The endpoint
    invalidates detail/raw/counter and company-prep keys, increments cache
    generations, broadcasts L1 invalidation, removes exact non-community IDs
    from global and author feeds, and cursor-scans `feed:inbox:*` before exact
    `ZREM`. The scan is bounded by
    `offerlab.feed.required-invalidation.max-inbox-keys` and fails closed when
    the bound or Redis observability is unavailable. It never uses `KEYS`,
    wildcard deletion, or `FLUSHDB`.
11. If step 10 reports failure after `STARTED`, do not change the approved ID
    set. Correct the dependency or limit issue and retry the same exact request.
    The operations are exact and idempotent, but the sequence is intentionally
    not represented as atomic; archive both attempts and require a final
    completed audit record before continuing.
12. Start the authorized post-index rebuild through
    `POST /api/v1/search/admin/rebuild`. Record task ID and checkpoint.
13. Start the audited question-index rebuild through
    `POST /api/v1/admin/questions/rebuild-index-task`. Question documents are
    derived from source posts, so this rebuild is mandatory after historical
    post environments change. Record its task ID, index name, indexed, failed,
    and total counts.
14. Require both rebuild tasks to reach status `SUCCEEDED`, each with failed
    count `0`; require post and question retry backlogs `0` and search readiness
    `UP`. A missing or wrong `contentEnvironment` keeps affected documents
    fail-closed.
15. Regenerate and verify `/api/v1/seo/sitemap.xml`, then run API and real
    browser checks across search, feed, tags, topics, user pages,
    recommendations, statistics, and SEO before reopening traffic.

## Automatic Stop Conditions

Stop without committing or reopening traffic when:

- candidate count or per-environment count differs from approval;
- an approved ID is missing, duplicated, deleted, or has a different old value;
- any decision is blank, unknown, self-approved, or lacks evidence;
- backup insertion count differs from the exact update count;
- an updated row is not one of the approved IDs;
- cache invalidation is partial, exceeds its bounded scan, lacks a completed
  audit record, or is otherwise unobservable;
- either post or question index rebuild is failed, incomplete, not
  `SUCCEEDED`, or reports any failed document;
- readiness is not `UP`;
- any public surface returns `TEST`, `INTERNAL`, `UNCLASSIFIED`, null, or an
  unknown environment.

## Rollback

Use `03-rollback-template.sql` with the same batch ID. Rollback restores exact
old values except that a row confirmed as `TEST` or `INTERNAL` remains
fail-closed and is never restored to `COMMUNITY`. Keep the new column and
migration in place; do not downgrade to an application version that ignores the
environment contract. Repeat cache invalidation, full index rebuild, readiness,
SEO, API, and browser verification after rollback.
