#!/usr/bin/env node
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'

const args = new Map()
for (let i = 2; i < process.argv.length; i += 1) {
  const token = process.argv[i]
  if (!token.startsWith('--')) continue
  const key = token.slice(2)
  const next = process.argv[i + 1]
  if (!next || next.startsWith('--')) {
    args.set(key, 'true')
  } else {
    args.set(key, next)
    i += 1
  }
}

const usage = `
Usage:
  node scripts/preview-mojibake-repair.mjs --input exports/posts.jsonl --report reports/mojibake-preview.md
  node scripts/preview-mojibake-repair.mjs --input exports/posts.jsonl --fix-map fixes.json --confirm PREVIEW_ONLY --sql reports/mojibake-repair.sql

Input rows must include table, id, and one or more text columns. JSON, JSONL, and simple CSV are supported.
This tool never connects to a database and never executes UPDATE statements.
`

const input = args.get('input')
if (!input) {
  console.error(usage.trim())
  process.exit(2)
}

const reportPath = args.get('report') || 'reports/mojibake-preview.md'
const sqlPath = args.get('sql') || 'reports/mojibake-repair.sql'
const fixMapPath = args.get('fix-map')
const confirm = args.get('confirm')

const MOJIBAKE_MARKERS = [
  /\?{4,}/,
  /\uFFFD/,
  /Ã[\x80-\xBF]/,
  /(?:鐧|鎶|娴|绠|诲|锛|鏂|棣|佹|垪|鍚|绋|棰|煡|瑙|槸|姝|湪)/,
]

const textColumns = [
  'title',
  'summary',
  'content',
  'description',
  'tag_name',
  'name',
  'reason',
  'detail',
  'ext_json',
]

const readRows = (file) => {
  const source = readFileSync(file, 'utf8').trim()
  if (!source) return []
  if (source.startsWith('[')) return JSON.parse(source)
  if (source.startsWith('{')) return source.split(/\r?\n/).filter(Boolean).map((line) => JSON.parse(line))
  return parseCsv(source)
}

const parseCsv = (source) => {
  const [headerLine, ...lines] = source.split(/\r?\n/)
  const headers = splitCsvLine(headerLine)
  return lines.filter(Boolean).map((line) => {
    const values = splitCsvLine(line)
    return Object.fromEntries(headers.map((header, index) => [header, values[index] || '']))
  })
}

const splitCsvLine = (line) => {
  const values = []
  let current = ''
  let quoted = false
  for (let i = 0; i < line.length; i += 1) {
    const char = line[i]
    if (char === '"' && line[i + 1] === '"') {
      current += '"'
      i += 1
    } else if (char === '"') {
      quoted = !quoted
    } else if (char === ',' && !quoted) {
      values.push(current)
      current = ''
    } else {
      current += char
    }
  }
  values.push(current)
  return values
}

const isSuspicious = (value) => typeof value === 'string' && MOJIBAKE_MARKERS.some((pattern) => pattern.test(value))
const previewValue = (value) => String(value).replace(/\s+/g, ' ').slice(0, 120)
const sqlString = (value) => `'${String(value).replaceAll('\\', '\\\\').replaceAll("'", "''")}'`
const sqlIdent = (value) => {
  if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(String(value))) {
    throw new Error(`Unsafe SQL identifier: ${value}`)
  }
  return `\`${value}\``
}
const hash = (value) => createHash('sha256').update(String(value)).digest('hex').slice(0, 12)

const rows = readRows(resolve(input))
const hits = []
for (const row of rows) {
  const table = row.table || row.table_name || 'unknown_table'
  const id = row.id || row.post_id || row.tag_id || row.report_id
  for (const column of textColumns) {
    if (!Object.prototype.hasOwnProperty.call(row, column)) continue
    const value = row[column]
    if (!isSuspicious(value)) continue
    hits.push({
      table,
      id,
      column,
      hash: hash(value),
      sample: previewValue(value),
      original: String(value),
    })
  }
}

const report = [
  '# Mojibake Repair Preview',
  '',
  `Input: ${resolve(input)}`,
  `Rows scanned: ${rows.length}`,
  `Suspicious cells: ${hits.length}`,
  '',
  '| table | id | column | hash | sample |',
  '|---|---:|---|---|---|',
  ...hits.map((hit) => `| ${hit.table} | ${hit.id ?? ''} | ${hit.column} | ${hit.hash} | ${hit.sample.replaceAll('|', '\\|')} |`),
  '',
  'No database writes were executed. Review this report before preparing any repair SQL.',
]

mkdirSync(dirname(resolve(reportPath)), { recursive: true })
writeFileSync(resolve(reportPath), `${report.join('\n')}\n`, 'utf8')

if (fixMapPath || confirm) {
  if (confirm !== 'PREVIEW_ONLY') {
    throw new Error('Use --confirm PREVIEW_ONLY to generate SQL. This still does not execute database writes.')
  }
  if (!fixMapPath || !existsSync(resolve(fixMapPath))) {
    throw new Error('--fix-map is required with --confirm PREVIEW_ONLY')
  }
  const fixes = JSON.parse(readFileSync(resolve(fixMapPath), 'utf8'))
  const statements = [
    '-- Review manually before execution. Generated SQL is exact-row and exact-old-value guarded.',
    'START TRANSACTION;',
  ]
  for (const hit of hits) {
    const fix = fixes[`${hit.table}:${hit.id}:${hit.column}`]
    if (!fix) continue
    statements.push(
      `UPDATE ${sqlIdent(hit.table)} SET ${sqlIdent(hit.column)} = ${sqlString(fix)} WHERE ${sqlIdent('id')} = ${sqlString(hit.id)} AND ${sqlIdent(hit.column)} = ${sqlString(hit.original)};`
    )
  }
  statements.push('-- Inspect affected row counts, then COMMIT manually. Use ROLLBACK if anything is unexpected.')
  statements.push('ROLLBACK;')
  mkdirSync(dirname(resolve(sqlPath)), { recursive: true })
  writeFileSync(resolve(sqlPath), `${statements.join('\n')}\n`, 'utf8')
}

console.log(`mojibake preview complete: ${hits.length} suspicious cells, report=${resolve(reportPath)}`)
