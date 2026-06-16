import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'

const readScript = (name) => readFileSync(new URL(`./${name}`, import.meta.url), 'utf8')
const readDoc = (path) => readFileSync(new URL(path, import.meta.url), 'utf8')

const smoke = readScript('smoke-offerlab.ps1')
const middleware = readScript('check-local-middleware.ps1')
const verifyLocal = readScript('verify-local.ps1')
const ciQualityGates = readDoc('../docs/ci-quality-gates.md')

function assertUtf8ConsoleGuard(source, name) {
  assert.match(source, /\$Utf8NoBom = \[System\.Text\.UTF8Encoding\]::new\(\$false\)/, `${name} must create a UTF-8 no-BOM encoder`)
  assert.match(source, /\[Console\]::OutputEncoding = \$Utf8NoBom/, `${name} must set console output encoding`)
  assert.match(source, /\$OutputEncoding = \$Utf8NoBom/, `${name} must set PowerShell pipeline output encoding`)
}

assertUtf8ConsoleGuard(smoke, 'smoke-offerlab.ps1')
assertUtf8ConsoleGuard(middleware, 'check-local-middleware.ps1')
assertUtf8ConsoleGuard(verifyLocal, 'verify-local.ps1')

for (const [name, source] of [
  ['smoke-offerlab.ps1', smoke],
  ['check-local-middleware.ps1', middleware],
  ['verify-local.ps1', verifyLocal],
]) {
  assert.doesNotMatch(source, /[^\x00-\x7F]/, `${name} must keep source text ASCII-safe for Windows PowerShell script decoding`)
}

assert.match(smoke, /\[System\.Text\.UTF8Encoding\]::new\(\$false, \$true\)/, 'smoke report writer must use strict UTF-8 decoding')
assert.match(middleware, /\[System\.Text\.UTF8Encoding\]::new\(\$false, \$true\)/, 'middleware probe must use strict UTF-8 decoding')
assert.match(smoke, /EncodingSelfTest/, 'smoke script must expose an encoding self-test mode')
assert.match(smoke, /Test-Mojibake/, 'smoke script must detect mojibake before writing artifacts')
assert.match(smoke, /WriteAllText\(\$ReportPath, \$json, \$script:Utf8NoBomStrict\)/, 'smoke JSON report must be written as strict UTF-8')
assert.match(middleware, /StreamReader.*Utf8NoBomStrict/s, 'middleware JSON responses must be decoded as strict UTF-8')
assert.match(ciQualityGates, /test-powershell-utf8-guards\.mjs/, 'CI quality gates must include the UTF-8 PowerShell guard')

console.log('PowerShell UTF-8 guard passed')
