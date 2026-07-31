import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')
const migrationSource = 'db/migration/20260726_query_path_indexes.sql'
const migration = readFileSync(resolve(root, migrationSource), 'utf8')
const manifest = JSON.parse(
  readFileSync(resolve(root, 'db/migration/flyway-manifest.json'), 'utf8'),
)

const expectedIndexes = [
  {
    table: 't_int_content_suggestion',
    name: 'idx_content_suggestion_author_resolution_time',
    columns: 'post_author_id, resolution, update_time, id',
  },
  {
    table: 't_post_knowledge_relation',
    name: 'idx_post_knowledge_relation_review_time',
    columns: 'review_status, is_deleted, update_time, id',
  },
  {
    table: 't_int_user_revisit_item',
    name: 'idx_revisit_user_source_status_time',
    columns: 'uid, source_type, revisit_status, update_time, id',
  },
]
const freshInit = readFileSync(resolve(root, 'db/init/29_query_path_indexes.sql'), 'utf8')

assert.equal(
  freshInit,
  migration,
  'fresh-init query-path indexes must mirror the canonical migration',
)

assert.match(
  migration,
  /FROM\s+information_schema\.STATISTICS[\s\S]*?TABLE_SCHEMA\s*=\s*DATABASE\(\)[\s\S]*?TABLE_NAME\s*=\s*p_table_name[\s\S]*?INDEX_NAME\s*=\s*p_index_name/i,
)
assert.match(migration, /AND\s+NOT EXISTS\s*\(/i)
assert.match(migration, /PREPARE stmt FROM @ddl[\s\S]*?EXECUTE stmt/i)
assert.doesNotMatch(migration, /\bDROP\s+INDEX\b/i)

for (const expected of expectedIndexes) {
  const escapedColumns = expected.columns.replaceAll(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const callPattern = new RegExp(
    `CALL\\s+v20260726_query_path_add_index_if_missing\\s*\\(\\s*` +
      `'${expected.table}'\\s*,\\s*'${expected.name}'\\s*,\\s*` +
      `'ALTER TABLE ${expected.table} ADD KEY ${expected.name} \\(${escapedColumns}\\)'\\s*\\)`,
    'i',
  )
  assert.match(migration, callPattern, `${expected.name} must use the idempotent helper`)

  const initPattern = new RegExp(
    `KEY\\s+${expected.name}\\s*\\(\\s*${escapedColumns.replaceAll(', ', '\\s*,\\s*')}\\s*\\)`,
    'i',
  )
  assert.match(freshInit, initPattern, `${expected.name} must exist in fresh init`)
}

assert.equal(
  [...migration.matchAll(/\bADD\s+KEY\s+idx_[a-z0-9_]+\s*\(/gi)].length,
  expectedIndexes.length,
  'migration must contain only the approved three index additions',
)

const manifestEntry = manifest.migrations.find(({ source }) => source === migrationSource)
assert.ok(manifestEntry, 'query-path index migration must be tracked by the Flyway manifest')
assert.equal(manifestEntry.stream, 'core')
assert.equal(manifestEntry.description, 'query_path_indexes')
assert.deepEqual(
  readFileSync(resolve(root, manifestEntry.resource)),
  readFileSync(resolve(root, migrationSource)),
  'runtime Flyway resource must be byte-identical to the canonical migration',
)

console.log('Query-path index migration guard passed.')
