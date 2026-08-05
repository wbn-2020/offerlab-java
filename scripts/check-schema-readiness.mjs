import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import {
  migrationContentSha256,
  normalizeMigrationContent,
} from './migration-content-hash.mjs'

const args = new Map()
for (const arg of process.argv.slice(2)) {
  const match = arg.match(/^--([^=]+)=(.*)$/)
  if (match) args.set(match[1], match[2])
}
if (args.has('password')) {
  console.error('--password is not supported because process arguments are observable. Use OFFERLAB_DB_PASSWORD, DB_PASSWORD, or DB_URL.')
  process.exit(2)
}

const jdbc = parseJdbcUrl(process.env.DB_URL)
const config = {
  host: args.get('host') || process.env.OFFERLAB_DB_HOST || jdbc.host || '127.0.0.1',
  port: args.get('port') || process.env.OFFERLAB_DB_PORT || jdbc.port || '3306',
  user: args.get('user') || process.env.OFFERLAB_DB_USER || process.env.DB_USERNAME || jdbc.user || 'offerlab',
  password: process.env.OFFERLAB_DB_PASSWORD || process.env.DB_PASSWORD || jdbc.password || '',
  database: args.get('database') || process.env.OFFERLAB_DB_NAME || jdbc.database || 'offerlab',
  json: process.argv.includes('--json'),
}

function parseJdbcUrl(value) {
  if (!value || !value.startsWith('jdbc:mysql://')) return {}
  try {
    const parsed = new URL(value.slice('jdbc:'.length))
    return {
      host: parsed.hostname,
      port: parsed.port || '3306',
      user: parsed.username ? decodeURIComponent(parsed.username) : undefined,
      password: parsed.password ? decodeURIComponent(parsed.password) : undefined,
      database: parsed.pathname.replace(/^\/+/, '') || undefined,
    }
  } catch {
    return {}
  }
}

const mysqlCandidates = [
  process.env.MYSQL_BIN,
  'mysql',
  'C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe',
].filter(Boolean)

const mysqlBin = mysqlCandidates.find((candidate) => candidate === 'mysql' || existsSync(candidate))
if (!mysqlBin) {
  console.error('mysql client not found. Set MYSQL_BIN to the mysql executable path.')
  process.exit(2)
}
const childEnv = { ...process.env }
if (config.password) childEnv.MYSQL_PWD = config.password

const migrationManifest = JSON.parse(
  readFileSync(new URL('../db/migration/flyway-manifest.json', import.meta.url), 'utf8'),
)
const coreMigrations = migrationManifest.migrations.filter(({ stream }) => stream === 'core')
const migrationAssetChecks = migrationManifest.migrations.map((migration) => {
  let ready = false
  try {
    const source = readFileSync(new URL(`../${migration.source}`, import.meta.url))
    const resource = readFileSync(new URL(`../${migration.resource}`, import.meta.url))
    const sha256 = migrationContentSha256(source)
    ready = sha256 === migration.sha256
      && flywayChecksum(source) === migration.flywayChecksum
      && normalizeMigrationContent(source) === normalizeMigrationContent(resource)
  } catch {
    ready = false
  }
  return {
    type: 'migrationAsset',
    table: 'flyway',
    name: migration.version,
    key: `migrationAsset:${migration.version}`,
    ready,
    migration: migration.source,
  }
})

const columnDefinitions = [
  columnDefinition('t_post_extension', 'domain', 'tinyint', true, {
    extra: 'VIRTUAL GENERATED',
    generationExpression: "casejson_unquotejson_extractext_json'$.domain'when'1'then1when'2'then2when'3'then3when'4'then4when'5'then5elsenullend",
  }),
  columnDefinition('t_int_post_trust_state', 'post_id', 'bigint', false),
  columnDefinition('t_int_post_trust_state', 'question_status', 'varchar(32)', false),
  columnDefinition('t_int_post_trust_state', 'accepted_comment_id', 'bigint', true),
  columnDefinition('t_int_post_trust_state', 'freshness_status', 'varchar(32)', false),
  columnDefinition('t_int_post_trust_state', 'successor_post_id', 'bigint', true),
  columnDefinition('t_int_post_trust_state', 'last_confirmed_at', 'datetime(3)', true),
  columnDefinition('t_int_post_trust_state', 'suggestions_open', 'tinyint', false),
  columnDefinition('t_int_post_useful_feedback', 'user_id', 'bigint', false),
  columnDefinition('t_int_post_useful_feedback', 'post_id', 'bigint', false),
  columnDefinition('t_int_post_useful_feedback', 'reason', 'varchar(32)', false),
  columnDefinition('t_int_content_suggestion', 'post_id', 'bigint', false),
  columnDefinition('t_int_content_suggestion', 'submitter_uid', 'bigint', false),
  columnDefinition('t_int_content_suggestion', 'suggestion_type', 'varchar(32)', false),
  columnDefinition('t_int_content_suggestion', 'normalized_content_hash', 'char(64)', false, {
    characterSet: 'ascii',
    collation: 'ascii_bin',
  }),
  columnDefinition('t_int_content_suggestion', 'decision', 'varchar(32)', true),
  columnDefinition('t_int_content_suggestion', 'pending_dedup_key', 'char(64)', true, {
    characterSet: 'ascii',
    collation: 'ascii_bin',
  }),
  columnDefinition('t_int_content_suggestion', 'pending_guard', 'tinyint', true, {
    extra: 'STORED GENERATED',
    generationExpression: 'casewhen(decisionisnull)then1elsenullend',
  }),
  columnDefinition('t_int_content_suggestion', 'decided_at', 'datetime(3)', true),
  columnDefinition('t_post_version_history', 'result_version', 'int', true),
  columnDefinition('t_post_version_history', 'public_update_summary', 'varchar(500)', true),
  columnDefinition('t_post_version_history', 'impact_scope', 'varchar(255)', true),
  columnDefinition('t_post_main', 'latest_effective_content_revision_at', 'datetime(3)', true),
  columnDefinition('t_post_main', 'latest_effective_content_revision_token', 'varchar(64)', true),
  columnDefinition('t_post_version_history', 'quality_signal_revision', 'tinyint', false),
  columnDefinition('t_post_version_history', 'quality_signal_revision_state', 'varchar(32)', true),
  columnDefinition('t_post_version_history', 'quality_signal_effective_at', 'datetime(3)', true),
  columnDefinition('t_post_version_history', 'quality_signal_revision_token', 'varchar(64)', true),
  columnDefinition('t_growth_event', 'event_key', 'varchar(128)', true, {
    characterSet: 'ascii',
    collation: 'ascii_bin',
  }),
  columnDefinition('t_collab_content_need', 'submitted_by_uid', 'bigint', true),
  columnDefinition('t_collab_content_need', 'submitted_at', 'datetime(3)', true),
  columnDefinition('t_collab_content_need', 'submission_resolution_type', 'varchar(24)', true),
  columnDefinition('t_collab_content_need', 'submission_resolution_id', 'bigint', true),
  columnDefinition('t_collab_content_need', 'submission_note', 'varchar(1000)', true),
  columnDefinition('t_collab_content_need', 'reject_reason', 'varchar(500)', true),
  columnDefinition('t_collab_content_need', 'claimed_at', 'datetime(3)', true),
  columnDefinition('t_collab_content_need', 'last_progress_at', 'datetime(3)', true),
  columnDefinition('t_collab_content_need_event', 'id', 'bigint', false),
  columnDefinition('t_collab_content_need_event', 'need_id', 'bigint', false),
  columnDefinition('t_collab_content_need_event', 'event_type', 'varchar(24)', false),
  columnDefinition('t_collab_content_need_event', 'actor_uid', 'bigint', true),
  columnDefinition('t_collab_content_need_event', 'claimant_uid', 'bigint', true),
  columnDefinition('t_collab_content_need_event', 'from_status', 'varchar(24)', true),
  columnDefinition('t_collab_content_need_event', 'to_status', 'varchar(24)', true),
  columnDefinition('t_collab_content_need_event', 'target_type', 'varchar(32)', true),
  columnDefinition('t_collab_content_need_event', 'target_id', 'bigint', true),
  columnDefinition('t_collab_content_need_event', 'note', 'varchar(1000)', true),
  columnDefinition('t_collab_content_need_event', 'visibility_scope', 'varchar(24)', false),
  columnDefinition('t_collab_content_need_event', 'create_time', 'datetime(3)', false),
  columnDefinition('t_collab_content_need_claim_cycle', 'id', 'bigint', false),
  columnDefinition('t_collab_content_need_claim_cycle', 'need_id', 'bigint', false),
  columnDefinition('t_collab_content_need_claim_cycle', 'cycle_no', 'int', false),
  columnDefinition('t_collab_content_need_claim_cycle', 'claimant_uid', 'bigint', false),
  columnDefinition('t_collab_content_need_claim_cycle', 'cycle_status', 'varchar(24)', false),
  columnDefinition('t_collab_content_need_claim_cycle', 'cycle_origin', 'varchar(32)', false),
  columnDefinition('t_collab_content_need_claim_cycle', 'claimed_at', 'datetime(3)', false),
  columnDefinition('t_collab_content_need_claim_cycle', 'last_progress_at', 'datetime(3)', false),
  columnDefinition('t_collab_content_need_claim_cycle', 'ended_at', 'datetime(3)', true),
  columnDefinition('t_collab_content_need_claim_cycle', 'end_reason', 'varchar(500)', true),
  columnDefinition('t_collab_content_need_claim_cycle', 'create_time', 'datetime(3)', false),
  columnDefinition('t_collab_content_need_claim_cycle', 'update_time', 'datetime(3)', false),
  columnDefinition('t_collab_content_need_claim_cycle', 'active_need_guard', 'bigint', true, {
    extra: 'STORED GENERATED',
    generationExpression: "casewhencycle_status='active'thenneed_idelsenullend",
  }),
  columnDefinition('t_collab_content_need_revision', 'id', 'bigint', false),
  columnDefinition('t_collab_content_need_revision', 'need_id', 'bigint', false),
  columnDefinition('t_collab_content_need_revision', 'cycle_id', 'bigint', false),
  columnDefinition('t_collab_content_need_revision', 'cycle_no', 'int', false),
  columnDefinition('t_collab_content_need_revision', 'revision_no', 'int', false),
  columnDefinition('t_collab_content_need_revision', 'submitter_uid', 'bigint', false),
  columnDefinition('t_collab_content_need_revision', 'resolution_type', 'varchar(24)', false),
  columnDefinition('t_collab_content_need_revision', 'resolution_id', 'bigint', false),
  columnDefinition('t_collab_content_need_revision', 'resolution_post_id', 'bigint', true),
  columnDefinition('t_collab_content_need_revision', 'revision_note', 'varchar(1000)', true),
  columnDefinition('t_collab_content_need_revision', 'revision_status', 'varchar(24)', false),
  columnDefinition('t_collab_content_need_revision', 'revision_origin', 'varchar(32)', false),
  columnDefinition('t_collab_content_need_revision', 'submitted_at', 'datetime(3)', false),
  columnDefinition('t_collab_content_need_revision', 'decided_by', 'bigint', true),
  columnDefinition('t_collab_content_need_revision', 'decided_at', 'datetime(3)', true),
  columnDefinition('t_collab_content_need_revision', 'decision_note', 'varchar(1000)', true),
  columnDefinition('t_collab_content_need_revision', 'visibility_scope', 'varchar(24)', false),
  columnDefinition('t_collab_content_need_revision', 'create_time', 'datetime(3)', false),
  columnDefinition('t_collab_content_need_revision', 'update_time', 'datetime(3)', false),
  columnDefinition('t_collab_curation_suggestion', 'pending_guard', 'tinyint', true, {
    extra: 'STORED GENERATED',
    generationExpression: "casewhenreview_status='pending'then1elsenullend",
  }),
  columnDefinition('t_collab_governance_case', 'pending_guard', 'tinyint', true, {
    extra: 'STORED GENERATED',
    generationExpression: "casewhencase_status='pending'then1elsenullend",
  }),
  columnDefinition('t_collab_governance_case', 'appeal_guard', 'bigint', true, {
    extra: 'STORED GENERATED',
    generationExpression: "casewhencase_type='appeal'thenparent_case_idelsenullend",
  }),
  columnDefinition('t_community_role_application', 'active_guard', 'tinyint', true, {
    extra: 'STORED GENERATED',
    generationExpression: "casewhenapplication_status='submitted'then1elsenullend",
  }),
  columnDefinition('t_community_role_grant', 'active_guard', 'tinyint', true, {
    extra: 'STORED GENERATED',
    generationExpression: "casewhengrant_statusin'active''suspended'then1elsenullend",
  }),
]

