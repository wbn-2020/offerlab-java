import { createHash } from 'node:crypto'
import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import assert from 'node:assert/strict'

const root = resolve(import.meta.dirname, '..')
const manifest = JSON.parse(readFileSync(resolve(root, 'db/migration/flyway-manifest.json'), 'utf8'))
const migrations = manifest.migrations

assert.equal(manifest.baselineVersion, '0')
assert.equal(manifest.schemaHistoryTable, 'flyway_schema_history')
assert.equal(migrations.length, 53, 'all canonical migrations must be tracked')
assert.equal(migrations.filter(({ stream }) => stream === 'core').length, 50)
assert.equal(migrations.filter(({ stream }) => stream === 'demo').length, 3)
assert.equal(new Set(migrations.map(({ version }) => version)).size, migrations.length)

const sortedVersions = [...migrations].sort((left, right) =>
  left.version.localeCompare(right.version, 'en', { numeric: true }),
)
assert.deepEqual(migrations.map(({ version }) => version), sortedVersions.map(({ version }) => version))

for (const migration of migrations) {
  assert.match(migration.version, /^\d{8}\.\d{2}$/)
  assert.match(migration.resource, /\/V\d{8}\.\d{2}__[a-z0-9_]+\.sql$/)
  const source = readFileSync(resolve(root, migration.source))
  const resource = readFileSync(resolve(root, migration.resource))
  const sha256 = createHash('sha256').update(source).digest('hex')
  assert.equal(sha256, migration.sha256, `manifest checksum drift: ${migration.source}`)
  assert.deepEqual(resource, source, `Flyway resource drift: ${migration.resource}`)
}

for (const stream of ['core', 'demo']) {
  const directory = resolve(root, `community-bootstrap/src/main/resources/db/flyway/${stream}`)
  const expected = migrations.filter((migration) => migration.stream === stream).map(({ resource }) =>
    resource.split('/').at(-1),
  )
  const actual = readdirSync(directory).filter((name) => name.endsWith('.sql')).sort()
  assert.deepEqual(actual, expected.sort(), `unexpected ${stream} Flyway resources`)
}

const application = readFileSync(resolve(root, 'community-bootstrap/src/main/resources/application.yml'), 'utf8')
assert.match(application, /locations:\s*\$\{OFFERLAB_FLYWAY_LOCATIONS:classpath:db\/flyway\/core\}/)
assert.match(application, /baseline-version:\s*["']?0["']?/)
assert.match(application, /clean-disabled:\s*true/)
assert.match(application, /validate-on-migrate:\s*true/)
assert.match(application, /validate-migration-naming:\s*true/)

const relationMigration = readFileSync(resolve(root, 'db/migration/20260530_relation_unique_keys.sql'), 'utf8')
assert.match(relationMigration, /DROP INDEX `', p_index, '`, ADD /)
assert.doesNotMatch(relationMigration, /CALL v20260530_rel_drop_index_if_exists/)

const operationMigration = readFileSync(
  resolve(root, 'db/migration/20260708_operation_soft_delete_unique_guard.sql'),
  'utf8',
)
assert.match(operationMigration, /DROP INDEX `', p_index, '`, ADD /)
assert.match(operationMigration, /operation_assert_no_active_duplicates/)
assert.doesNotMatch(operationMigration, /CALL v20260708_operation_drop_index_if_exists/)

const readinessScript = readFileSync(resolve(root, 'scripts/check-schema-readiness.mjs'), 'utf8')
assert.match(readinessScript, /flyway_schema_history/)
assert.match(readinessScript, /checksum/)
assert.match(readinessScript, /migrationLifecycle/)

const runtimeReadiness = readFileSync(
  resolve(
    root,
    'community-infrastructure/src/main/java/com/offerlab/community/infra/db/MigrationCheckService.java',
  ),
  'utf8',
)
assert.match(runtimeReadiness, /EXPECTED_CORE_MIGRATIONS = 50/)
assert.match(runtimeReadiness, /LATEST_CORE_MIGRATION = "20260709\.01"/)
assert.match(runtimeReadiness, /flywayLifecycleStatus\(\)/)
assert.match(runtimeReadiness, /checksum IS NULL/)

console.log('Flyway migration lifecycle guard passed.')
