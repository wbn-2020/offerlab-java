import { execFileSync } from 'node:child_process'
import { existsSync } from 'node:fs'

const args = new Map()
for (const arg of process.argv.slice(2)) {
  const match = arg.match(/^--([^=]+)=(.*)$/)
  if (match) args.set(match[1], match[2])
}

const config = {
  host: args.get('host') || process.env.OFFERLAB_DB_HOST || '127.0.0.1',
  port: args.get('port') || process.env.OFFERLAB_DB_PORT || '3306',
  user: args.get('user') || process.env.OFFERLAB_DB_USER || 'offerlab',
  password: args.get('password') || process.env.OFFERLAB_DB_PASSWORD || 'offerlab123',
  database: args.get('database') || process.env.OFFERLAB_DB_NAME || 'offerlab',
  json: process.argv.includes('--json'),
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

const expectations = [
  ...tables([
    't_admin_audit_log',
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
    't_post_extension',
    't_user_task_state',
    't_content_assist_record',
    't_content_series',
    't_content_series_post',
    't_feed_recommend_support_stat',
    't_expert_cert_application',
    't_int_contact_request',
    't_user_privacy_setting',
    't_int_discussion_follow',
    't_int_favorite',
    't_int_favorite_folder',
    't_int_comment_quality_signal',
    't_int_comment_helpful',
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
  ...indexes('t_post_report', ['idx_post_reporter_status']),
  ...indexes('t_comment_report', ['idx_comment_reporter_status']),
  ...indexes('t_interview_question', ['idx_status_time']),
  ...indexes('t_tag', ['idx_tag_status_recommend', 'idx_tag_merge_target']),
  ...indexes('t_community_topic', ['uk_topic_slug', 'idx_topic_status_sort', 'idx_topic_featured_sort']),
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
    'idx_growth_event_type_time',
    'idx_growth_event_domain_time',
    'idx_growth_event_uid_time',
    'idx_growth_event_content_time',
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
  ...primaryKeys('t_domain_moderator', ['id']),
  ...primaryKeys('t_domain_config', ['domain']),
  ...primaryKeys('t_growth_event', ['id']),
  ...primaryKeys('t_user_task_state', ['id']),
  ...primaryKeys('t_content_assist_record', ['id']),
  ...primaryKeys('t_content_series', ['id']),
  ...primaryKeys('t_content_series_post', ['id']),
  ...primaryKeys('t_feed_recommend_support_stat', ['id']),
  ...primaryKeys('t_expert_cert_application', ['id']),
  ...primaryKeys('t_int_contact_request', ['id']),
  ...primaryKeys('t_user_privacy_setting', ['user_id']),
  ...primaryKeys('t_int_discussion_follow', ['id']),
  ...primaryKeys('t_int_favorite', ['id']),
  ...primaryKeys('t_int_favorite_folder', ['id']),
  ...primaryKeys('t_int_comment_quality_signal', ['id']),
  ...primaryKeys('t_int_comment_helpful', ['id']),
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
GROUP BY table_name, column_name;
`

let output
try {
  output = execFileSync(mysqlBin, [
    '--batch',
    '--raw',
    '--skip-column-names',
    '-h', config.host,
    '-P', config.port,
    '-u', config.user,
    config.database,
    '-e',
    sql,
  ], { encoding: 'utf8', env: childEnv }).trim()
} catch (error) {
  console.error(`schema readiness check failed to query MySQL: ${error.message}`)
  process.exit(2)
}

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

function migrationForTable(table) {
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
  if (table === 't_expert_cert_application') return 'db/migration/20260624_expert_certification.sql'
  if (table === 't_int_contact_request') return 'db/migration/20260707_contact_request.sql'
  if (table === 't_user_privacy_setting') return 'db/migration/20260707_contact_request_settings.sql'
  if (table === 't_int_discussion_follow') return 'db/migration/20260707_discussion_follow.sql'
  if (table === 't_int_favorite' || table === 't_int_favorite_folder') return 'db/migration/20260707_favorite_folder.sql'
  if (table === 't_int_comment_quality_signal' || table === 't_int_comment_helpful') return 'db/migration/20260707_comment_quality_schema.sql'
  return 'earlier governance/init migration'
}

function migrationForColumn(table, name) {
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
  if (table === 't_expert_cert_application') return 'db/migration/20260624_expert_certification.sql'
  if ((table === 't_operation_curation_item' || table === 't_operation_slot_item') && name === 'active_guard') return 'db/migration/20260708_operation_soft_delete_unique_guard.sql'
  if (table === 't_int_contact_request') return 'db/migration/20260707_contact_request.sql'
  if (table === 't_user_privacy_setting') return 'db/migration/20260707_contact_request_settings.sql'
  if (table === 't_int_discussion_follow') return 'db/migration/20260707_discussion_follow.sql'
  if (table === 't_int_favorite' || table === 't_int_favorite_folder') return 'db/migration/20260707_favorite_folder.sql'
  if (table === 't_int_comment' || table === 't_int_comment_quality_signal' || table === 't_int_comment_helpful') return 'db/migration/20260707_comment_quality_schema.sql'
  return 'unknown'
}

function migrationForIndex(table, name) {
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
  if (table === 't_expert_cert_application') return 'db/migration/20260624_expert_certification.sql'
  if (table === 't_int_contact_request') return 'db/migration/20260707_contact_request.sql'
  if (table === 't_user_privacy_setting') return 'db/migration/20260707_contact_request_settings.sql'
  if (table === 't_int_discussion_follow') return 'db/migration/20260707_discussion_follow.sql'
  if (table === 't_int_favorite' || table === 't_int_favorite_folder') return 'db/migration/20260707_favorite_folder.sql'
  if (table === 't_int_comment_quality_signal' || table === 't_int_comment_helpful') return 'db/migration/20260707_comment_quality_schema.sql'
  return 'earlier governance/init migration'
}

function migrationForConstraint(table) {
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