const indexDefinitions = [
  indexDefinition('t_int_post_trust_state', 'idx_trust_state_question_status', false,
    'question_status,update_time,post_id'),
  indexDefinition('t_int_post_trust_state', 'idx_trust_state_freshness_status', false,
    'freshness_status,update_time,post_id'),
  indexDefinition('t_int_post_trust_state', 'idx_trust_state_accepted_comment', false,
    'accepted_comment_id'),
  indexDefinition('t_int_post_trust_state', 'idx_trust_state_duplicate_post', false,
    'duplicate_post_id'),
  indexDefinition('t_int_post_trust_state', 'idx_trust_state_successor_post', false,
    'successor_post_id'),
  indexDefinition('t_int_post_useful_feedback', 'uk_user_post', true, 'user_id,post_id'),
  indexDefinition('t_int_post_useful_feedback', 'idx_useful_feedback_post_reason', false,
    'post_id,reason'),
  indexDefinition('t_int_post_useful_feedback', 'idx_useful_feedback_author_time', false,
    'post_author_id,create_time,post_id'),
  indexDefinition('t_int_content_suggestion', 'uk_pending_suggestion', true,
    'post_id,submitter_uid,suggestion_type,normalized_content_hash,pending_guard'),
  indexDefinition('t_int_content_suggestion', 'idx_content_suggestion_mine_time', false,
    'post_id,submitter_uid,update_time,id'),
  indexDefinition('t_int_content_suggestion', 'idx_content_suggestion_mine_decided', false,
    'post_id,submitter_uid,decided_at,id'),
  indexDefinition('t_int_content_suggestion', 'idx_content_suggestion_author_pending', false,
    'post_author_id,decision,update_time,id'),
  indexDefinition('t_int_content_suggestion', 'idx_content_suggestion_author_time', false,
    'post_id,update_time,id'),
  indexDefinition('t_int_content_suggestion', 'idx_content_suggestion_public', false,
    'post_id,decided_at,id'),
  indexDefinition('t_int_content_suggestion', 'idx_content_suggestion_type_status', false,
    'post_id,suggestion_type,decision'),
  indexDefinition('t_post_version_history', 'uk_post_result_version', true,
    'post_id,result_version'),
  indexDefinition('t_post_version_history', 'idx_post_public_update', false,
    'post_id,result_version,create_time,id'),
  indexDefinition('t_post_version_history', 'idx_post_quality_signal_revision', false,
    'post_id,quality_signal_revision,quality_signal_revision_state,quality_signal_effective_at,result_version,id'),
  indexDefinition('t_growth_event', 'uk_growth_event_key', true, 'event_key'),
  indexDefinition('t_collab_content_need_follow', 'uk_collab_need_follow', true,
    'need_id,uid'),
  indexDefinition('t_collab_content_need_follow', 'idx_collab_need_follow_uid_active_id', false,
    'uid,active,id'),
  indexDefinition('t_collab_content_need_follow', 'idx_collab_need_follow_need_active_id', false,
    'need_id,active,id'),
  indexDefinition('t_collab_content_need_event', 'idx_collab_need_event_need', false,
    'need_id,id'),
  indexDefinition('t_collab_content_need_event', 'idx_collab_need_event_visibility', false,
    'need_id,visibility_scope,id'),
  indexDefinition('t_collab_content_need_claim_cycle', 'uk_collab_need_claim_cycle_no', true,
    'need_id,cycle_no'),
  indexDefinition('t_collab_content_need_claim_cycle', 'uk_collab_need_claim_cycle_active', true,
    'active_need_guard'),
  indexDefinition('t_collab_content_need_claim_cycle', 'idx_collab_need_claim_cycle_need', false,
    'need_id,cycle_no,id'),
  indexDefinition('t_collab_content_need_claim_cycle', 'idx_collab_need_claim_cycle_status', false,
    'need_id,cycle_status,id'),
  indexDefinition('t_collab_content_need_claim_cycle', 'idx_collab_need_claim_cycle_claimant', false,
    'claimant_uid,cycle_status,update_time,id'),
  indexDefinition('t_collab_content_need_revision', 'uk_collab_need_revision_no', true,
    'cycle_id,revision_no'),
  indexDefinition('t_collab_content_need_revision', 'idx_collab_need_revision_need', false,
    'need_id,cycle_no,revision_no,id'),
  indexDefinition('t_collab_content_need_revision', 'idx_collab_need_revision_cycle_status', false,
    'cycle_id,revision_status,id'),
  indexDefinition('t_collab_content_need_revision', 'idx_collab_need_revision_submitter', false,
    'submitter_uid,create_time,id'),
  indexDefinition('t_collab_content_need_revision', 'idx_collab_need_revision_visibility', false,
    'need_id,visibility_scope,id'),
  indexDefinition('t_collab_series_member', 'uk_collab_series_member', true,
    'series_id,uid'),
  indexDefinition('t_collab_series_submission', 'uk_collab_series_submission', true,
    'series_id,post_id'),
  indexDefinition('t_collab_series_contribution', 'uk_collab_series_contribution_source', true,
    'source_submission_id'),
  indexDefinition('t_collab_activity_submission', 'uk_collab_activity_submission', true,
    'activity_id,post_id'),
  indexDefinition('t_collab_curation_suggestion', 'uk_collab_curation_pending', true,
    'topic_id,post_id,submitter_uid,pending_guard'),
  indexDefinition('t_collab_topic_post', 'uk_collab_topic_post', true,
    'topic_id,post_id'),
  indexDefinition('t_collab_topic_post', 'uk_collab_topic_post_source', true,
    'source_type,source_id'),
  indexDefinition('t_collab_office_hour_reservation', 'uk_collab_office_reservation_once', true,
    'office_hour_id,attendee_uid'),
  indexDefinition('t_collab_office_hour_feedback', 'uk_collab_office_feedback_author', true,
    'reservation_id,author_uid'),
  indexDefinition('t_collab_discussion_vote', 'uk_collab_discussion_vote', true,
    'discussion_id,uid'),
  indexDefinition('t_collab_governance_case', 'uk_collab_case_pending', true,
    'case_type,target_type,target_id,submitter_uid,pending_guard'),
  indexDefinition('t_collab_governance_case', 'uk_collab_case_single_appeal', true,
    'appeal_guard'),
  indexDefinition('t_incentive_account', 'uk_incentive_account_scope', true,
    'user_id,account_type,domain_code'),
  indexDefinition('t_incentive_account', 'idx_incentive_account_type_domain', false,
    'account_type,domain_code,user_id'),
  indexDefinition('t_incentive_ledger', 'uk_incentive_ledger_idempotency', true,
    'idempotency_key'),
  indexDefinition('t_incentive_ledger', 'uk_incentive_ledger_reversal', true,
    'reversed_entry_id'),
  indexDefinition('t_incentive_ledger', 'idx_incentive_ledger_user_time', false,
    'user_id,create_time,id'),
  indexDefinition('t_incentive_ledger', 'idx_incentive_ledger_account_time', false,
    'account_id,create_time,id'),
  indexDefinition('t_incentive_ledger', 'idx_incentive_ledger_reference', false,
    'reference_type,reference_id'),
  indexDefinition('t_incentive_freeze_record', 'idx_incentive_freeze_account_status', false,
    'account_id,freeze_status,blocks_spending,freeze_amount'),
  indexDefinition('t_incentive_invalidation_job', 'idx_incentive_invalidation_reference', false,
    'reference_type,reference_id'),
  indexDefinition('t_virtual_benefit_order', 'idx_virtual_benefit_order_benefit', false,
    'benefit_id'),
  indexDefinition('t_quota_bounty_submission', 'idx_quota_submission_applicant', false,
    'applicant_uid,create_time,id'),
  indexDefinition('t_quota_bounty_appeal', 'idx_quota_bounty_appeal_applicant', false,
    'applicant_uid,create_time,id'),
  indexDefinition('t_community_role_application', 'uk_community_role_active_application', true,
    'applicant_uid,role_code,domain_code,active_guard'),
  indexDefinition('t_community_role_grant', 'uk_community_role_active_grant', true,
    'user_id,role_code,domain_code,active_guard'),
]

