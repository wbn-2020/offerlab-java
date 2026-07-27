# OfferLab CI Quality Gates

This document is platform neutral. The stages below can be mapped to GitHub Actions, Jenkins, GitLab CI, or a local release script without changing the required checks.

## Backend

1. Read-only migration and schema gates
   - `powershell -ExecutionPolicy Bypass -File .\scripts\check-migration-safety.ps1`
   - `node .\scripts\check-schema-readiness.mjs --json`
   - The schema readiness gate must be run against a dedicated local/test database. It intentionally fails when reviewed migrations are missing.

2. Unit and guard tests
   - `mvn -pl community-domain-question,community-domain-search,community-domain-post,community-domain-interaction,community-domain-feed,community-domain-user,community-infrastructure -am test`

3. Bootstrap compile and context readiness
   - `mvn -pl community-bootstrap -am -DskipTests compile`
   - Optional runtime smoke after services are configured: `GET /api/v1/health/liveness`, `GET /api/v1/health/readiness`, and `GET /api/v1/health/readiness/strict`
   - Strict readiness must return HTTP 200 before a demo or release sign-off; HTTP 503 means schema, middleware, outbox, or retry backlog still blocks full readiness.

4. Local middleware preflight for Windows demos
   - PowerShell UTF-8 artifact guard: `node .\scripts\test-powershell-utf8-guards.mjs`
   - Warn-only, read-only check: `powershell -ExecutionPolicy Bypass -File .\scripts\check-local-middleware.ps1 -WarnOnly -SkipNetworkProbe`
   - Strict check after services are expected to be running: `powershell -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1 -StrictMiddleware`
   - Do not fix Kafka `*.checkpoint.deleted` permissions, run database migrations, or modify system configuration in CI without a reviewed, explicitly confirmed operation.

5. Integration profile
   - Run module integration tests only against disposable or dedicated test middleware.
   - Do not run destructive schema reset commands in CI unless the database is provisioned per run.
   - CI command: `mvn -pl community-infrastructure,community-domain-user -am -Pintegration-tests verify`
   - The current integration suite has five Testcontainers classes and skips automatically when Docker is unavailable locally; CI must provide Docker and reject failed, missing, or skipped Failsafe reports.

6. Cross-repository contracts
   - Backend and frontend CI explicitly check out the counterpart repository into sibling directories before running source-contract guards.
   - A workflow must never rely on an undeclared directory left by a developer machine.
   - Pull requests from `main` or `dev-v2` use the matching counterpart branch; other pull requests use their base branch. Stable-branch pushes use the matching branch, and other pushes fall back to `dev-v2`.
   - Set the repository variable `OFFERLAB_FRONTEND_REF` or `OFFERLAB_BACKEND_REF` when a coordinated contract branch must override that fallback.

## Frontend

1. Static guard tests
   - `npm run test:guards`

2. Type check and production build
   - `npm run typecheck`
   - `npm run build`

3. Critical E2E smoke
   - Login and auth hydration after refresh
   - Public search and admin-only rebuild visibility
   - Post publish, detail, comment, like, favorite
   - Question list/detail, progress, notes
   - Mock interview start, draft save, submit, AI review pending/failed/retry states
   - Notification list and read/unread actions

## Artifacts

Each CI run should publish:

- Maven Surefire reports
- Maven Failsafe reports
- Executable backend JAR, aggregate SBOM, and SHA-256 checksums
- Frontend guard output
- Frontend build logs
- E2E traces or screenshots on failure
- Visual snapshot `summary.json` and desktop/mobile screenshots when `npm run test:visual-snapshots` is enabled
- Coverage reports when enabled by the selected test runner
