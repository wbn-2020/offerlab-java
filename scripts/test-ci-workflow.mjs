import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const workflow = readFileSync(new URL('../.github/workflows/ci.yml', import.meta.url), 'utf8')

assert.doesNotMatch(workflow, /\t/, 'backend CI workflow must use YAML spaces rather than tabs')
assert.match(
  workflow,
  / {6}- name: Check out frontend contract source\s+ {8}uses: actions\/checkout@v4\s+ {8}with:\s+ {10}repository: wbn-2020\/offerlab-vue\s+ {10}ref: \$\{\{ vars\.OFFERLAB_FRONTEND_REF \|\| \(github\.head_ref == 'main' \|\| github\.head_ref == 'dev-v2'\) && github\.head_ref \|\| \(github\.ref_name == 'main' \|\| github\.ref_name == 'dev-v2'\) && github\.ref_name \|\| github\.base_ref \|\| 'dev-v2' \}\}\s+ {10}path: offerlab-vue/,
  'frontend checkout must prefer a matching stable PR head before the push branch, PR base, or fallback',
)
assert.doesNotMatch(
  workflow,
  /ref:\s+\$\{\{\s*(?:github\.head_ref|github\.ref_name)\s*\}\}/,
  'feature branch names must not be reused unconditionally across repositories',
)
assert.match(
  workflow,
  /node \.\/scripts\/test-ci-workflow\.mjs/,
  'backend CI must execute its workflow regression guard',
)

console.log('backend CI workflow guard passed')