const foreignKeyDefinitions = [
  foreignKeyDefinition('t_int_post_trust_state', 'fk_trust_state_post',
    'post_id', 't_post_main', 'id', 'CASCADE'),
  foreignKeyDefinition('t_int_post_trust_state', 'fk_trust_state_accepted_comment',
    'accepted_comment_id', 't_int_comment', 'id', 'SET NULL'),
  foreignKeyDefinition('t_int_post_trust_state', 'fk_trust_state_duplicate_post',
    'duplicate_post_id', 't_post_main', 'id', 'SET NULL'),
  foreignKeyDefinition('t_int_post_trust_state', 'fk_trust_state_successor_post',
    'successor_post_id', 't_post_main', 'id', 'SET NULL'),
  foreignKeyDefinition('t_int_post_useful_feedback', 'fk_useful_feedback_post',
    'post_id', 't_post_main', 'id', 'CASCADE'),
  foreignKeyDefinition('t_int_post_useful_feedback', 'fk_useful_feedback_user',
    'user_id', 't_user_account', 'id', 'CASCADE'),
  foreignKeyDefinition('t_int_content_suggestion', 'fk_content_suggestion_post',
    'post_id', 't_post_main', 'id', 'CASCADE'),
  foreignKeyDefinition('t_int_content_suggestion', 'fk_content_suggestion_submitter',
    'submitter_uid', 't_user_account', 'id', 'RESTRICT'),
  foreignKeyDefinition('t_int_content_suggestion', 'fk_content_suggestion_decider',
    'post_author_id', 't_user_account', 'id', 'RESTRICT'),
  foreignKeyDefinition('t_int_content_suggestion', 'fk_content_suggestion_result_version',
    'post_id,result_version', 't_post_version_history', 'post_id,result_version', 'RESTRICT'),
]

const checkConstraintDefinitions = [
  checkConstraintDefinition('t_search_index_rebuild_task',
    'chk_search_index_rebuild_status',
    "task_statusin'pending''running''succeeded''failed'"),
  checkConstraintDefinition('t_projection_reconcile_request',
    'chk_projection_reconcile_status',
    "request_statusin'pending''completed'"),
  checkConstraintDefinition('t_user_subscription_preference',
    'chk_user_subscription_preference_source_type',
    "source_typein'user''topic''discussion''need''series''activity'"),
  checkConstraintDefinition('t_user_subscription_preference',
    'chk_user_subscription_preference_delivery_mode',
    "delivery_modein'immediate''digest''muted'"),
  checkConstraintDefinition('t_user_subscription_preference',
    'chk_user_subscription_preference_deleted',
    'is_deletedin01'),
  checkConstraintDefinition('t_feed_feedback_preference', 'chk_feed_feedback_action',
    "actionin'hide''less_like_this'"),
  checkConstraintDefinition('t_feed_feedback_preference', 'chk_feed_feedback_target_type',
    "target_typein'post''domain'"),
  checkConstraintDefinition('t_incentive_account', 'chk_incentive_account_balances',
    'total_balance>=0andavailable_balance>=0andfrozen_balance>=0andrecovery_debt>=0andtotal_balance=available_balance+frozen_balance'),
  checkConstraintDefinition('t_virtual_benefit_catalog', 'chk_virtual_benefit_stock',
    'total_stockisnullortotal_stock>=0andavailable_stock>=0andavailable_stock<=total_stock'),
  checkConstraintDefinition('t_virtual_benefit_order', 'chk_virtual_benefit_order_status',
    "order_statusin'created''reserved''delivered''cancelled''refunded'"),
  checkConstraintDefinition('t_virtual_benefit_catalog', 'chk_virtual_benefit_stock_null_pair',
    'total_stockisnullandavailable_stockisnullortotal_stockisnotnullandavailable_stockisnotnullandtotal_stock>=0andavailable_stock>=0andavailable_stock<=total_stock'),
  checkConstraintDefinition('t_virtual_benefit_order', 'chk_virtual_benefit_order_cost',
    'quantity>0andunit_point_cost>0andtotal_point_cost>0andcasttotal_point_costasdecimal650=castquantityasdecimal650*castunit_point_costasdecimal650'),
]

const triggerDefinitions = [
  triggerDefinition(
    't_incentive_ledger',
    'trg_incentive_ledger_block_update',
    'BEFORE',
    'UPDATE',
    "signalsqlstate'45000'setmessage_text='t_incentive_ledgerisappend-only'",
  ),
  triggerDefinition(
    't_incentive_ledger',
    'trg_incentive_ledger_block_delete',
    'BEFORE',
    'DELETE',
    "signalsqlstate'45000'setmessage_text='t_incentive_ledgerisappend-only'",
  ),
]

