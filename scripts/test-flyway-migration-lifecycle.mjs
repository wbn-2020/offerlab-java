import { createHash } from 'node:crypto'
import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import assert from 'node:assert/strict'

const root = resolve(import.meta.dirname, '..')
const manifest = JSON.parse(readFileSync(resolve(root, 'db/migration/flyway-manifest.json'), 'utf8'))
const migrations = manifest.migrations
const coreMigrations = migrations.filter(({ stream }) => stream === 'core')
const demoMigrations = migrations.filter(({ stream }) => stream === 'demo')

assert.deepEqual(Object.keys(manifest), [
  'formatVersion',
  'baselineVersion',
  'schemaHistoryTable',
  'generatedFrom',
  'streams',
  'migrations',
])
assert.equal(manifest.formatVersion, 2)
assert.equal(manifest.baselineVersion, '0')
assert.equal(manifest.schemaHistoryTable, 'flyway_schema_history')
assert.equal(manifest.generatedFrom, 'db/migration/20*.sql')
assert.deepEqual(manifest.streams, {
  core: {
    location: 'classpath:db/flyway/core',
    autoMigrate: true,
    expectedMigrations: coreMigrations.length,
  },
  demo: {
    location: 'classpath:db/flyway/demo',
    autoMigrate: false,
    expectedMigrations: demoMigrations.length,
    historyTable: 'flyway_demo_schema_history',
  },
})
assert.ok(coreMigrations.length > 0, 'at least one core migration must be tracked')
assert.equal(migrations.length, coreMigrations.length + demoMigrations.length)
assert.equal(new Set(migrations.map(({ version }) => version)).size, migrations.length)

const sortedVersions = [...migrations].sort((left, right) =>
  left.version.localeCompare(right.version, 'en', { numeric: true }),
)
assert.deepEqual(migrations.map(({ version }) => version), sortedVersions.map(({ version }) => version))

