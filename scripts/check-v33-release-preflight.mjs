import { spawnSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { migrationContentSha256, normalizeMigrationContent } from './migration-content-hash.mjs'

const root = new URL('../', import.meta.url)
const rootPath = fileURLToPath(root)
const schemaScript = new URL('./check-schema-readiness.mjs', import.meta.url)
const manifestPath = new URL('../db/migration/flyway-manifest.json', import.meta.url)
const healthControllerPath = new URL(
  '../community-bootstrap/src/main/java/com/offerlab/community/HealthController.java',
  import.meta.url,
)
const migrationCheckServicePath = new URL(
  '../community-infrastructure/src/main/java/com/offerlab/community/infra/db/MigrationCheckService.java',
  import.meta.url,
)
const channelHealthServicePath = new URL(
  '../community-domain-analytics/src/main/java/com/offerlab/community/analytics/application/ChannelHealthService.java',
  import.meta.url,
)
const postRevisionFacadePath = new URL(
  '../community-domain-post/src/main/java/com/offerlab/community/post/api/quality/PostContentRevisionQueryFacade.java',
  import.meta.url,
)
const postRevisionProjectionPath = new URL(
  '../community-domain-post/src/main/java/com/offerlab/community/post/api/quality/PostPublicRevisionBoundaryProjection.java',
  import.meta.url,
)
const postRevisionMapperPath = new URL(
  '../community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostVersionHistoryMapper.java',
  import.meta.url,
)
const v33DocumentPath = new URL(
  '../../文档/V33/OfferLab-V33-修订感知频道质量投影与可控发布验收护栏详细方案-2026-08-04.md',
  import.meta.url,
)

const staticChecks = [
  checkFile(v33DocumentPath, 'v33DesignDocument'),
  checkText(
    healthControllerPath,
    'creatorQualityProjectionReleaseReady',
    'strictReadinessProjectionGate',
  ),
  checkText(
    migrationCheckServicePath,
    'creatorQualityProjectionReady',
    'projectionSchemaReadiness',
  ),
  checkText(
    channelHealthServicePath,
    'projectionTokensStillCurrent',
    'revisionAwareTokenRevalidation',
  ),
  checkText(
    channelHealthServicePath,
    'remainingCandidates',
    'requestWideCandidateBudget',
  ),
  checkText(
    postRevisionFacadePath,
    'queryPublicRevisionBoundaryPage',
    'publicRevisionBoundaryContract',
  ),
  checkText(
    postRevisionProjectionPath,
    'revisionToken',
    'opaqueRevisionProjectionContract',
  ),
  checkText(
    postRevisionMapperPath,
    "JSON_EXTRACT(e.ext_json, '$.anonymous')",
    'anonymousCareerProjectionExclusion',
  ),
  checkRevisionBoundaryAssets(),
]

const schema = runSchemaReadiness()
const ready = staticChecks.every((item) => item.ready) && schema.ready
const report = {
  version: 'V33',
  mode: 'read-only',
  checkedAt: new Date().toISOString(),
  ready,
  staticChecks,
  schema,
  action: ready
    ? 'Run the multi-role HTTP acceptance checklist in the authorized environment.'
    : 'Review the blocked checks. This command does not apply migrations, backfill data, or start services.',
}

console.log(JSON.stringify(report, null, 2))
process.exitCode = ready ? 0 : 1

function runSchemaReadiness() {
  const result = spawnSync(process.execPath, [fileURLToPath(schemaScript), '--json'], {
    cwd: rootPath,
    env: process.env,
    encoding: 'utf8',
    windowsHide: true,
  })
  const payload = parseJson(result.stdout)
  if (!payload || typeof payload !== 'object') {
    return {
      ready: false,
      status: 'UNAVAILABLE',
      code: 'SCHEMA_READINESS_CHECK_FAILED',
    }
  }
  return {
    ready: payload.ready === true,
    status: payload.ready === true ? 'UP' : 'BLOCKED',
    missing: Array.isArray(payload.missing) ? payload.missing : [],
    migrationLifecycle: payload.migrationLifecycle ?? null,
  }
}

function checkRevisionBoundaryAssets() {
  try {
    const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'))
    const migration = manifest.migrations?.find(
      (item) => item.version === '20260804.01' && item.stream === 'core',
    )
    if (!migration?.source || !migration?.resource || !migration?.sha256) {
      return { name: 'v32RevisionBoundaryMigrationAssets', ready: false }
    }
    const source = readFileSync(new URL(`../${migration.source}`, import.meta.url))
    const resource = readFileSync(new URL(`../${migration.resource}`, import.meta.url))
    const init = readFileSync(new URL('../db/init/39_creator_content_revision_boundary.sql', import.meta.url))
    return {
      name: 'v32RevisionBoundaryMigrationAssets',
      ready: migrationContentSha256(source) === migration.sha256
        && normalizeMigrationContent(source) === normalizeMigrationContent(resource)
        && normalizeMigrationContent(source) === normalizeMigrationContent(init),
    }
  } catch {
    return { name: 'v32RevisionBoundaryMigrationAssets', ready: false }
  }
}

function checkFile(path, name) {
  return { name, ready: existsSync(path) }
}

function checkText(path, marker, name) {
  try {
    return { name, ready: readFileSync(path, 'utf8').includes(marker) }
  } catch {
    return { name, ready: false }
  }
}

function parseJson(value) {
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}