const expectations = [
  ...tables([
    'flyway_schema_history',
    't_admin_audit_log',
    't_search_index_rebuild_task',
    't_post_reference',
    't_post_knowledge_relation',
    't_int_post_outcome',
    't_projection_reconcile_request',
    't_moderation_keyword',
    't_moderation_keyword_hit',
    't_user_moderation_state',
    't_user_prep_target',
    't_interview_question',
    't_post_report',
    't_comment_report',
    't_tag',
    't_community_topic',
    't_community_topic_tag',
    't_community_topic_follow',
    't_review_queue',
    't_mock_interview_answer',
    't_ai_extract_task',
    't_domain_moderator',
    't_domain_config',
    't_growth_event',
    't_post_version_history',
    't_post_extension',
    't_user_task_state',
    't_content_assist_record',
    't_content_series',
    't_content_series_post',
    't_feed_recommend_support_stat',
    't_feed_feedback_preference',
    't_expert_cert_application',
    't_int_contact_request',
    't_user_privacy_setting',
    't_user_subscription_preference',
    't_int_discussion_follow',
    't_int_favorite',
    't_int_favorite_folder',
    't_int_comment_quality_signal',
    't_int_comment_helpful',
    't_int_post_trust_state',
    't_int_post_useful_feedback',
    't_int_content_suggestion',
    't_collab_content_need',
    't_collab_content_need_follow',
    't_collab_content_need_event',
    't_collab_content_need_claim_cycle',
    't_collab_content_need_revision',
    't_collab_series',
    't_collab_series_member',
    't_collab_series_submission',
    't_collab_series_contribution',
    't_collab_activity',
    't_collab_activity_submission',
    't_collab_curation_suggestion',
    't_collab_topic_post',
    't_collab_office_hour',
    't_collab_office_hour_reservation',
    't_collab_office_hour_feedback',
    't_collab_discussion',
    't_collab_discussion_option',
    't_collab_discussion_vote',
    't_collab_governance_case',
    't_incentive_account',
    't_incentive_ledger',
    't_incentive_recovery_debt',
    't_incentive_reward_rule',
    't_incentive_reward_batch',
    't_incentive_reward_inbox',
    't_incentive_invalidation_job',
    't_incentive_reward_guard',
    't_incentive_freeze_record',
    't_incentive_reconciliation_run',
    't_incentive_reconciliation_cursor',
    't_incentive_reconciliation_item',
    't_incentive_appeal',
    't_incentive_risk_scan_cursor',
    't_incentive_risk_scan_run',
    't_incentive_risk_finding',
    't_virtual_benefit_catalog',
    't_virtual_benefit_order',
    't_virtual_benefit_order_history',
    't_virtual_benefit_entitlement',
    't_virtual_benefit_entitlement_usage',
    't_thank_ticket_daily',
    't_thank_action',
    't_incentive_domain_policy',
    't_bounty_platform_budget_guard',
    't_bounty_user_budget_guard',
    't_quota_bounty',
    't_quota_bounty_submission',
    't_quota_bounty_appeal',
    't_community_role_definition',
    't_community_role_metric',
    't_community_role_application',
    't_community_role_grant',
    't_community_role_grant_history',
  ]),
  ...columns('t_community_topic', [
    'domain',
    'allowed_domains',
  ]),
  ...columns('t_tag', [
    'tag_status',
    'recommended',
    'synonyms',
    'merge_target_id',
    'update_time',
  ]),
  ...columns('t_mock_interview_answer', [
    'ai_review_task_id',
    'ai_review_fallback_used',
    'ai_review_duration_ms',
    'ai_review_prompt_tokens',
    'ai_review_completion_tokens',
    'ai_review_estimated_cost_micros',
    'ai_review_error_code',
  ]),
  ...columns('t_ai_extract_task', [
    'provider',
    'fallback_used',
    'duration_ms',
    'prompt_tokens',
    'completion_tokens',
    'estimated_cost_micros',
    'error_code',
  ]),
  ...columns('t_domain_moderator', [
    'id',
    'uid',
    'domain',
    'enabled',
    'created_by',
    'create_time',
    'update_time',
  ]),
  ...columns('t_domain_config', [
    'domain',
    'domain_name',
    'domain_slug',
    'description',
    'sort_order',
    'enabled',
    'risk_level',
    'posting_notice',
    'browse_notice',
    'interaction_notice',
    'created_by',
    'updated_by',
    'create_time',
    'update_time',
  ]),
  ...columns('t_growth_event', [
    'id',
    'event_key',
    'event_type',
    'uid',
    'domain',
    'content_id',
    'target_type',
    'target_value',
    'source_page',
    'ext_json',
    'create_time',
  ]),
  ...columns('t_post_version_history', [
    'result_version',
    'public_update_summary',
    'impact_scope',
    'quality_signal_revision',
    'quality_signal_revision_state',
    'quality_signal_effective_at',
    'quality_signal_revision_token',
  ]),
  ...columns('t_post_main', [
    'latest_effective_content_revision_at',
    'latest_effective_content_revision_token',
  ]),
  ...columns('t_post_extension', [
    'domain',
  ]),
  ...columns('t_user_task_state', [
    'id',
    'uid',
    'task_type',
    'task_code',
    'task_date',
    'completed',
    'complete_source',
    'complete_ref_id',
    'first_completed_time',
    'create_time',
    'update_time',
  ]),
  ...columns('t_content_assist_record', [
    'id',
    'uid',
    'scene',
    'provider',
    'assist_status',
    'domain',
    'content_length',
    'content_hash',
    'prompt_tokens',
    'completion_tokens',
    'estimated_cost_micros',
    'error_code',
    'create_time',
    'update_time',
  ]),
  ...columns('t_content_series', [
    'id',
    'creator_uid',
    'title',
    'description',
    'domain',
    'cover_url',
    'create_time',
    'update_time',
    'is_deleted',
  ]),
  ...columns('t_content_series_post', [
    'id',
    'series_id',
    'post_id',
    'sort_order',
    'create_time',
    'update_time',
    'is_deleted',
  ]),
  ...columns('t_feed_recommend_support_stat', [
    'id',
    'viewer_uid',
    'domain',
    'delivered_item_count',
    'support_hit_item_count',
    'create_time',
    'update_time',
  ]),
  ...columns('t_feed_feedback_preference', [
    'id',
    'uid',
    'post_id',
    'action',
    'target_type',
    'target_id',
    'reason',
    'expires_at',
    'create_time',
    'update_time',
  ]),
  ...columns('t_expert_cert_application', [
    'id',
    'applicant_uid',
    'domain',
    'status',
    'evidence_summary',
    'evidence_links_json',
    'eligibility_passed',
    'eligibility_summary',
    'eligibility_snapshot_json',
    'risk_acknowledged',
    'risk_warning',
    'reviewer_uid',
    'review_note',
    'review_time',
    'revoked_by',
    'revoke_note',
    'revoked_time',
    'create_time',
    'update_time',
    'is_deleted',
    'active_guard',
  ]),
  ...columns('t_operation_curation_item', ['active_guard']),
  ...columns('t_operation_slot_item', ['active_guard']),
  ...columns('t_int_contact_request', [
    'id',
    'requester_uid',
    'receiver_uid',
    'request_status',
    'dedup_key',
    'create_time',
    'update_time',
    'is_deleted',
  ]),
  ...columns('t_user_privacy_setting', [
    'accept_contact_request',
    'contact_request_policy',
    'contact_request_daily_limit',
  ]),
  ...columns('t_user_subscription_preference', [
    'id',
    'uid',
    'source_type',
    'source_id',
    'delivery_mode',
    'expires_at',
    'create_time',
    'update_time',
    'is_deleted',
  ]),
  ...columns('t_projection_reconcile_request', [
    'id',
    'resource_id',
    'operator_uid',
    'projection_type',
    'idempotency_key',
    'request_fingerprint',
    'request_status',
    'result_json',
    'create_time',
    'update_time',
  ]),
  ...columns('t_search_index_rebuild_task', [
    'task_id',
    'task_type',
    'task_status',
    'operator_uid',
    'checkpoint_id',
    'indexed_count',
    'failed_count',
    'total_count',
    'index_name',
    'last_error',
    'lock_owner',
    'lock_until',
    'heartbeat_time',
    'started_at',
    'finished_at',
    'active_key',
    'create_time',
    'update_time',
  ]),
  ...columns('t_int_content_suggestion', [
    'base_version',
    'target_scope',
    'target_locator',
    'expected_change',
    'resolution',
    'delivery_status',
  ]),
  ...columns('t_post_reference', [
    'id', 'post_id', 'owner_uid', 'reference_type', 'title', 'url',
    'normalized_url', 'source_domain', 'note', 'broken_reason',
    'reference_status', 'sort_order', 'revision', 'last_confirmed_at',
    'create_time', 'update_time', 'is_deleted', 'active_guard',
  ]),
  ...columns('t_post_knowledge_relation', [
    'id', 'source_post_id', 'target_post_id', 'relation_type', 'reason_text',
    'proposer_uid', 'review_status', 'visibility_status', 'reviewer_uid',
    'review_note', 'reviewed_at', 'risk_level', 'create_time', 'update_time',
    'is_deleted', 'effective_guard',
  ]),
  ...columns('t_int_post_outcome', [
    'id', 'post_id', 'uid', 'outcome_type', 'context_note', 'result_note',
    'visibility', 'publication_status', 'consented_at', 'reviewer_uid',
    'review_note', 'reviewed_at', 'follow_up_at', 'outcome_status', 'revision',
    'create_time', 'update_time', 'is_deleted', 'effective_guard',
  ]),
  ...columns('t_int_discussion_follow', [
    'id',
    'uid',
    'post_id',
    'follow_status',
    'last_read_comment_id',
    'last_notified_comment_id',
    'create_time',
    'update_time',
    'is_deleted',
  ]),
  ...columns('t_int_favorite', [
    'folder_id',
    'sort_order',
    'update_time',
  ]),
  ...columns('t_int_favorite_folder', [
    'id',
    'user_id',
    'name',
    'visibility',
    'sort_order',
    'post_count',
    'is_default',
    'is_deleted',
    'default_active_key',
    'name_active_key',
    'create_time',
    'update_time',
  ]),
  ...columns('t_int_comment', ['helpful_count']),
  ...columns('t_int_comment_quality_signal', [
    'id',
    'post_id',
    'comment_id',
    'root_id',
    'signal_type',
    'signal_status',
    'operator_uid',
    'operator_role',
    'source',
    'create_time',
    'update_time',
    'is_deleted',
  ]),
  ...columns('t_int_comment_helpful', [
    'id',
    'uid',
    'post_id',
    'comment_id',
    'helpful_status',
    'create_time',
    'update_time',
    'is_deleted',
  ]),
  ...columns('t_int_post_trust_state', [
    'post_id',
    'question_status',
    'accepted_comment_id',
    'duplicate_post_id',
    'freshness_status',
    'successor_post_id',
    'last_confirmed_at',
    'suggestions_open',
    'create_time',
    'update_time',
  ]),
  ...columns('t_int_post_useful_feedback', [
    'id',
    'user_id',
    'post_id',
    'post_author_id',
    'reason',
    'create_time',
    'update_time',
  ]),
  ...columns('t_int_content_suggestion', [
    'id',
    'post_id',
    'post_author_id',
    'submitter_uid',
    'suggestion_type',
    'detail',
    'normalized_content_hash',
    'source_url',
    'allow_public_attribution',
    'decision',
    'author_reply',
    'public_note',
    'result_version',
    'pending_dedup_key',
    'pending_guard',
    'decided_at',
    'create_time',
    'update_time',
  ]),
  ...indexes('t_post_report', ['idx_post_reporter_status']),
  ...indexes('t_comment_report', ['idx_comment_reporter_status']),
  ...indexes('t_interview_question', ['idx_status_time']),
  ...indexes('t_tag', ['idx_tag_status_recommend', 'idx_tag_merge_target']),
  ...indexes('t_community_topic', ['uk_topic_slug', 'idx_topic_status_sort', 'idx_topic_featured_sort']),
  ...indexes('t_community_topic', ['idx_community_topic_domain']),
  ...indexes('t_community_topic_tag', ['uk_topic_tag', 'idx_topic_tag_topic', 'idx_topic_tag_tag']),
  ...indexes('t_community_topic_follow', ['uk_topic_follow_user', 'idx_topic_follow_uid', 'idx_topic_follow_topic']),
  ...indexes('t_review_queue', [
    'uk_review_queue_source',
    'idx_review_queue_status_priority',
    'idx_review_queue_source_status',
    'idx_review_queue_assignee_status',
    'idx_review_queue_risk_status',
  ]),
  ...indexes('t_domain_moderator', [
    'uk_domain_moderator_uid_domain',
    'idx_domain_moderator_domain_enabled',
    'idx_domain_moderator_uid_enabled',
  ]),
  ...indexes('t_domain_config', [
    'uk_domain_config_slug',
    'idx_domain_config_enabled_sort',
    'idx_domain_config_risk_enabled',
  ]),
  ...indexes('t_growth_event', [
    'uk_growth_event_key',
    'idx_growth_event_type_time',
    'idx_growth_event_domain_time',
    'idx_growth_event_uid_time',
    'idx_growth_event_content_time',
  ]),
  ...indexes('t_post_version_history', [
    'uk_post_result_version',
    'idx_post_public_update',
    'idx_post_quality_signal_revision',
  ]),
  ...indexes('t_post_extension', ['idx_post_extension_domain_post']),
  ...indexes('t_user_task_state', ['uk_user_task_scope_code_day', 'idx_user_task_scope_date']),
  ...indexes('t_content_assist_record', [
    'idx_content_assist_scene_time',
    'idx_content_assist_status_time',
    'idx_content_assist_provider_time',
    'idx_content_assist_uid_time',
  ]),
  ...indexes('t_content_series', [
    'idx_content_series_creator_update',
    'idx_content_series_domain_update',
  ]),
  ...indexes('t_content_series_post', [
    'uk_content_series_post',
    'idx_content_series_post_series_sort',
    'idx_content_series_post_post',
  ]),
  ...indexes('t_feed_recommend_support_stat', [
    'idx_feed_recommend_support_stat_create_time',
    'idx_feed_recommend_support_stat_domain_create_time',
    'idx_feed_recommend_support_stat_viewer_create_time',
  ]),
  ...indexes('t_feed_feedback_preference', [
    'uk_feed_feedback_uid_post',
    'idx_feed_feedback_uid_action_active',
    'idx_feed_feedback_uid_target_active',
    'idx_feed_feedback_uid_cursor',
    'idx_feed_feedback_quality_signal_v32',
  ]),
  ...indexes('t_expert_cert_application', [
    'uk_expert_cert_active_guard',
    'idx_expert_cert_applicant_domain',
    'idx_expert_cert_review_queue',
  ]),
  ...indexes('t_user_follow', [
    'idx_following_page',
    'idx_follower_page',
  ]),
  ...indexes('t_notif_message', [
    'idx_receiver_list',
    'idx_receiver_unread_latest',
  ]),
  ...indexes('t_int_contact_request', [
    'uk_contact_request_dedup',
    'idx_contact_request_receiver_status',
    'idx_contact_request_requester_status',
    'idx_contact_request_pair_status',
    'idx_contact_request_requester_day',
    'idx_contact_request_receiver_page',
    'idx_contact_request_requester_page',
  ]),
  ...indexes('t_user_privacy_setting', ['idx_contact_request_policy']),
  ...indexes('t_user_subscription_preference', [
    'uk_user_subscription_preference_source',
    'idx_user_subscription_preference_mode',
    'idx_user_subscription_preference_expiry',
  ]),
  ...indexes('t_projection_reconcile_request', [
    'uk_projection_reconcile_resource',
    'idx_projection_reconcile_operator_time',
    'idx_projection_reconcile_type_time',
  ]),
  ...indexes('t_search_index_rebuild_task', [
    'uk_search_index_rebuild_active',
    'idx_search_index_rebuild_status_time',
    'idx_search_index_rebuild_lease',
  ]),
  ...indexes('t_post_reference', [
    'uk_post_reference_active_url',
    'idx_post_reference_public',
    'idx_post_reference_owner',
  ]),
  ...indexes('t_post_knowledge_relation', [
    'uk_post_knowledge_relation_effective',
    'idx_post_knowledge_relation_source',
    'idx_post_knowledge_relation_target',
    'idx_post_knowledge_relation_review',
    'idx_post_knowledge_relation_proposer',
  ]),
  ...indexes('t_int_post_outcome', [
    'uk_int_post_outcome_current',
    'idx_int_post_outcome_public',
    'idx_int_post_outcome_follow_up',
  ]),
  ...indexes('t_int_discussion_follow', [
    'uk_discussion_follow_user_post',
    'idx_discussion_follow_post_status',
    'idx_discussion_follow_uid_status',
    'idx_discussion_follow_notify_page',
    'idx_discussion_follow_uid_page',
  ]),
  ...indexes('t_int_favorite', [
    'uk_user_post',
    'idx_user_folder_sort',
    'idx_folder_time',
    'idx_favorite_user_page',
    'idx_favorite_user_folder_page',
  ]),
  ...indexes('t_int_favorite_folder', [
    'uk_favorite_folder_active_default',
    'uk_favorite_folder_active_name',
    'idx_favorite_folder_user_sort',
    'idx_favorite_folder_user_default',
  ]),
  ...indexes('t_int_like', ['idx_like_user_page']),
  ...indexes('t_int_comment', ['idx_comment_quality_roots']),
  ...indexes('t_int_comment_quality_signal', [
    'uk_comment_quality_signal_comment_type',
    'idx_comment_quality_signal_post_type_status',
    'idx_comment_quality_signal_root_type_status',
    'idx_comment_quality_signal_operator_time',
    'idx_comment_quality_post_comment',
  ]),
  ...indexes('t_int_comment_helpful', [
    'uk_comment_helpful_uid_comment',
    'idx_comment_helpful_comment_status',
    'idx_comment_helpful_user_status',
    'idx_comment_helpful_post_comment',
  ]),
  ...indexes('t_int_post_trust_state', [
    'idx_trust_state_question_status',
    'idx_trust_state_freshness_status',
    'idx_trust_state_accepted_comment',
    'idx_trust_state_duplicate_post',
    'idx_trust_state_successor_post',
  ]),
  ...indexes('t_int_post_useful_feedback', [
    'uk_user_post',
    'idx_useful_feedback_post_reason',
    'idx_useful_feedback_author_time',
  ]),
  ...indexes('t_int_content_suggestion', [
    'uk_pending_suggestion',
    'idx_content_suggestion_mine_time',
    'idx_content_suggestion_mine_decided',
    'idx_content_suggestion_author_pending',
    'idx_content_suggestion_author_time',
    'idx_content_suggestion_public',
    'idx_content_suggestion_type_status',
  ]),
  ...primaryKeys('t_domain_moderator', ['id']),
  ...primaryKeys('t_domain_config', ['domain']),
  ...primaryKeys('t_growth_event', ['id']),
  ...primaryKeys('t_post_version_history', ['id']),
  ...primaryKeys('t_user_task_state', ['id']),
  ...primaryKeys('t_content_assist_record', ['id']),
  ...primaryKeys('t_content_series', ['id']),
  ...primaryKeys('t_content_series_post', ['id']),
  ...primaryKeys('t_feed_recommend_support_stat', ['id']),
  ...primaryKeys('t_feed_feedback_preference', ['id']),
  ...primaryKeys('t_expert_cert_application', ['id']),
  ...primaryKeys('t_int_contact_request', ['id']),
  ...primaryKeys('t_user_privacy_setting', ['user_id']),
  ...primaryKeys('t_user_subscription_preference', ['id']),
  ...primaryKeys('t_projection_reconcile_request', ['id']),
  ...primaryKeys('t_search_index_rebuild_task', ['task_id']),
  ...primaryKeys('t_post_reference', ['id']),
  ...primaryKeys('t_post_knowledge_relation', ['id']),
  ...primaryKeys('t_int_post_outcome', ['id']),
  ...primaryKeys('t_int_discussion_follow', ['id']),
  ...primaryKeys('t_int_favorite', ['id']),
  ...primaryKeys('t_int_favorite_folder', ['id']),
  ...primaryKeys('t_int_comment_quality_signal', ['id']),
  ...primaryKeys('t_int_comment_helpful', ['id']),
  ...primaryKeys('t_int_post_trust_state', ['post_id']),
  ...primaryKeys('t_int_post_useful_feedback', ['id']),
  ...primaryKeys('t_int_content_suggestion', ['id']),
  ...primaryKeys('t_collab_content_need_claim_cycle', ['id']),
  ...primaryKeys('t_collab_content_need_revision', ['id']),
  ...foreignKeys('t_int_post_trust_state', [
    'fk_trust_state_post',
    'fk_trust_state_accepted_comment',
    'fk_trust_state_duplicate_post',
    'fk_trust_state_successor_post',
  ]),
  ...foreignKeys('t_int_post_useful_feedback', [
    'fk_useful_feedback_post',
    'fk_useful_feedback_user',
  ]),
  ...foreignKeys('t_int_content_suggestion', [
    'fk_content_suggestion_post',
    'fk_content_suggestion_submitter',
    'fk_content_suggestion_decider',
    'fk_content_suggestion_result_version',
  ]),
]