for (const migration of migrations) {
  assert.deepEqual(Object.keys(migration), [
    'version',
    'description',
    'stream',
    'source',
    'resource',
    'sha256',
    'flywayChecksum',
  ])
  assert.match(migration.version, /^\d{8}\.\d{2}$/)
  assert.match(migration.resource, /\/V\d{8}\.\d{2}__[a-z0-9_]+\.sql$/)
  const source = readFileSync(resolve(root, migration.source))
  const resource = readFileSync(resolve(root, migration.resource))
  const sha256 = createHash('sha256').update(source).digest('hex')
  assert.equal(sha256, migration.sha256, `manifest checksum drift: ${migration.source}`)
  assert.equal(
    flywayChecksum(source),
    migration.flywayChecksum,
    `manifest Flyway checksum drift: ${migration.source}`,
  )
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
assert.match(application, /table:\s*\$\{OFFERLAB_FLYWAY_TABLE:flyway_schema_history\}/)
assert.match(application, /baseline-version:\s*["']?0["']?/)
assert.match(application, /clean-disabled:\s*true/)
assert.match(application, /validate-on-migrate:\s*true/)
assert.match(application, /validate-migration-naming:\s*true/)
assert.match(application, /on-profile:\s*local[\s\S]*?enabled:\s*\$\{OFFERLAB_FLYWAY_ENABLED:false\}/)

const migrationReadme = readFileSync(resolve(root, 'db/migration/README.md'), 'utf8')
assert.match(migrationReadme, /four `demo_\*`\s+data seeds/)
assert.match(migrationReadme, /flyway-manifest\.json/)
assert.match(migrationReadme, /OFFERLAB_FLYWAY_LOCATIONS='classpath:db\/flyway\/demo'/)
assert.match(migrationReadme, /OFFERLAB_FLYWAY_TABLE='flyway_demo_schema_history'/)

const syncScript = readFileSync(resolve(root, 'db/migration/sync-flyway-resources.ps1'), 'utf8')
assert.match(syncScript, /\$existingManifest\.migrations/)
assert.match(syncScript, /Refusing to remove a migration already tracked by the Flyway manifest/)
assert.match(syncScript, /\$existingVersionsBySource\[\$sourceRelative\]/)
assert.match(syncScript, /AllowTrackedMigrationRewrite/)
assert.match(syncScript, /Tracked migration content changed/)
assert.match(syncScript, /Flyway manifest drift detected/)
assert.match(syncScript, /flywayChecksum/)
assert.match(syncScript, /Get-FlywayChecksum/)
assert.match(syncScript, /Database init mirror drift detected/)

const safetyScript = readFileSync(resolve(root, 'scripts/check-migration-safety.ps1'), 'utf8')
assert.match(safetyScript, /canonical migration count does not match the manifest/)
assert.doesNotMatch(safetyScript, /\$files\.Count -ne \d+/)
for (const destructiveRule of [
  /DROP\\s\+DATABASE/,
  /DROP\\s\+INDEX/,
  /MODIFY\\s\+/,
  /CHANGE\\s\+/,
]) {
  assert.match(safetyScript, destructiveRule)
}
assert.match(
  safetyScript,
  /DROP\\s\+\(\?:COLUMN\\s\+/,
  'migration safety must recognize the explicit DROP COLUMN form',
)
assert.match(
  safetyScript,
  /\(\?!INDEX\\b\|KEY\\b\|PRIMARY\\b\|FOREIGN\\b/,
  'shorthand DROP detection must exclude index and constraint operations',
)
assert.match(safetyScript, /migration-safety:\\s\*allow/)
assert.match(safetyScript, /unused migration safety rule/)

const safetyPolicyTest = readFileSync(
  resolve(root, 'scripts/test-migration-safety-policy.ps1'),
  'utf8',
)
for (const fixture of [
  'drop-database',
  'drop-column',
  'drop-column-shorthand',
  'drop-index',
  'modify-column',
  'change-column',
  'reviewed-drop-index',
  'reviewed-modify',
  'unused-directive',
]) {
  assert.match(safetyPolicyTest, new RegExp(`"${fixture}"`))
}

const eventConsumerInboxMigration = readFileSync(
  resolve(root, 'db/migration/20260726_event_consumer_inbox.sql'),
  'utf8',
)
assert.match(
  eventConsumerInboxMigration,
  /PRIMARY KEY\s*\(\s*consumer_name,\s*idempotency_key\s*\)/i,
)
assert.match(
  eventConsumerInboxMigration,
  /idx_event_consumer_inbox_created\s*\(\s*create_time,\s*id\s*\)/i,
)
assert.doesNotMatch(eventConsumerInboxMigration, /ON\s+DUPLICATE\s+KEY/i)

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

const collaborationLifecycleMigration = readFileSync(
  resolve(root, 'db/migration/20260718_collab_need_lifecycle.sql'),
  'utf8',
)
for (const token of [
  'claimed_at',
  'last_progress_at',
  't_collab_content_need_event',
  'visibility_scope',
  'idx_collab_need_follow_uid_active_id',
  'idx_collab_need_follow_need_active_id',
  'idx_collab_need_event_need',
  'idx_collab_need_event_visibility',
]) {
  assert.match(
    collaborationLifecycleMigration,
    new RegExp(token),
    `collaboration lifecycle migration must include ${token}`,
  )
}
assert.match(collaborationLifecycleMigration, /UPDATE t_collab_content_need/)
assert.match(collaborationLifecycleMigration, /WHERE need_status IN \('CLAIMED', 'SUBMITTED'\)/)
assert.match(collaborationLifecycleMigration, /COALESCE\(claimed_at, update_time, create_time\)/)
assert.match(collaborationLifecycleMigration, /COALESCE\(last_progress_at, update_time, create_time\)/)
assert.doesNotMatch(collaborationLifecycleMigration, /INSERT INTO t_collab_content_need_event/)
assert.doesNotMatch(collaborationLifecycleMigration, /FOREIGN\s+KEY/i)

const trustedContentMigration = readFileSync(
  resolve(root, 'db/migration/20260713_trusted_content_stage1.sql'),
  'utf8',
)
for (const table of [
  't_int_post_trust_state',
  't_int_post_useful_feedback',
  't_int_content_suggestion',
]) {
  assert.match(trustedContentMigration, new RegExp(`CREATE TABLE IF NOT EXISTS ${table}`))
}
for (const column of [
  'result_version',
  'public_update_summary',
  'impact_scope',
  'event_key',
  'successor_post_id',
  'last_confirmed_at',
  'suggestions_open',
  'pending_guard',
]) {
  assert.match(trustedContentMigration, new RegExp(column))
}
for (const index of [
  'uk_user_post',
  'uk_pending_suggestion',
  'uk_growth_event_key',
  'uk_post_result_version',
  'idx_post_public_update',
]) {
  assert.match(trustedContentMigration, new RegExp(index))
}
for (const constraint of [
  'fk_trust_state_post',
  'fk_trust_state_accepted_comment',
  'fk_trust_state_duplicate_post',
  'fk_trust_state_successor_post',
  'fk_useful_feedback_post',
  'fk_useful_feedback_user',
  'fk_content_suggestion_post',
  'fk_content_suggestion_submitter',
  'fk_content_suggestion_decider',
  'fk_content_suggestion_result_version',
]) {
  assert.match(trustedContentMigration, new RegExp(constraint))
}
assert.match(trustedContentMigration, /assert_no_duplicates/)
assert.match(
  trustedContentMigration,
  /pending_guard\s+TINYINT[\s\S]*?GENERATED ALWAYS AS\s*\(\s*CASE WHEN decision IS NULL THEN 1 ELSE NULL END\s*\)\s*STORED/i,
)
assert.doesNotMatch(
  trustedContentMigration,
  /CASE WHEN decision IS NULL THEN pending_dedup_key ELSE NULL END/i,
)
assert.match(
  trustedContentMigration,
  /UNIQUE KEY uk_pending_suggestion\s*\(\s*post_id,\s*submitter_uid,\s*suggestion_type,\s*normalized_content_hash,\s*pending_guard\s*\)/i,
)
assert.match(
  trustedContentMigration,
  /SELECT post_id,\s*submitter_uid,\s*suggestion_type,\s*normalized_content_hash[\s\S]*?WHERE decision IS NULL[\s\S]*?GROUP BY post_id,\s*submitter_uid,\s*suggestion_type,\s*normalized_content_hash/i,
)
assert.match(trustedContentMigration, /ensure_pending_guard/)
assert.match(trustedContentMigration, /ensure_column_definition/)
assert.match(trustedContentMigration, /COLUMN_TYPE/)
assert.match(trustedContentMigration, /IS_NULLABLE/)
assert.match(trustedContentMigration, /CHARACTER_SET_NAME/)
assert.match(trustedContentMigration, /COLLATION_NAME/)
for (const [index, columns] of [
  ['idx_content_suggestion_mine_time', 'post_id,submitter_uid,update_time,id'],
  ['idx_content_suggestion_mine_decided', 'post_id,submitter_uid,decided_at,id'],
  ['idx_content_suggestion_author_pending', 'post_author_id,decision,update_time,id'],
  ['idx_content_suggestion_author_time', 'post_id,update_time,id'],
  ['idx_content_suggestion_public', 'post_id,decided_at,id'],
]) {
  assert.match(
    trustedContentMigration,
    new RegExp(`'${index}'\\s*,\\s*'${columns}'`, 'i'),
  )
}
const answeredQuestionBackfill = trustedContentMigration.match(
  /INSERT INTO t_int_post_trust_state\s*\(\s*post_id,\s*question_status\s*\)\s*SELECT\s+p\.id,\s*'ANSWERED'\s+FROM\s+t_post_main\s+p[\s\S]*?;/i,
)?.[0]
assert.ok(
  answeredQuestionBackfill,
  'trusted-content migration must backfill answered community questions',
)
assert.match(answeredQuestionBackfill, /\bp\.post_type\s*=\s*13\b/i)
assert.match(answeredQuestionBackfill, /\bp\.is_deleted\s*=\s*0\b/i)
assert.match(
  answeredQuestionBackfill,
  /EXISTS\s*\([\s\S]*?FROM\s+t_int_comment\s+c[\s\S]*?c\.post_id\s*=\s*p\.id[\s\S]*?c\.root_id\s*=\s*0[\s\S]*?c\.comment_status\s*=\s*1[\s\S]*?c\.is_deleted\s*=\s*0[\s\S]*?\)/i,
)
assert.match(
  answeredQuestionBackfill,
  /NOT EXISTS\s*\([\s\S]*?FROM\s+t_int_post_trust_state\s+s[\s\S]*?s\.post_id\s*=\s*p\.id[\s\S]*?\)/i,
)
assert.doesNotMatch(answeredQuestionBackfill, /\bOPEN\b/i)
assert.doesNotMatch(
  answeredQuestionBackfill,
  /\bp\.(?:visibility|post_status)\b|\bc\.parent_id\b|\bt_int_comment_quality_signal\b/i,
)
assert.doesNotMatch(trustedContentMigration, /\bDROP TABLE\b/i)
assert.doesNotMatch(trustedContentMigration, /\bDELETE\s+FROM\b/i)

const knowledgeLifecycleMigration = readFileSync(
  resolve(root, 'db/migration/20260720_knowledge_lifecycle.sql'),
  'utf8',
)
const knowledgeLifecycleBackfill = readFileSync(
  resolve(root, 'db/migration/20260720_knowledge_lifecycle_backfill.sql'),
  'utf8',
)
for (const token of [
  'ALTER TABLE t_int_content_suggestion',
  'base_version',
  'target_scope',
  'target_locator',
  'expected_change',
  'resolution',
  'delivery_status',
  'CREATE TABLE t_post_reference',
  'normalized_url',
  'active_guard',
  'uk_post_reference_active_url',
  'CREATE TABLE t_post_knowledge_relation',
  'visibility_status',
  'effective_guard',
  'uk_post_knowledge_relation_effective',
  'CREATE TABLE t_int_post_outcome',
  'publication_status',
  'uk_int_post_outcome_current',
]) {
  assert.match(
    knowledgeLifecycleMigration,
    new RegExp(token),
    `V10 knowledge lifecycle migration must include ${token}`,
  )
}
assert.match(knowledgeLifecycleMigration, /source_post_id\s*<>\s*target_post_id/)
assert.match(knowledgeLifecycleMigration, /reviewer_uid IS NULL OR reviewer_uid <> proposer_uid/)
assert.doesNotMatch(knowledgeLifecycleMigration, /\bCREATE TABLE t_knowledge_relation\b/)
assert.doesNotMatch(knowledgeLifecycleMigration, /\bCREATE TABLE t_post_outcome\b/)
assert.doesNotMatch(knowledgeLifecycleMigration, /\bALTER TABLE t_content_suggestion\b/)
assert.match(knowledgeLifecycleBackfill, /SET resolution = CASE decision/)
assert.match(knowledgeLifecycleBackfill, /WHEN 'PARTIAL_ACCEPTED' THEN 'PARTIAL'/)
assert.match(knowledgeLifecycleBackfill, /WHEN 'MERGED' THEN 'ACCEPTED'/)
assert.match(knowledgeLifecycleBackfill, /delivery_status = CASE/)
assert.match(knowledgeLifecycleBackfill, /result_version IS NOT NULL/)
assert.match(knowledgeLifecycleBackfill, /WHERE decision IS NOT NULL/)
assert.equal(
  migrations.find(({ source }) =>
    source === 'db/migration/20260720_knowledge_lifecycle.sql')?.version,
  '20260720.02',
  'V10 knowledge lifecycle migration must retain Flyway version 20260720.02',
)

const collaborationMigration = readFileSync(
  resolve(root, 'db/migration/20260714_collaboration_stage2.sql'),
  'utf8',
)
assert.match(collaborationMigration, /ALTER TABLE t_community_topic/)
assert.doesNotMatch(collaborationMigration, /v20260714_collaboration_ensure_topic_schema/)
assert.match(collaborationMigration, /idx_community_topic_domain/)
assert.equal(
  migrations.find(({ source }) =>
    source === 'db/migration/20260714_collaboration_stage2.sql')?.flywayChecksum,
  384653082,
  'published collaboration migration checksum must remain immutable',
)

const collaborationInit = readFileSync(resolve(root, 'db/init/14_collaboration.sql'), 'utf8')
const incentiveInit = readFileSync(resolve(root, 'db/init/15_incentive.sql'), 'utf8')
const hardeningMigration = readFileSync(
  resolve(root, 'db/migration/20260714_database_integrity_hardening.sql'),
  'utf8',
)
const hardeningInit = readFileSync(
  resolve(root, 'db/init/16_database_integrity_hardening.sql'),
  'utf8',
)
assert.equal(
  collaborationInit,
  collaborationMigration,
  'fresh collaboration init must mirror the canonical Stage 2 migration',
)
assert.equal(
  incentiveInit,
  readFileSync(resolve(root, 'db/migration/20260714_incentive_stage3_stage5.sql'), 'utf8'),
  'fresh incentive init must mirror the canonical Stage 3-5 migration',
)
assert.equal(
  hardeningInit,
  hardeningMigration,
  'fresh integrity init must mirror the canonical hardening migration',
)
for (const index of [
  'idx_incentive_freeze_account_status',
  'idx_incentive_invalidation_reference',
  'idx_virtual_benefit_order_benefit',
  'idx_quota_submission_applicant',
  'idx_quota_bounty_appeal_applicant',
]) {
  assert.match(hardeningMigration, new RegExp(index))
}
assert.match(hardeningMigration, /chk_virtual_benefit_stock_null_pair/)
assert.match(hardeningMigration, /chk_virtual_benefit_order_cost/)
assert.match(hardeningMigration, /SIGNAL SQLSTATE '45000'/)
assert.match(hardeningMigration, /conflicting integrity index/)
assert.match(hardeningMigration, /conflicting integrity check/)
assert.match(hardeningMigration, /reward rule seed identity conflicts/)
assert.match(hardeningMigration, /virtual benefit seed identity conflicts/)
assert.match(hardeningMigration, /community role seed identity conflicts/)
for (const table of [
  't_collab_content_need',
  't_collab_series',
  't_collab_activity',
  't_collab_office_hour',
  't_collab_discussion',
  't_collab_governance_case',
]) {
  assert.match(collaborationInit, new RegExp(`CREATE TABLE IF NOT EXISTS ${table}`))
}
for (const table of [
  't_incentive_account',
  't_incentive_ledger',
  't_incentive_reward_inbox',
  't_virtual_benefit_catalog',
  't_quota_bounty',
  't_community_role_grant',
]) {
  assert.match(incentiveInit, new RegExp(`CREATE TABLE IF NOT EXISTS ${table}`))
}

const postInit = readFileSync(resolve(root, 'db/init/02_post.sql'), 'utf8')
assert.match(postInit, /result_version/)
assert.match(postInit, /public_update_summary/)
assert.match(postInit, /impact_scope/)
assert.match(postInit, /idx_post_public_update/)
assert.doesNotMatch(postInit, /allowed_domains/)
assert.doesNotMatch(postInit, /idx_community_topic_domain/)
const communityTopicsInit = readFileSync(resolve(root, 'db/init/12_community_topics.sql'), 'utf8')
assert.doesNotMatch(communityTopicsInit, /allowed_domains/)
assert.doesNotMatch(communityTopicsInit, /idx_community_topic_domain/)

const interactionInit = readFileSync(resolve(root, 'db/init/03_interaction.sql'), 'utf8')
assert.match(interactionInit, /CREATE TABLE t_int_post_trust_state/)
assert.match(interactionInit, /CREATE TABLE t_int_post_useful_feedback/)
assert.match(interactionInit, /CREATE TABLE t_int_content_suggestion/)
assert.match(
  interactionInit,
  /pending_guard\s+TINYINT[\s\S]*?GENERATED ALWAYS AS\s*\(\s*CASE WHEN decision IS NULL THEN 1 ELSE NULL END\s*\)\s*STORED/i,
)
assert.match(
  interactionInit,
  /UNIQUE KEY uk_pending_suggestion\s*\(\s*post_id,\s*submitter_uid,\s*suggestion_type,\s*normalized_content_hash,\s*pending_guard\s*\)/i,
)
for (const [index, columns] of [
  ['idx_content_suggestion_mine_time', 'post_id, submitter_uid, update_time, id'],
  ['idx_content_suggestion_mine_decided', 'post_id, submitter_uid, decided_at, id'],
  ['idx_content_suggestion_author_pending', 'post_author_id, decision, update_time, id'],
  ['idx_content_suggestion_author_time', 'post_id, update_time, id'],
  ['idx_content_suggestion_public', 'post_id, decided_at, id'],
]) {
  assert.match(interactionInit, new RegExp(`${index}\\s*\\(${columns}\\)`, 'i'))
}

const contentSuggestionMapper = readFileSync(
  resolve(
    root,
    'community-domain-interaction/src/main/java/com/offerlab/community/interaction/infrastructure/persistence/mapper/ContentSuggestionMapper.java',
  ),
  'utf8',
)
assert.equal(
  [...contentSuggestionMapper.matchAll(/AND decided_at IS NOT NULL/g)].length,
  3,
  'decided and public suggestion lists must reject rows without decided_at',
)
assert.equal(
  [...contentSuggestionMapper.matchAll(/ORDER BY decided_at DESC, id DESC/g)].length,
  3,
  'decided and public suggestion lists must sort by decision time',
)
assert.equal(
  [...contentSuggestionMapper.matchAll(/ORDER BY update_time DESC, id DESC/g)].length,
  2,
  'default and pending suggestion lists must sort by update time',
)

const analyticsInit = readFileSync(resolve(root, 'db/init/05_analytics.sql'), 'utf8')
assert.match(analyticsInit, /event_key/)
assert.match(analyticsInit, /uk_growth_event_key/)

const demoCommunitySeed = readFileSync(
  resolve(root, 'db/migration/20260712_demo_community_seed.sql'),
  'utf8',
)
assert.match(demoCommunitySeed, /community seed requires an existing active author/)
assert.match(demoCommunitySeed, /community seed topic tag identity conflict/)
assert.match(demoCommunitySeed, /community seed post tag identity conflict/)
assert.match(
  demoCommunitySeed,
  /\(991100000000000002, @community_demo_uid, 16,[\s\S]*?'format', 'DISCUSSION'/,
)
assert.match(
  demoCommunitySeed,
  /\(991100000000000006, @community_demo_uid, 11,[\s\S]*?'format', 'REVIEW'/,
)
assert.match(
  demoCommunitySeed,
  /SELECT COUNT\(\*\)[\s\S]*?WHERE author_id = @community_demo_uid[\s\S]*?AND post_status = 1[\s\S]*?AND is_deleted = 0/,
)

const initSeed = readFileSync(resolve(root, 'db/init/99_seed.sql'), 'utf8')
const generatedSeedStart = '-- BEGIN GENERATED FROM db/migration/20260712_demo_community_seed.sql'
const generatedSeedEnd = '-- END GENERATED FROM db/migration/20260712_demo_community_seed.sql'
const generatedSeedStartIndex = initSeed.indexOf(generatedSeedStart)
const generatedSeedEndIndex = initSeed.indexOf(generatedSeedEnd)
assert.ok(generatedSeedStartIndex >= 0, '99_seed.sql must contain the generated community seed start marker')
assert.ok(generatedSeedEndIndex > generatedSeedStartIndex, '99_seed.sql must contain the generated community seed end marker')
const generatedSeedBody = initSeed
  .slice(generatedSeedStartIndex + generatedSeedStart.length, generatedSeedEndIndex)
  .replace(/\r\n/g, '\n')
  .trim()
assert.equal(
  generatedSeedBody,
  demoCommunitySeed.replace(/\r\n/g, '\n').trim(),
  '99_seed.sql community seed block must match the canonical demo migration',
)

const readinessScript = readFileSync(resolve(root, 'scripts/check-schema-readiness.mjs'), 'utf8')
assert.match(readinessScript, /flyway_schema_history/)
assert.match(readinessScript, /checksum/)
assert.match(readinessScript, /migrationLifecycle/)
assert.match(readinessScript, /t_int_post_trust_state/)
assert.match(readinessScript, /t_int_post_useful_feedback/)
assert.match(readinessScript, /t_int_content_suggestion/)
assert.match(readinessScript, /t_collab_content_need/)
assert.match(readinessScript, /t_collab_content_need_event/)
assert.match(readinessScript, /idx_collab_need_follow_uid_active_id/)
assert.match(readinessScript, /idx_collab_need_follow_need_active_id/)
assert.match(readinessScript, /idx_collab_need_event_need/)
assert.match(readinessScript, /idx_collab_need_event_visibility/)
assert.match(readinessScript, /20260718_collab_need_lifecycle\.sql/)
assert.match(readinessScript, /t_collab_governance_case/)
assert.match(readinessScript, /t_incentive_account/)
assert.match(readinessScript, /t_incentive_ledger/)
assert.match(readinessScript, /t_search_index_rebuild_task/)
assert.match(readinessScript, /uk_search_index_rebuild_active/)
assert.match(readinessScript, /idx_search_index_rebuild_status_time/)
assert.match(readinessScript, /idx_search_index_rebuild_lease/)
assert.match(readinessScript, /20260720_search_index_rebuild_task\.sql/)
assert.match(readinessScript, /t_post_reference/)
assert.match(readinessScript, /t_post_knowledge_relation/)
assert.match(readinessScript, /t_int_post_outcome/)
assert.match(readinessScript, /20260720_knowledge_lifecycle\.sql/)
assert.match(readinessScript, /t_virtual_benefit_catalog/)
assert.match(readinessScript, /t_community_role_grant/)
assert.match(readinessScript, /idx_community_topic_domain/)
assert.match(readinessScript, /uk_growth_event_key/)
assert.match(readinessScript, /foreignKey/)
assert.match(readinessScript, /20260713_trusted_content_stage1\.sql/)
assert.match(readinessScript, /columnDefinitions/)
assert.match(readinessScript, /indexDefinitions/)
assert.match(readinessScript, /foreignKeyDefinitions/)
assert.match(readinessScript, /generation_expression/)
assert.match(readinessScript, /flywayChecksum/)
assert.match(readinessScript, /function flywayChecksum\(content\)/)
assert.match(readinessScript, /checksumMismatches/)
assert.match(readinessScript, /unexpectedVersions/)
assert.match(readinessScript, /input:\s*sql/)
assert.doesNotMatch(readinessScript, /['"]-e['"]/)

const runtimeReadiness = readFileSync(
  resolve(
    root,
    'community-infrastructure/src/main/java/com/offerlab/community/infra/db/MigrationCheckService.java',
  ),
  'utf8',
)
assert.doesNotMatch(runtimeReadiness, /EXPECTED_CORE_MIGRATIONS/)
assert.doesNotMatch(runtimeReadiness, /LATEST_CORE_MIGRATION/)
assert.match(runtimeReadiness, /int expectedCoreMigrations = expectedMigrations\.size\(\)/)
assert.match(runtimeReadiness, /flywayLifecycleStatus\(\)/)
assert.match(runtimeReadiness, /trustedContentReady\(\)/)
assert.match(runtimeReadiness, /t_collab_content_need/)
assert.match(runtimeReadiness, /t_collab_content_need_event/)
for (const column of [
  'submitted_by_uid',
  'submitted_at',
  'submission_resolution_type',
  'submission_resolution_id',
  'submission_note',
  'reject_reason',
  'claimed_at',
  'last_progress_at',
]) {
  assert.match(
    runtimeReadiness,
    new RegExp(`"${column}"`),
    `runtime collaboration readiness must check ${column}`,
  )
}
assert.match(runtimeReadiness, /collaborationLifecycleReady\(\)/)
assert.match(runtimeReadiness, /idx_collab_need_follow_uid_active_id/)
assert.match(runtimeReadiness, /idx_collab_need_follow_need_active_id/)
assert.match(runtimeReadiness, /idx_collab_need_event_need/)
assert.match(runtimeReadiness, /idx_collab_need_event_visibility/)
assert.match(runtimeReadiness, /t_collab_governance_case/)
assert.match(runtimeReadiness, /t_incentive_account/)
assert.match(runtimeReadiness, /t_incentive_ledger/)
assert.match(runtimeReadiness, /searchIndexRebuildTaskReady\(\)/)
assert.match(runtimeReadiness, /knowledgeLifecycleReady\(\)/)
assert.match(runtimeReadiness, /t_post_reference/)
assert.match(runtimeReadiness, /t_post_knowledge_relation/)
assert.match(runtimeReadiness, /t_int_post_outcome/)
assert.match(runtimeReadiness, /t_search_index_rebuild_task/)
assert.match(runtimeReadiness, /uk_search_index_rebuild_active/)
assert.match(runtimeReadiness, /t_virtual_benefit_catalog/)
assert.match(runtimeReadiness, /t_community_role_grant/)
assert.match(runtimeReadiness, /idx_community_topic_domain/)
assert.match(runtimeReadiness, /columnDefinitionMatches/)
assert.match(runtimeReadiness, /indexDefinitionMatches/)
assert.match(runtimeReadiness, /foreignKeyDefinitionMatches/)
assert.match(runtimeReadiness, /expectedCoreMigrations/)
assert.match(runtimeReadiness, /migrationLifecycleRequired/)
assert.match(runtimeReadiness, /calculateFlywayChecksum/)
assert.match(runtimeReadiness, /checksumMismatches/)
assert.match(runtimeReadiness, /unexpectedVersions/)
assert.match(runtimeReadiness, /replace\("_utf8mb4", ""\)/)
for (const index of [
  'idx_content_suggestion_mine_time',
  'idx_content_suggestion_mine_decided',
  'idx_content_suggestion_author_pending',
  'idx_content_suggestion_author_time',
]) {
  assert.match(runtimeReadiness, new RegExp(index), `runtime readiness must check ${index}`)
}

const applicationMain = readFileSync(
  resolve(root, 'community-bootstrap/src/main/java/com/offerlab/community/CommunityApplication.java'),
  'utf8',
)
const flywayEnvironmentGuard = readFileSync(
  resolve(root, 'community-bootstrap/src/main/java/com/offerlab/community/FlywayEnvironmentGuard.java'),
  'utf8',
)
assert.match(applicationMain, /addInitializers\(new FlywayEnvironmentGuard\(\)\)/)
assert.match(flywayEnvironmentGuard, /Set\.of\("prod", "production", "acceptance"\)/)
assert.match(flywayEnvironmentGuard, /must load only the core Flyway location/)
assert.match(flywayEnvironmentGuard, /must keep Flyway enabled/)
assert.match(flywayEnvironmentGuard, /must use the core Flyway history table/)
assert.match(flywayEnvironmentGuard, /must not enable Flyway baseline-on-migrate/)
for (const legacyIndex of [
  'idx_content_suggestion_post_status',
  'idx_content_suggestion_submitter',
  'idx_content_suggestion_author_status',
]) {
  assert.doesNotMatch(
    runtimeReadiness,
    new RegExp(legacyIndex),
    `runtime readiness must not check removed index ${legacyIndex}`,
  )
}

console.log('Flyway migration lifecycle guard passed.')

function flywayChecksum(content) {
  const crcTable = Array.from({ length: 256 }, (_, value) => {
    let crc = value
    for (let bit = 0; bit < 8; bit += 1) {
      crc = (crc & 1) === 1 ? (crc >>> 1) ^ 0xEDB88320 : crc >>> 1
    }
    return crc >>> 0
  })
  let crc = 0xFFFFFFFF
  const lines = content.toString('utf8').split(/\r\n|\n|\r/)
  for (const rawLine of lines) {
    const line = rawLine.replace(/^\uFEFF/, '')
    for (const byte of Buffer.from(line, 'utf8')) {
      crc = (crc >>> 8) ^ crcTable[(crc ^ byte) & 0xFF]
    }
  }
  return (crc ^ 0xFFFFFFFF) | 0
}
