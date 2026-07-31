import { execFileSync } from 'node:child_process'
import { existsSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

const scriptName = 'verify-demo-question-data'
const args = new Map()
for (const arg of process.argv.slice(2)) {
  const match = arg.match(/^--([^=]+)=(.*)$/)
  if (match) args.set(match[1], match[2])
}
if (args.has('password')) {
  console.error('--password is not supported because process arguments are observable. Use OFFERLAB_DB_PASSWORD or DB_PASSWORD.')
  process.exit(2)
}

const config = {
  host: args.get('host') || process.env.OFFERLAB_DB_HOST || '127.0.0.1',
  port: args.get('port') || process.env.OFFERLAB_DB_PORT || '3306',
  user: args.get('user') || process.env.OFFERLAB_DB_USER || 'offerlab',
  password: process.env.OFFERLAB_DB_PASSWORD || process.env.DB_PASSWORD || 'offerlab-local-db-change-me',
  database: args.get('database') || process.env.OFFERLAB_DB_NAME || 'offerlab',
  adminEmail: args.get('admin-email') || process.env.OFFERLAB_ADMIN_EMAIL || '',
}

const fallbackEmails = unique([
  'demo.admin@offerlab.local',
  'admin',
])

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

const accountCandidatesSql = fallbackEmails
  .map((email, index) => `SELECT ${index + 2} AS priority, '${escapeSql(email)}' AS email`)
  .join('\n  UNION ALL\n  ')

const sql = `
WITH requested_user AS (
  SELECT u.id, u.email, 0 AS priority
  FROM t_user_account u
  WHERE '${escapeSql(config.adminEmail)}' <> ''
    AND u.email = '${escapeSql(config.adminEmail)}'
    AND u.account_status = 1
    AND u.is_deleted = 0
  LIMIT 1
),
seed_owner AS (
  SELECT u.id, u.email, 1 AS priority
  FROM t_user_prep_target target
  JOIN t_user_account u ON u.id = target.uid
  WHERE target.id = 990300000000000001
    AND u.account_status = 1
    AND u.is_deleted = 0
  LIMIT 1
),
account_candidates AS (
  ${accountCandidatesSql}
),
fallback_users AS (
  SELECT u.id, u.email, c.priority
  FROM t_user_account u
  JOIN account_candidates c ON c.email = u.email
  WHERE u.account_status = 1
    AND u.is_deleted = 0
),
retest_user_candidates AS (
  SELECT id, email, priority FROM requested_user
  UNION ALL
  SELECT id, email, priority
  FROM seed_owner
  WHERE '${escapeSql(config.adminEmail)}' = ''
    AND NOT EXISTS (SELECT 1 FROM requested_user)
  UNION ALL
  SELECT id, email, priority
  FROM fallback_users
  WHERE '${escapeSql(config.adminEmail)}' = ''
    AND NOT EXISTS (SELECT 1 FROM requested_user)
    AND NOT EXISTS (SELECT 1 FROM seed_owner)
),
retest_user AS (
  SELECT id, email
  FROM retest_user_candidates
  ORDER BY priority
  LIMIT 1
)
SELECT
  COALESCE((SELECT id FROM retest_user LIMIT 1), 0) AS retest_uid,
  COALESCE((SELECT email FROM retest_user LIMIT 1), '') AS retest_email,
  (SELECT COUNT(*) FROM t_interview_question) AS question_total,
  (SELECT COUNT(*)
   FROM t_interview_question q
   JOIN t_post_main p ON p.id = q.source_post_id
   WHERE q.status = 1 AND p.is_deleted = 0 AND p.post_status = 1 AND p.visibility = 1) AS visible_question_total,
  (SELECT COUNT(*)
   FROM t_user_prep_target t
   WHERE t.uid = COALESCE((SELECT id FROM retest_user LIMIT 1), 0)) AS admin_prep_targets,
  (SELECT COUNT(*)
   FROM t_user_question_progress up
   JOIN t_interview_question q ON q.id = up.question_id
   JOIN t_post_main p ON p.id = q.source_post_id
   WHERE up.uid = COALESCE((SELECT id FROM retest_user LIMIT 1), 0)
     AND q.status = 1 AND p.is_deleted = 0 AND p.post_status = 1 AND p.visibility = 1) AS admin_progress_rows,
  (SELECT COUNT(*)
   FROM t_interview_question q
   JOIN t_post_main p ON p.id = q.source_post_id
   WHERE q.company = '深测科技'
     AND q.status = 1 AND p.is_deleted = 0 AND p.post_status = 1 AND p.visibility = 1) AS shence_visible_questions;
`

let mysqlDefaultsDirectory = ''
let output
try {
  mysqlDefaultsDirectory = mkdtempSync(path.join(tmpdir(), 'offerlab-mysql-'))
  const mysqlDefaultsFile = path.join(mysqlDefaultsDirectory, 'client.cnf')
  writeFileSync(mysqlDefaultsFile, [
    '[client]',
    `password="${escapeOptionFileValue(config.password)}"`,
    '',
  ].join('\n'), { encoding: 'utf8', mode: 0o600 })

  output = execFileSync(mysqlBin, [
    `--defaults-extra-file=${mysqlDefaultsFile}`,
    '--batch',
    '--raw',
    '--skip-column-names',
    '-h', config.host,
    '-P', config.port,
    '-u', config.user,
    config.database,
    '-e',
    sql,
  ], { encoding: 'utf8' }).trim()
} finally {
  if (mysqlDefaultsDirectory) {
    rmSync(mysqlDefaultsDirectory, { recursive: true, force: true })
  }
}

const [
  retestUidValue,
  retestEmailValue,
  questionTotalValue,
  visibleQuestionTotalValue,
  adminPrepTargetsValue,
  adminProgressRowsValue,
  shenceVisibleQuestionsValue,
] = output.split(/\t/)

const retestUid = Number(retestUidValue || 0)
const retestEmail = retestEmailValue || ''
const questionTotal = Number(questionTotalValue || 0)
const visibleQuestionTotal = Number(visibleQuestionTotalValue || 0)
const adminPrepTargets = Number(adminPrepTargetsValue || 0)
const adminProgressRows = Number(adminProgressRowsValue || 0)
const shenceVisibleQuestions = Number(shenceVisibleQuestionsValue || 0)

const checks = [
  [`retest uid exists (${retestEmail || config.adminEmail || 'seed owner -> demo.admin -> admin'})`, retestUid > 0, retestUid],
  ['question_total > 0', questionTotal > 0, questionTotal],
  ['visible_question_total > 0', visibleQuestionTotal > 0, visibleQuestionTotal],
  ['admin_prep_targets > 0', adminPrepTargets > 0, adminPrepTargets],
  ['admin_progress_rows > 0', adminProgressRows > 0, adminProgressRows],
  ['shence_visible_questions > 0', shenceVisibleQuestions > 0, shenceVisibleQuestions],
]

for (const [name, ok, value] of checks) {
  console.log(`${ok ? 'OK' : 'FAIL'} ${name}: ${value}`)
}

if (checks.some(([, ok]) => !ok)) {
  console.error(`${scriptName}: demo question data is not visible for the retest account.`)
  process.exit(1)
}

console.log(`${scriptName}: demo question data is visible for the retest account.`)

function unique(values) {
  return [...new Set(values)]
}

function escapeSql(value) {
  return String(value).replaceAll('\\', '\\\\').replaceAll("'", "''")
}

function escapeOptionFileValue(value) {
  const normalized = String(value)
  if (/[\r\n\0]/.test(normalized)) {
    throw new Error('MySQL password must not contain line breaks or NUL characters.')
  }
  return normalized.replaceAll('\\', '\\\\').replaceAll('"', '\\"')
}