const sql = `
SELECT CONCAT('table:', table_name) AS item, COUNT(*) AS ready
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (${sqlList(expectations.filter((item) => item.type === 'table').map((item) => item.table))})
GROUP BY table_name
UNION ALL
SELECT CONCAT('column:', table_name, '.', column_name) AS item, COUNT(*) AS ready
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND CONCAT(table_name, '.', column_name) IN (${sqlList(expectations.filter((item) => item.type === 'column').map((item) => `${item.table}.${item.name}`))})
GROUP BY table_name, column_name
UNION ALL
SELECT CONCAT('index:', table_name, '.', index_name) AS item, COUNT(*) AS ready
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND CONCAT(table_name, '.', index_name) IN (${sqlList(expectations.filter((item) => item.type === 'index').map((item) => `${item.table}.${item.name}`))})
GROUP BY table_name, index_name
UNION ALL
SELECT CONCAT('primaryKey:', table_name, '.', column_name) AS item, COUNT(*) AS ready
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND column_key = 'PRI'
  AND CONCAT(table_name, '.', column_name) IN (${sqlList(expectations.filter((item) => item.type === 'primaryKey').map((item) => `${item.table}.${item.name}`))})
GROUP BY table_name, column_name
UNION ALL
SELECT CONCAT('foreignKey:', table_name, '.', constraint_name) AS item, COUNT(*) AS ready
FROM information_schema.referential_constraints
WHERE constraint_schema = DATABASE()
  AND CONCAT(table_name, '.', constraint_name) IN (${sqlList(expectations.filter((item) => item.type === 'foreignKey').map((item) => `${item.table}.${item.name}`))})
GROUP BY table_name, constraint_name;
`

const output = runMysql(sql, 'schema readiness check failed to query MySQL')

const found = new Map()
if (output) {
  for (const line of output.split(/\r?\n/)) {
    const [item, ready] = line.split(/\t/)
    found.set(item, Number(ready) > 0)
  }
}

const checks = expectations.map((item) => {
  const key = itemKey(item)
  return {
    type: item.type,
    table: item.table,
    name: item.name || item.table,
    key,
    ready: found.get(key) === true,
    migration: item.migration,
  }
})
checks.push(...inspectSchemaDefinitions())
const lifecycle = inspectFlywayHistory(found.get('table:flyway_schema_history') === true)
checks.push(...migrationAssetChecks, ...lifecycle.checks)
const missing = checks.filter((item) => !item.ready)
const byMigration = missing.reduce((acc, item) => {
  const migration = item.migration || 'unknown'
  acc[migration] ||= []
  acc[migration].push(item.key)
  return acc
}, {})

if (config.json) {
  console.log(JSON.stringify({
    database: config.database,
    ready: missing.length === 0,
    checked: checks.length,
    missing: missing.map((item) => item.key),
    byMigration,
    migrationLifecycle: lifecycle.summary,
  }, null, 2))
} else {
  console.log(`Schema readiness checked ${checks.length} item(s) in database '${config.database}'.`)
  if (missing.length === 0) {
    console.log('Schema readiness passed.')
  } else {
    console.error('Schema readiness failed. Missing item(s):')
    for (const [migration, items] of Object.entries(byMigration)) {
      console.error(`- ${migration}`)
      for (const item of items) console.error(`  - ${item}`)
    }
  }
}

if (missing.length > 0) process.exit(1)

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

function tables(values) {
  return values.map((table) => ({ type: 'table', table, migration: migrationForTable(table) }))
}

function columns(table, values) {
  return values.map((name) => ({ type: 'column', table, name, migration: migrationForColumn(table, name) }))
}

function indexes(table, values) {
  return values.map((name) => ({ type: 'index', table, name, migration: migrationForIndex(table, name) }))
}

function primaryKeys(table, values) {
  return values.map((name) => ({ type: 'primaryKey', table, name, migration: migrationForConstraint(table, name) }))
}

function foreignKeys(table, values) {
  return values.map((name) => ({ type: 'foreignKey', table, name, migration: migrationForConstraint(table, name) }))
}

function columnDefinition(table, name, columnType, nullable, options = {}) {
  return {
    type: 'columnDefinition',
    table,
    name,
    columnType,
    nullable,
    characterSet: options.characterSet || null,
    collation: options.collation || null,
    extra: options.extra || '',
    generationExpression: options.generationExpression || '',
    migration: migrationForColumn(table, name),
  }
}

function indexDefinition(table, name, unique, columnsValue) {
  return {
    type: 'indexDefinition',
    table,
    name,
    unique,
    columns: columnsValue,
    migration: migrationForIndex(table, name),
  }
}

function foreignKeyDefinition(
  table,
  name,
  columnsValue,
  referencedTable,
  referencedColumns,
  deleteRule,
) {
  return {
    type: 'foreignKeyDefinition',
    table,
    name,
    columns: columnsValue,
    referencedTable,
    referencedColumns,
    deleteRule,
    updateRule: 'RESTRICT',
    migration: migrationForConstraint(table, name),
  }
}

function checkConstraintDefinition(table, name, checkClause) {
  return {
    type: 'checkConstraintDefinition',
    table,
    name,
    checkClause,
    migration: migrationForConstraint(table, name),
  }
}

function triggerDefinition(table, name, timing, event, actionStatement) {
  return {
    type: 'triggerDefinition',
    table,
    name,
    timing,
    event,
    actionStatement,
    migration: migrationForConstraint(table, name),
  }
}

