# OfferLab CI Quality Gates

This document is platform neutral. The stages below can be mapped to GitHub Actions, Jenkins, GitLab CI, or a local release script without changing the required checks.

## Backend

1. Unit and guard tests
   - `mvn -pl community-domain-question,community-domain-search,community-domain-post,community-domain-interaction,community-domain-feed,community-domain-user,community-infrastructure -am test`

2. Bootstrap compile and context readiness
   - `mvn -pl community-bootstrap -am -DskipTests compile`
   - Optional runtime smoke after services are configured: `GET /api/v1/health/liveness` and `GET /api/v1/health/readiness`

3. Integration profile
   - Run module integration tests only against disposable or dedicated test middleware.
   - Do not run destructive schema reset commands in CI unless the database is provisioned per run.
   - Suggested command shape: `mvn -pl community-infrastructure -Pit verify`

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
- Frontend guard output
- Frontend build logs
- E2E traces or screenshots on failure
- Coverage reports when enabled by the selected test runner
