<#
  Local baseline verification entry for OfferLab backend.

  Scope:
  - Runs migration safety and local middleware/config checks.
  - Runs test-compile plus focused backend API/RBAC/domain tests.
  - Does not start services.
  - Does not execute database migrations.

  Semantics:
  - This script is not a pure static/read-only inspector.
  - This script is also not evidence of runtime integration passing.
  - A successful run only means the current repository passes the selected
    local baseline checks and focused verification commands.

  Runtime-dependent behavior must still be verified separately after:
  - services are started,
  - required migrations are applied,
  - real API/browser/database flows are exercised.
#>
param(
  [switch]$StrictMiddleware,
  [string]$BackendUrl = "http://127.0.0.1:8080"
)

$ErrorActionPreference = "Stop"

$Utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $Utf8NoBom
[Console]::InputEncoding = $Utf8NoBom
$OutputEncoding = $Utf8NoBom

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $root

Write-Host "Running local baseline verification: migration safety check..."
& .\scripts\check-migration-safety.ps1

Write-Host "Running local baseline verification: Flyway lifecycle guard..."
& node .\scripts\test-flyway-migration-lifecycle.mjs

if ($StrictMiddleware) {
  Write-Host "Running local baseline verification: schema readiness check..."
  & node .\scripts\check-schema-readiness.mjs
}

Write-Host "Running local baseline verification: middleware path/config check..."
if ($StrictMiddleware) {
  & .\scripts\check-local-middleware.ps1 -BackendUrl $BackendUrl -RequireBackendReadiness -RequireCoreApiProbes
} else {
  & .\scripts\check-local-middleware.ps1 -WarnOnly -SkipNetworkProbe -BackendUrl $BackendUrl
}

Write-Host "Running local baseline verification: backend test-compile suite..."
& mvn --% -pl community-bootstrap -am -DskipTests test-compile
& mvn --% -pl community-domain-analytics -am -DskipTests test-compile

Write-Host "Running local baseline verification: backend API/RBAC focused tests..."
& mvn --% -pl community-bootstrap -am -Dtest=AuthControllerApiTest,InteractionControllerApiTest,QuestionAdminControllerApiTest,NotificationOpsControllerApiTest,OpsControllerStatusApiTest,AuthInterceptorRedisFailureTest,OpsControllerWriteApiTest,HealthControllerReadinessTest,PostControllerApiTest,ContentAssistDashboardControllerApiTest,KnowledgeControllerApiTest,GrowthInsightControllerApiTest,ExpertCertificationControllerApiTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test

Write-Host "Running local baseline verification: backend stage3/stage4 domain-focused tests..."
& mvn --% -pl community-domain-user -am -Dtest=NotificationPreferenceAliasGuardTest,UserTaskStateSafetyGuardTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
& mvn --% -pl community-domain-post -am -Dtest=ContentAssistServiceTest,ContentSeriesServiceTest,ExpertCertificationServiceTest,KnowledgeRelationServiceTest,ExpertCertificationGuardTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
& mvn --% -pl community-domain-analytics -am -Dtest=GrowthEventGuardTest,GrowthEventListenerTest,GrowthEventServiceTest,GrowthInsightServiceTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test

Write-Host "Local baseline verification passed. This does not imply runtime integration has passed."