function inspectSchemaDefinitions() {
  const definitionChecks = [
    ...columnDefinitions,
    ...indexDefinitions,
    ...foreignKeyDefinitions,
    ...checkConstraintDefinitions,
    ...triggerDefinitions,
  ]
  const statements = [
    ...columnDefinitions.map(columnDefinitionSql),
    ...indexDefinitions.map(indexDefinitionSql),
    ...foreignKeyDefinitions.map(foreignKeyDefinitionSql),
    ...checkConstraintDefinitions.map(checkConstraintDefinitionSql),
    ...triggerDefinitions.map(triggerDefinitionSql),
  ]
  if (statements.length === 0) return []

  const definitionOutput = runMysql(
    `${statements.join('\nUNION ALL\n')};`,
    'schema readiness failed to inspect definitions',
  )

  const readiness = new Map()
  if (definitionOutput) {
    for (const line of definitionOutput.split(/\r?\n/)) {
      const [key, ready] = line.split(/\t/)
      readiness.set(key, Number(ready) === 1)
    }
  }
  return definitionChecks.map((item) => ({
    type: item.type,
    table: item.table,
    name: item.name,
    key: itemKey(item),
    ready: readiness.get(itemKey(item)) === true,
    migration: item.migration,
  }))
}

function columnDefinitionSql(item) {
  const clauses = [
    'table_schema = DATABASE()',
    `table_name = '${escapeSql(item.table)}'`,
    `column_name = '${escapeSql(item.name)}'`,
    `LOWER(column_type) = '${escapeSql(item.columnType.toLowerCase())}'`,
    `is_nullable = '${item.nullable ? 'YES' : 'NO'}'`,
  ]
  if (item.characterSet) {
    clauses.push(`LOWER(COALESCE(character_set_name, '')) = '${escapeSql(item.characterSet.toLowerCase())}'`)
  }
  if (item.collation) {
    clauses.push(`LOWER(COALESCE(collation_name, '')) = '${escapeSql(item.collation.toLowerCase())}'`)
  }
  if (item.extra) {
    clauses.push(`UPPER(TRIM(COALESCE(extra, ''))) = '${escapeSql(item.extra.toUpperCase())}'`)
  }
  if (item.generationExpression) {
    const normalized = normalizedExpressionSql('generation_expression')
    const expected = escapeSql(item.generationExpression.toLowerCase())
    if (item.table === 't_int_content_suggestion' && item.name === 'pending_guard') {
      clauses.push(`(
        ${normalized} = '${expected}'
        OR (
          ${normalized} LIKE '%decisionisnull%'
          AND ${normalized} LIKE '%1%'
          AND ${normalized} LIKE '%null%'
        )
      )`)
    } else {
      clauses.push(`${normalized} = '${expected}'`)
    }
  }
  return `
SELECT '${escapeSql(itemKey(item))}' AS item,
       IF(COUNT(*) = 1, 1, 0) AS ready
FROM information_schema.columns
WHERE ${clauses.join('\n  AND ')}
`.trim()
}

function indexDefinitionSql(item) {
  return `
SELECT '${escapeSql(itemKey(item))}' AS item,
       IF(
         COUNT(*) > 0
         AND MIN(non_unique) = ${item.unique ? 0 : 1}
         AND MAX(non_unique) = ${item.unique ? 0 : 1}
         AND GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') = '${escapeSql(item.columns)}',
         1,
         0
       ) AS ready
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = '${escapeSql(item.table)}'
  AND index_name = '${escapeSql(item.name)}'
`.trim()
}

function foreignKeyDefinitionSql(item) {
  return `
SELECT '${escapeSql(itemKey(item))}' AS item,
       IF(
         COUNT(*) > 0
         AND GROUP_CONCAT(kcu.column_name ORDER BY kcu.ordinal_position SEPARATOR ',') = '${escapeSql(item.columns)}'
         AND MAX(kcu.referenced_table_name) = '${escapeSql(item.referencedTable)}'
         AND GROUP_CONCAT(kcu.referenced_column_name ORDER BY kcu.ordinal_position SEPARATOR ',') = '${escapeSql(item.referencedColumns)}'
         AND MAX(rc.delete_rule) = '${escapeSql(item.deleteRule)}'
         AND MAX(rc.update_rule) = '${escapeSql(item.updateRule)}',
         1,
         0
       ) AS ready
FROM information_schema.key_column_usage kcu
JOIN information_schema.referential_constraints rc
  ON rc.constraint_schema = kcu.constraint_schema
 AND rc.table_name = kcu.table_name
 AND rc.constraint_name = kcu.constraint_name
WHERE kcu.constraint_schema = DATABASE()
  AND kcu.table_name = '${escapeSql(item.table)}'
  AND kcu.constraint_name = '${escapeSql(item.name)}'
  AND kcu.referenced_table_name IS NOT NULL
`.trim()
}

function checkConstraintDefinitionSql(item) {
  const normalized = normalizedExpressionSql('cc.check_clause')
  return `
SELECT '${escapeSql(itemKey(item))}' AS item,
       IF(COUNT(*) = 1, 1, 0) AS ready
FROM information_schema.table_constraints tc
JOIN information_schema.check_constraints cc
  ON cc.constraint_schema = tc.constraint_schema
 AND cc.constraint_name = tc.constraint_name
WHERE tc.constraint_schema = DATABASE()
  AND tc.table_name = '${escapeSql(item.table)}'
  AND tc.constraint_type = 'CHECK'
  AND tc.constraint_name = '${escapeSql(item.name)}'
  AND ${normalized} = '${escapeSql(item.checkClause.toLowerCase())}'
`.trim()
}

function triggerDefinitionSql(item) {
  const normalized = normalizedExpressionSql('action_statement')
  return `
SELECT '${escapeSql(itemKey(item))}' AS item,
       IF(COUNT(*) = 1, 1, 0) AS ready
FROM information_schema.triggers
WHERE trigger_schema = DATABASE()
  AND event_object_table = '${escapeSql(item.table)}'
  AND trigger_name = '${escapeSql(item.name)}'
  AND action_timing = '${escapeSql(item.timing.toUpperCase())}'
  AND event_manipulation = '${escapeSql(item.event.toUpperCase())}'
  AND action_orientation = 'ROW'
  AND ${normalized} LIKE '%${escapeSql(item.actionStatement.toLowerCase())}%'
`.trim()
}

function normalizedExpressionSql(expression) {
  return `REPLACE(
    REPLACE(
      REPLACE(
        LOWER(
          REPLACE(
            REPLACE(
              REPLACE(
                REPLACE(
                  REPLACE(
                    REPLACE(
                      REPLACE(
                        REPLACE(COALESCE(${expression}, ''), ' ', ''),
                        CHAR(9),
                        ''
                      ),
                      CHAR(10),
                      ''
                    ),
                    CHAR(13),
                    ''
                  ),
                  '\`',
                  ''
                ),
                '(',
                ''
              ),
              ')',
              ''
            ),
            ',',
            ''
          )
        ),
        '_utf8mb4',
        ''
      ),
      '_utf8',
      ''
    ),
    '_ascii',
    ''
  )`
}

function itemKey(item) {
  if (item.type === 'table') return `table:${item.table}`
  return `${item.type}:${item.table}.${item.name}`
}

function sqlList(values) {
  return values.map((value) => `'${escapeSql(value)}'`).join(', ')
}

function escapeSql(value) {
  return String(value).replaceAll('\\', '\\\\').replaceAll("'", "''")
}

function runMysql(sql, errorContext) {
  try {
    return execFileSync(mysqlBin, [
      '--batch',
      '--raw',
      '--skip-column-names',
      '-h', config.host,
      '-P', config.port,
      '-u', config.user,
      config.database,
    ], {
      encoding: 'utf8',
      env: childEnv,
      input: sql,
      maxBuffer: 4 * 1024 * 1024,
    }).trim()
  } catch (error) {
    console.error(`${errorContext}: ${error.message}`)
    process.exit(2)
  }
}

function inspectFlywayHistory(historyTableExists) {
  const rows = []
  if (historyTableExists) {
    const historySql = `
SELECT COALESCE(version, ''), script, type, COALESCE(CAST(checksum AS CHAR), ''), success
FROM flyway_schema_history
ORDER BY installed_rank;
`
    const historyOutput = runMysql(
      historySql,
      'schema readiness failed to query Flyway history',
    )
    if (historyOutput) {
      for (const line of historyOutput.split(/\r?\n/)) {
        const [version, script, type, checksum, success] = line.split(/\t/)
        rows.push({ version, script, type, checksum, success: success === '1' })
      }
    }
  }

  const demoHistoryTable = migrationManifest.streams.demo.historyTable
  if (!/^[a-z0-9_]+$/i.test(demoHistoryTable)) {
    throw new Error(`invalid demo Flyway history table: ${demoHistoryTable}`)
  }
  let demoHistoryTableExists = false
  let appliedDemoMigrations = 0
  const demoHistoryOutput = runMysql(`
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = '${escapeSql(demoHistoryTable)}';
`, 'schema readiness failed to query demo Flyway history')
  demoHistoryTableExists = demoHistoryOutput === '1'
  if (demoHistoryTableExists) {
    const appliedDemoOutput = runMysql(
      `SELECT COUNT(*) FROM \`${demoHistoryTable}\` WHERE type = 'SQL' AND success = 1;`,
      'schema readiness failed to query applied demo migrations',
    )
    appliedDemoMigrations = Number.parseInt(appliedDemoOutput, 10) || 0
  }

  const rowsByVersion = new Map()
  for (const row of rows) {
    const values = rowsByVersion.get(row.version) || []
    values.push(row)
    rowsByVersion.set(row.version, values)
  }

  const historyChecks = coreMigrations.map((migration) => {
    const matchingRows = rowsByVersion.get(migration.version) || []
    const expectedScript = migration.resource.split('/').at(-1)
    const expectedChecksum = String(migration.flywayChecksum)
    const ready = matchingRows.length === 1
      && matchingRows[0].success
      && matchingRows[0].type === 'SQL'
      && matchingRows[0].script === expectedScript
      && matchingRows[0].checksum === expectedChecksum
    return {
      type: 'flywayHistory',
      table: 'flyway_schema_history',
      name: migration.version,
      key: `flywayHistory:${migration.version}`,
      ready,
      migration: migration.source,
    }
  })
  const failedRows = rows.filter(({ success }) => !success)
  const duplicateVersions = [...rowsByVersion.entries()]
    .filter(([version, matchingRows]) => version && matchingRows.length > 1)
    .map(([version]) => version)
  const expectedVersions = new Set(coreMigrations.map(({ version }) => version))
  const unexpectedVersions = rows
    .filter(({ version, type }) => version && type === 'SQL' && !expectedVersions.has(version))
    .map(({ version }) => version)
  const checksumMismatches = coreMigrations
    .filter((migration) => {
      const matchingRows = rowsByVersion.get(migration.version) || []
      return matchingRows.length === 1
        && matchingRows[0].type === 'SQL'
        && matchingRows[0].checksum !== String(migration.flywayChecksum)
    })
    .map(({ version }) => version)
  const scriptMismatches = coreMigrations
    .filter((migration) => {
      const matchingRows = rowsByVersion.get(migration.version) || []
      return matchingRows.length === 1
        && matchingRows[0].type === 'SQL'
        && matchingRows[0].script !== migration.resource.split('/').at(-1)
    })
    .map(({ version }) => version)
  const missingVersions = coreMigrations
    .filter(({ version }) => (rowsByVersion.get(version) || []).length === 0)
    .map(({ version }) => version)
  const appliedCore = historyChecks.filter(({ ready }) => ready).length
  const latestApplied = rows
    .filter(({ version, success, type }) => version && success && type === 'SQL')
    .map(({ version }) => version)
    .sort((left, right) => left.localeCompare(right, 'en', { numeric: true }))
    .at(-1) || null
  const demoScripts = new Set(
    migrationManifest.migrations
      .filter(({ stream }) => stream === 'demo')
      .map(({ resource }) => resource.split('/').at(-1)),
  )
  const demoMigrationsInCoreHistory = rows
    .filter(({ type, script }) => type === 'SQL' && demoScripts.has(script))
    .map(({ script }) => script)

  return {
    checks: [
      ...historyChecks,
      {
        type: 'flywayHistory',
        table: 'flyway_schema_history',
        name: 'unexpectedVersions',
        key: 'flywayHistory:unexpectedVersions',
        ready: unexpectedVersions.length === 0,
        migration: 'Flyway lifecycle metadata',
      },
    ],
    summary: {
      historyTableExists,
      baselineVersion: migrationManifest.baselineVersion,
      expectedCoreMigrations: coreMigrations.length,
      appliedCoreMigrations: appliedCore,
      latestExpectedVersion: coreMigrations.at(-1)?.version || null,
      latestAppliedVersion: latestApplied,
      failedScripts: failedRows.map(({ script }) => script),
      duplicateVersions,
      missingVersions,
      checksumMismatches,
      scriptMismatches,
      unexpectedVersions,
      assetsReady: migrationAssetChecks.every(({ ready }) => ready),
      demoMigrationsAutoApplied: demoMigrationsInCoreHistory.length > 0,
      demoMigrationsInCoreHistory,
      demoHistoryTable,
      demoHistoryTableExists,
      appliedDemoMigrations,
    },
  }
}

