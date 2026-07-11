import { readFileSync, readdirSync, statSync } from 'node:fs'
import path from 'node:path'

const roots = ['db/init', 'db/migration']
const sqlFiles = roots.flatMap((root) => readdirSync(root)
  .map(name => path.join(root, name))
  .filter(file => statSync(file).isFile() && file.endsWith('.sql')))

const failures = []
for (const file of sqlFiles) {
  const lines = readFileSync(file, 'utf8').split(/\r?\n/)
  let inString = false
  let stringStartLine = 0

  for (let lineIndex = 0; lineIndex < lines.length; lineIndex += 1) {
    const line = lines[lineIndex]
    for (let index = 0; index < line.length; index += 1) {
      if (line[index] !== "'") continue
      if (inString && line[index + 1] === "'") {
        index += 1
        continue
      }
      inString = !inString
      if (inString) stringStartLine = lineIndex + 1
    }
    if (inString) {
      failures.push(`${file}:${stringStartLine} has an unterminated SQL string literal`)
      inString = false
    }
  }
}

if (failures.length > 0) {
  console.error('SQL source quality check failed:')
  for (const failure of failures) console.error(`- ${failure}`)
  process.exit(1)
}

console.log(`SQL source quality check passed (${sqlFiles.length} files scanned).`)
