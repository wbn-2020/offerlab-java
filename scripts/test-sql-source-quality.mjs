import assert from 'node:assert/strict'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import path from 'node:path'

const roots = ['db/init', 'db/migration', 'db/demo', 'db/local']
const sqlFiles = roots.flatMap((root) => readdirSync(root)
  .map(name => path.join(root, name))
  .filter(file => statSync(file).isFile() && file.endsWith('.sql')))

const findLexicalIssue = (source) => {
  let quoteDelimiter = ''
  let quoteType = ''
  let inLineComment = false
  let inBlockComment = false
  let inExecutableComment = false
  let quoteStartLine = 0
  let blockCommentStartLine = 0
  let lineNumber = 1

  for (let index = 0; index < source.length; index += 1) {
    const character = source[index]
    const nextCharacter = source[index + 1]

    if (character === '\n') {
      lineNumber += 1
      if (inLineComment) inLineComment = false
      continue
    }
    if (inLineComment) continue
    if (inBlockComment) {
      if (character === '*' && nextCharacter === '/') {
        inBlockComment = false
        index += 1
      }
      continue
    }
    if (quoteDelimiter) {
      if (character === '\\' && quoteDelimiter !== '`') {
        if (nextCharacter === '\n') lineNumber += 1
        index += 1
        continue
      }
      if (character !== quoteDelimiter) continue
      if (nextCharacter === quoteDelimiter) {
        index += 1
        continue
      }
      quoteDelimiter = ''
      quoteType = ''
      continue
    }
    if (inExecutableComment && character === '*' && nextCharacter === '/') {
      inExecutableComment = false
      index += 1
      continue
    }
    const afterDashPair = source[index + 2]
    if (character === '-' && nextCharacter === '-'
        && (afterDashPair === undefined || /\s/.test(afterDashPair))) {
      inLineComment = true
      index += 1
      continue
    }
    if (character === '#') {
      inLineComment = true
      continue
    }
    if (character === '/' && nextCharacter === '*') {
      if (source[index + 2] === '!') {
        inExecutableComment = true
        blockCommentStartLine = lineNumber
        index += 2
        continue
      }
      inBlockComment = true
      blockCommentStartLine = lineNumber
      index += 1
      continue
    }
    if (character === "'" || character === '"' || character === '`') {
      quoteDelimiter = character
      quoteType = character === '`' ? 'quoted identifier' : 'string literal'
      quoteStartLine = lineNumber
    }
  }

  if (quoteDelimiter) {
    return { type: quoteType, line: quoteStartLine }
  }
  if (inBlockComment || inExecutableComment) {
    return { type: 'block comment', line: blockCommentStartLine }
  }
  return null
}

assert.equal(findLexicalIssue("-- claimant's pending deliverable\r\nSELECT 'ok';"), null)
assert.equal(findLexicalIssue("# author's note\nSELECT 'ok';"), null)
assert.equal(findLexicalIssue("/* author's note */ SELECT 'Claimant''s note';"), null)
assert.equal(findLexicalIssue('SELECT "double-quoted";'), null)
assert.equal(findLexicalIssue('SELECT `quoted``identifier`;'), null)
assert.equal(findLexicalIssue("SELECT 'multi\nline';"), null)
assert.equal(findLexicalIssue("/*!40101 SET @saved_sql_mode = @@sql_mode */;"), null)
assert.equal(findLexicalIssue("/*!80000 SELECT 'executed string' */;"), null)
assert.deepEqual(findLexicalIssue("SELECT 1--not-a-comment 'unterminated;"), {
  type: 'string literal',
  line: 1,
})
assert.deepEqual(findLexicalIssue("SELECT 'unterminated;"), { type: 'string literal', line: 1 })
assert.deepEqual(findLexicalIssue("SELECT 'first line\nunterminated;"), { type: 'string literal', line: 1 })
assert.deepEqual(findLexicalIssue('SELECT "unterminated;'), { type: 'string literal', line: 1 })
assert.deepEqual(findLexicalIssue('SELECT `unterminated;'), { type: 'quoted identifier', line: 1 })
assert.deepEqual(findLexicalIssue("SELECT 'trailing backslash\\"), { type: 'string literal', line: 1 })
assert.deepEqual(findLexicalIssue("SELECT 'escaped\\\nline';\nSELECT 'unterminated"), {
  type: 'string literal',
  line: 3,
})
assert.deepEqual(findLexicalIssue("/* unterminated"), { type: 'block comment', line: 1 })
assert.deepEqual(findLexicalIssue("/*!40101 unterminated"), { type: 'block comment', line: 1 })
assert.deepEqual(findLexicalIssue("/*!80000 SELECT 'unterminated */"), {
  type: 'string literal',
  line: 1,
})

const failures = []
for (const file of sqlFiles) {
  const issue = findLexicalIssue(readFileSync(file, 'utf8'))
  if (issue != null) {
    failures.push(`${file}:${issue.line} has an unterminated SQL ${issue.type}`)
  }
}

if (failures.length > 0) {
  console.error('SQL source quality check failed:')
  for (const failure of failures) console.error(`- ${failure}`)
  process.exit(1)
}

console.log(`SQL source quality check passed (${sqlFiles.length} files scanned).`)