function migrationForTable(table) {
  if (table === 'flyway_schema_history') return 'Flyway lifecycle metadata'
  if (['t_post_reference', 't_post_knowledge_relation', 't_int_post_outcome'].includes(table)) {
    return 'db/migration/20260720_knowledge_lifecycle.sql'
  }
  if (table === 't_collab_content_need_event') {
    return 'db/migration/20260718_collab_need_lifecycle.sql'
  }
  if (table === 't_collab_content_need_claim_cycle'
    || table === 't_collab_content_need_revision') {
    return 'db/migration/20260719_collab_need_claim_cycle_revision.sql'
  }
  if (table.startsWith('t_collab_')) {
    return 'db/migration/20260714_collaboration_stage2.sql'
  }
  if (table.startsWith('t_incentive_')
    || table.startsWith('t_virtual_benefit_')
    || table.startsWith('t_thank_')
    || table.startsWith('t_bounty_')
    || table.startsWith('t_quota_bounty')
    || table.startsWith('t_community_role_')) {
    return 'db/migration/20260714_incentive_stage3_stage5.sql'
  }
  if (table === 't_int_post_trust_state'
    || table === 't_int_post_useful_feedback'
    || table === 't_int_content_suggestion') {
    return 'db/migration/20260713_trusted_content_stage1.sql'
  }
  if (table === 't_post_version_history') return 'db/migration/20260530_post_version_history.sql'
  if (table.startsWith('t_community_topic')) return 'db/migration/20260608_community_topics.sql'
  if (table === 't_review_queue') return 'db/migration/20260608_review_queue.sql'
  if (table === 't_mock_interview_answer') return 'db/migration/20260608_mock_interview_ai_review_transparency.sql'
  if (table === 't_ai_extract_task') return 'db/migration/20260605_ai_extract_task_metrics.sql'
  if (table === 't_tag') return 'db/migration/20260608_tag_governance.sql'
  if (table === 't_domain_moderator') return 'db/migration/20260617_domain_moderators.sql'
  if (table === 't_domain_config') return 'db/migration/20260623_domain_config.sql'
  if (table === 't_growth_event') return 'db/migration/20260623_growth_event.sql'
  if (table === 't_user_task_state') return 'db/migration/20260623_user_task_state.sql'
  if (table === 't_post_extension') return 'db/migration/20260618_post_extension_domain_index.sql'
  if (table === 't_content_assist_record') return 'db/migration/20260624_content_assist_ai.sql'
  if (table === 't_content_series' || table === 't_content_series_post') return 'db/migration/20260624_content_series.sql'
  if (table === 't_feed_recommend_support_stat') return 'db/migration/20260624_new_creator_support_stats.sql'
  if (table === 't_feed_feedback_preference') {
    return 'db/migration/20260719_feed_feedback_control.sql'
  }
  if (table === 't_expert_cert_application') return 'db/migration/20260624_expert_certification.sql'
  if (table === 't_int_contact_request') return 'db/migration/20260707_contact_request.sql'
  if (table === 't_user_privacy_setting') return 'db/migration/20260707_contact_request_settings.sql'
  if (table === 't_user_subscription_preference') {
    return 'db/migration/20260719_user_subscription_preference.sql'
  }
  if (table === 't_projection_reconcile_request') {
    return 'db/migration/20260719_projection_reconcile_request.sql'
  }
  if (table === 't_search_index_rebuild_task') {
    return 'db/migration/20260720_search_index_rebuild_task.sql'
  }
  if (table === 't_int_discussion_follow') return 'db/migration/20260707_discussion_follow.sql'
  if (table === 't_int_favorite' || table === 't_int_favorite_folder') return 'db/migration/20260707_favorite_folder.sql'
  if (table === 't_int_comment_quality_signal' || table === 't_int_comment_helpful') return 'db/migration/20260707_comment_quality_schema.sql'
  return 'earlier governance/init migration'
}

function migrationForColumn(table, name) {
  if ((table === 't_post_main'
      && ['latest_effective_content_revision_at', 'latest_effective_content_revision_token'].includes(name))
    || (table === 't_post_version_history'
      && ['quality_signal_revision', 'quality_signal_revision_state',
        'quality_signal_effective_at', 'quality_signal_revision_token'].includes(name))) {
    return 'db/migration/20260804_creator_content_revision_boundary.sql'
  }
  if (['t_post_reference', 't_post_knowledge_relation', 't_int_post_outcome'].includes(table)
    || (table === 't_int_content_suggestion' && [
      'base_version', 'target_scope', 'target_locator', 'expected_change',
      'resolution', 'delivery_status',
    ].includes(name))) {
    return 'db/migration/20260720_knowledge_lifecycle.sql'
  }
  if (table === 't_post_extension' && name === 'domain') {
    return 'db/migration/20260712_unclassified_domain.sql'
  }
  if (table === 't_collab_content_need' && [
    'submitted_by_uid',
    'submitted_at',
    'submission_resolution_type',
    'submission_resolution_id',
    'submission_note',
    'reject_reason',
  ].includes(name)) {
    return 'db/migration/20260717_collab_need_submission.sql'
  }
  if ((table === 't_collab_content_need'
      && ['claimed_at', 'last_progress_at'].includes(name))
    || table === 't_collab_content_need_event') {
    return 'db/migration/20260718_collab_need_lifecycle.sql'
  }
  if (table === 't_collab_content_need_claim_cycle'
    || table === 't_collab_content_need_revision') {
    return 'db/migration/20260719_collab_need_claim_cycle_revision.sql'
  }
  if (table === 't_community_topic' && ['domain', 'allowed_domains'].includes(name)) {
    return 'db/migration/20260714_collaboration_stage2.sql'
  }
  if (table.startsWith('t_collab_')) {
    return 'db/migration/20260714_collaboration_stage2.sql'
  }
  if (table.startsWith('t_incentive_')
    || table.startsWith('t_virtual_benefit_')
    || table.startsWith('t_thank_')
    || table.startsWith('t_bounty_')
    || table.startsWith('t_quota_bounty')
    || table.startsWith('t_community_role_')) {
    return 'db/migration/20260714_incentive_stage3_stage5.sql'
  }
  if (table === 't_int_post_trust_state'
    || table === 't_int_post_useful_feedback'
    || table === 't_int_content_suggestion'
    || (table === 't_post_version_history'
      && ['result_version', 'public_update_summary', 'impact_scope'].includes(name))
    || (table === 't_growth_event' && name === 'event_key')) {
    return 'db/migration/20260713_trusted_content_stage1.sql'
  }
  if (table === 't_tag') return 'db/migration/20260608_tag_governance.sql'
  if (table === 't_mock_interview_answer') return 'db/migration/20260608_mock_interview_ai_review_transparency.sql'
  if (table === 't_ai_extract_task') return 'db/migration/20260605_ai_extract_task_metrics.sql'
  if (table === 't_domain_moderator') return 'db/migration/20260617_domain_moderators.sql'
  if (table === 't_domain_config') return 'db/migration/20260623_domain_config.sql'
  if (table === 't_growth_event') return 'db/migration/20260623_growth_event.sql'
  if (table === 't_user_task_state') return 'db/migration/20260623_user_task_state.sql'
  if (table === 't_post_extension') return 'db/migration/20260618_post_extension_domain_index.sql'
  if (table === 't_content_assist_record') return 'db/migration/20260624_content_assist_ai.sql'
  if (table === 't_content_series' || table === 't_content_series_post') return 'db/migration/20260624_content_series.sql'
  if (table === 't_feed_recommend_support_stat') return 'db/migration/20260624_new_creator_support_stats.sql'
  if (table === 't_feed_feedback_preference') {
    return 'db/migration/20260719_feed_feedback_control.sql'
  }
  if (table === 't_expert_cert_application') return 'db/migration/20260624_expert_certification.sql'
  if ((table === 't_operation_curation_item' || table === 't_operation_slot_item') && name === 'active_guard') return 'db/migration/20260708_operation_soft_delete_unique_guard.sql'
  if (table === 't_int_contact_request') return 'db/migration/20260707_contact_request.sql'
  if (table === 't_user_privacy_setting') return 'db/migration/20260707_contact_request_settings.sql'
  if (table === 't_user_subscription_preference') {
    return 'db/migration/20260719_user_subscription_preference.sql'
  }
  if (table === 't_projection_reconcile_request') {
    return 'db/migration/20260719_projection_reconcile_request.sql'
  }
  if (table === 't_search_index_rebuild_task') {
    return 'db/migration/20260720_search_index_rebuild_task.sql'
  }
  if (table === 't_int_discussion_follow') return 'db/migration/20260707_discussion_follow.sql'
  if (table === 't_int_favorite' || table === 't_int_favorite_folder') return 'db/migration/20260707_favorite_folder.sql'
  if (table === 't_int_comment' || table === 't_int_comment_quality_signal' || table === 't_int_comment_helpful') return 'db/migration/20260707_comment_quality_schema.sql'
  return 'unknown'
}

