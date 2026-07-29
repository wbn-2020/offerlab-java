# OfferLab Backend V22 Readiness

> Version: V22
> Theme: Local runtime reliability and demo trustworthiness
> Status: STATIC_VERIFIED / QA_PASSED / PR_CI_PENDING
> Branch: `feature/v22-local-runtime-reliability`
> Base: `dev-v2@25d2cef8b5f91ea85d3cae721878a63cdfa812bf`

## Scope

- Track a shareable `application-local.yml` compatible with the current Redisson starter.
- Bind the local profile to loopback by default and keep Kafka, Kafka Admin, and Elasticsearch opt-in.
- Prevent recommendation scoring from failing on null or unsupported post domains.
- Repair user-visible demo mojibake and remove the external DiceBear dependency.
- Keep deterministic demo identities and natural keys fail-closed before upsert.
- Make fresh-init seed writes atomic and provide a transactional refresh entry for existing local databases.
- Provide an explicit, transactional, local-only admin promotion script.
- Harden database verification scripts so credentials do not enter process arguments.

## Contract

- MySQL `8.0.16+` is required because the seed assertions rely on enforced `CHECK` constraints.
- No new API endpoint or response contract.
- No production database migration or production profile behavior change.
- Historical migration SQL, Flyway resources, and manifest entries remain immutable.
- Existing `dev-v2 -> main` and V21 pull requests remain untouched.

## Verification

| Check | Result | Evidence |
|---|---|---|
| Local profile and security guards | PASS | Current Redisson class, loopback binding, opt-in middleware, credential overrides |
| Recommendation-domain tests | PASS | Null, unknown, empty-set, immutable-set, and valid-domain cases |
| Demo identity/text guards | PASS | Canonical seed, refresh, local admin, mojibake, deterministic ownership |
| SQL source lexical quality | PASS | 107 SQL files scanned |
| Full Maven test suite | PASS | 14 modules; 264 Surefire reports / 895 tests / 0 failures / 0 errors / 0 skipped |
| CI workflow guard | PASS | `node scripts/test-ci-workflow.mjs` |
| Flyway lifecycle guard | PASS | Immutable migration plus exact V22 fresh-init safety transforms |
| Migration content hash | PASS | LF, CRLF, and UTF-8 BOM fixtures |
| Migration safety checks | PASS | 73 migrations; sync, destructive-policy, and fixture checks |
| Database script syntax | PASS | Node syntax checks plus SQL lexical scan |
| API / production migration scope | PASS | No controller/API contract or `db/migration/*.sql` change |
| `git diff --check` | PASS | Latest working tree |
| Independent database QA | PASS | Final review: 0 blocker / 0 high |
| Independent cross-stack QA | PASS | Final review: 0 blocker / 0 high |
| Pull request CI | PENDING | |

## Dynamic Boundary

No frontend/backend service, database, middleware, container, or browser was started
for V22 implementation or final verification. Static tests do not prove:

- real MySQL execution of fresh init, existing-database refresh, or local admin promotion;
- disconnect/reconnect and concurrent reserved-identity behavior;
- local Spring context startup with a real MySQL/Redis pair;
- HTTP recommendation behavior.

Existing local databases must use the documented
`mysql --skip-force --skip-reconnect ... --execute="SOURCE ..."` refresh entry.
The refresh script is outside production Flyway and is not executed automatically.
