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

Write-Host "Running migration safety check..."
& .\scripts\check-migration-safety.ps1

if ($StrictMiddleware) {
  Write-Host "Running read-only schema readiness check..."
  & node .\scripts\check-schema-readiness.mjs
}

Write-Host "Running local middleware path/config check..."
if ($StrictMiddleware) {
  & .\scripts\check-local-middleware.ps1 -BackendUrl $BackendUrl -RequireBackendReadiness -RequireCoreApiProbes
} else {
  & .\scripts\check-local-middleware.ps1 -WarnOnly -SkipNetworkProbe -BackendUrl $BackendUrl
}

Write-Host "Running backend focused test suite..."
& mvn --% -pl community-bootstrap -am -DskipTests test-compile
& mvn --% -pl community-domain-analytics -am -DskipTests test-compile

Write-Host "Running backend API/RBAC focused test suite..."
& mvn --% -pl community-bootstrap -am -Dtest=AuthControllerApiTest,InteractionControllerApiTest,QuestionAdminControllerApiTest,NotificationOpsControllerApiTest,OpsControllerStatusApiTest,AuthInterceptorRedisFailureTest,OpsControllerWriteApiTest,HealthControllerReadinessTest,PostControllerApiTest,ContentAssistDashboardControllerApiTest,KnowledgeControllerApiTest,GrowthInsightControllerApiTest,ExpertCertificationControllerApiTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test

Write-Host "Running backend stage3/stage4 domain-focused tests..."
& mvn --% -pl community-domain-user -am -Dtest=NotificationPreferenceAliasGuardTest,UserTaskStateSafetyGuardTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
& mvn --% -pl community-domain-post -am -Dtest=ContentAssistServiceTest,ContentSeriesServiceTest,ExpertCertificationServiceTest,KnowledgeRelationServiceTest,ExpertCertificationGuardTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
& mvn --% -pl community-domain-analytics -am -Dtest=GrowthEventGuardTest,GrowthEventListenerTest,GrowthEventServiceTest,GrowthInsightServiceTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test

Write-Host "Backend local verification passed."