function migrationForIndex(table, name) {
  if ((table === 't_post_version_history' && name === 'idx_post_quality_signal_revision')
    || (table === 't_feed_feedback_preference' && name === 'idx_feed_feedback_quality_signal_v32')) {
    return 'db/migration/20260804_creator_content_revision_boundary.sql'
  }
  if (['t_post_reference', 't_post_knowledge_relation', 't_int_post_outcome'].includes(table)) {
    return 'db/migration/20260720_knowledge_lifecycle.sql'
  }
  if ([
    'idx_collab_need_follow_uid_active_id',
    'idx_collab_need_follow_need_active_id',
    'idx_collab_need_event_need',
    'idx_collab_need_event_visibility',
    'uk_collab_need_claim_cycle_no',
    'uk_collab_need_claim_cycle_active',
    'idx_collab_need_claim_cycle_need',
    'idx_collab_need_claim_cycle_status',
    'idx_collab_need_claim_cycle_claimant',
    'uk_collab_need_revision_no',
    'idx_collab_need_revision_need',
    'idx_collab_need_revision_cycle_status',
    'idx_collab_need_revision_submitter',
    'idx_collab_need_revision_visibility',
  ].includes(name)) {
    return name.startsWith('uk_collab_need_claim_cycle')
      || name.startsWith('idx_collab_need_claim_cycle')
      || name.startsWith('uk_collab_need_revision')
      || name.startsWith('idx_collab_need_revision')
      ? 'db/migration/20260719_collab_need_claim_cycle_revision.sql'
      : 'db/migration/20260718_collab_need_lifecycle.sql'
  }
  if ([
    'idx_incentive_freeze_account_status',
    'idx_incentive_invalidation_reference',
    'idx_virtual_benefit_order_benefit',
    'idx_quota_submission_applicant',
    'idx_quota_bounty_appeal_applicant',
  ].includes(name)) {
    return 'db/migration/20260714_database_integrity_hardening.sql'
  }
  if (table === 't_community_topic' && name === 'idx_community_topic_domain') {
    return 'db/migration/20260714_collaboration_stage2.sql'
  }
  if (table.startsWith('t_collab_')) {
    return 'db/migration/20260714_collaboration_stage2.sql'
  }
  if (table.startsWith('t_incentive_')
    || table.startsWith('t_virtual_benefit_')
    || table.startsWith('t_thank_')
    || table.startsWith('t_bounty_')
    || table.startsWith('t_quota_bounty')
    || table.startsWith('t_community_role_')) {
    return 'db/migration/20260714_incentive_stage3_stage5.sql'
  }
  if (table === 't_int_post_trust_state'
    || table === 't_int_post_useful_feedback'
    || table === 't_int_content_suggestion'
    || (table === 't_post_version_history'
      && (name === 'uk_post_result_version' || name === 'idx_post_public_update'))
    || (table === 't_growth_event' && name === 'uk_growth_event_key')) {
    return 'db/migration/20260713_trusted_content_stage1.sql'
  }
  if (table === 't_user_follow' && (name === 'idx_following_page' || name === 'idx_follower_page')) return 'db/migration/20260708_public_read_indexes.sql'
  if (table === 't_notif_message' && (name === 'idx_receiver_list' || name === 'idx_receiver_unread_latest')) return 'db/migration/20260708_public_read_indexes.sql'
  if (table === 't_int_contact_request' && (name === 'idx_contact_request_receiver_page' || name === 'idx_contact_request_requester_page')) return 'db/migration/20260708_public_read_indexes.sql'
  if (table === 't_int_favorite' && (name === 'idx_favorite_user_page' || name === 'idx_favorite_user_folder_page')) return 'db/migration/20260708_public_read_indexes.sql'
  if (table === 't_int_like' && name === 'idx_like_user_page') return 'db/migration/20260708_public_read_indexes.sql'
  if (table === 't_int_comment' && name === 'idx_comment_quality_roots') return 'db/migration/20260708_public_read_indexes.sql'
  if (table === 't_int_comment_quality_signal' && name === 'idx_comment_quality_post_comment') return 'db/migration/20260708_public_read_indexes.sql'
  if (table === 't_int_discussion_follow' && (name === 'idx_discussion_follow_notify_page' || name === 'idx_discussion_follow_uid_page')) return 'db/migration/20260708_public_read_indexes.sql'
  if (table === 't_tag') return 'db/migration/20260608_tag_governance.sql'
  if (table.startsWith('t_community_topic')) return 'db/migration/20260608_community_topics.sql'
  if (table === 't_review_queue') return 'db/migration/20260608_review_queue.sql'
  if (table === 't_domain_moderator') return 'db/migration/20260617_domain_moderators.sql'
  if (table === 't_domain_config') return 'db/migration/20260623_domain_config.sql'
  if (table === 't_growth_event') return 'db/migration/20260623_growth_event.sql'
  if (table === 't_user_task_state') return 'db/migration/20260623_user_task_state.sql'
  if (table === 't_post_extension') return 'db/migration/20260618_post_extension_domain_index.sql'
  if (table === 't_content_assist_record') return 'db/migration/20260624_content_assist_ai.sql'
  if (table === 't_content_series' || table === 't_content_series_post') return 'db/migration/20260624_content_series.sql'
  if (table === 't_feed_recommend_support_stat') return 'db/migration/20260624_new_creator_support_stats.sql'
  if (table === 't_feed_feedback_preference') {
    return 'db/migration/20260719_feed_feedback_control.sql'
  }
  if (table === 't_expert_cert_application') return 'db/migration/20260624_expert_certification.sql'
  if (table === 't_int_contact_request') return 'db/migration/20260707_contact_request.sql'
  if (table === 't_user_privacy_setting') return 'db/migration/20260707_contact_request_settings.sql'
  if (table === 't_user_subscription_preference') {
    return 'db/migration/20260719_user_subscription_preference.sql'
  }
  if (table === 't_projection_reconcile_request') {
    return 'db/migration/20260719_projection_reconcile_request.sql'
  }
  if (table === 't_search_index_rebuild_task') {
    return 'db/migration/20260720_search_index_rebuild_task.sql'
  }
  if (table === 't_int_discussion_follow') return 'db/migration/20260707_discussion_follow.sql'
  if (table === 't_int_favorite' || table === 't_int_favorite_folder') return 'db/migration/20260707_favorite_folder.sql'
  if (table === 't_int_comment_quality_signal' || table === 't_int_comment_helpful') return 'db/migration/20260707_comment_quality_schema.sql'
  return 'earlier governance/init migration'
}

function migrationForConstraint(table, name) {
  if (['t_post_reference', 't_post_knowledge_relation', 't_int_post_outcome'].includes(table)
    || (table === 't_int_content_suggestion' && name.startsWith('chk_int_content_suggestion_'))) {
    return 'db/migration/20260720_knowledge_lifecycle.sql'
  }
  if (table === 't_feed_feedback_preference') {
    return 'db/migration/20260719_feed_feedback_control.sql'
  }
  if (table === 't_user_subscription_preference') {
    return 'db/migration/20260719_user_subscription_preference.sql'
  }
  if (table === 't_projection_reconcile_request') {
    return 'db/migration/20260719_projection_reconcile_request.sql'
  }
  if (table === 't_search_index_rebuild_task') {
    return 'db/migration/20260720_search_index_rebuild_task.sql'
  }
  if (table === 't_collab_content_need_claim_cycle'
    || table === 't_collab_content_need_revision') {
    return 'db/migration/20260719_collab_need_claim_cycle_revision.sql'
  }
  if ([
    'chk_virtual_benefit_stock_null_pair',
    'chk_virtual_benefit_order_cost',
  ].includes(name)) {
    return 'db/migration/20260714_database_integrity_hardening.sql'
  }
  if (table.startsWith('t_collab_')) {
    return 'db/migration/20260714_collaboration_stage2.sql'
  }
  if (table.startsWith('t_incentive_')
    || table.startsWith('t_virtual_benefit_')
    || table.startsWith('t_thank_')
    || table.startsWith('t_bounty_')
    || table.startsWith('t_quota_bounty')
    || table.startsWith('t_community_role_')) {
    return 'db/migration/20260714_incentive_stage3_stage5.sql'
  }
  if (table === 't_int_post_trust_state'
    || table === 't_int_post_useful_feedback'
    || table === 't_int_content_suggestion') {
    return 'db/migration/20260713_trusted_content_stage1.sql'
  }
  if (table === 't_post_version_history') return 'db/migration/20260530_post_version_history.sql'
  if (table === 't_domain_moderator') return 'db/migration/20260617_domain_moderators.sql'
  if (table === 't_domain_config') return 'db/migration/20260623_domain_config.sql'
  if (table === 't_growth_event') return 'db/migration/20260623_growth_event.sql'
  if (table === 't_user_task_state') return 'db/migration/20260623_user_task_state.sql'
  if (table === 't_content_assist_record') return 'db/migration/20260624_content_assist_ai.sql'
  if (table === 't_content_series' || table === 't_content_series_post') return 'db/migration/20260624_content_series.sql'
  if (table === 't_feed_recommend_support_stat') return 'db/migration/20260624_new_creator_support_stats.sql'
  if (table === 't_expert_cert_application') return 'db/migration/20260624_expert_certification.sql'
  if (table === 't_int_contact_request') return 'db/migration/20260707_contact_request.sql'
  if (table === 't_user_privacy_setting') return 'db/migration/20260707_contact_request_settings.sql'
  if (table === 't_int_discussion_follow') return 'db/migration/20260707_discussion_follow.sql'
  if (table === 't_int_favorite' || table === 't_int_favorite_folder') return 'db/migration/20260707_favorite_folder.sql'
  if (table === 't_int_comment_quality_signal' || table === 't_int_comment_helpful') return 'db/migration/20260707_comment_quality_schema.sql'
  return 'unknown'
}
