import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'

const migration = readFileSync(new URL('../db/migration/20260617_domain_moderators.sql', import.meta.url), 'utf8')
const readiness = readFileSync(new URL('./check-schema-readiness.mjs', import.meta.url), 'utf8')
const runtimeReadiness = readFileSync(
  new URL('../community-infrastructure/src/main/java/com/offerlab/community/infra/db/MigrationCheckService.java', import.meta.url),
  'utf8',
)

assert.match(
  migration,
  /v20260617_add_domain_moderator_pk_if_missing/,
  'domain moderator migration must define a guarded primary-key repair procedure',
)
assert.match(
  migration,
  /ALTER TABLE t_domain_moderator ADD PRIMARY KEY \(id\)/,
  'domain moderator migration must add PRIMARY(id) when id already exists without a primary key',
)
assert.match(
  migration,
  /HAVING COUNT\(\*\) > 1/,
  'primary-key repair must check duplicate id values before adding the key',
)
assert.match(
  migration,
  /v_duplicate_id_count = 0/,
  'primary-key repair must only run when duplicate ids are absent',
)
assert.match(readiness, /primaryKeys\('t_domain_moderator', \['id'\]\)/, 'schema script must check PRIMARY(id)')
assert.match(runtimeReadiness, /primaryKeyExists\("t_domain_moderator", "id"\)/, 'runtime readiness must check PRIMARY(id)')

console.log('Domain moderator migration readiness guard passed.')
