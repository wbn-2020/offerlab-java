$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $root

Write-Host "Running migration safety check..."
& .\scripts\check-migration-safety.ps1

Write-Host "Running local middleware path/config check..."
& .\scripts\check-local-middleware.ps1 -WarnOnly -SkipNetworkProbe

Write-Host "Running backend focused test suite..."
& mvn -pl community-domain-user,community-infrastructure,community-domain-search,community-domain-question,community-archtest -am test

Write-Host "Running backend API/RBAC focused test suite..."
& mvn -pl community-bootstrap -am "-Dtest=AuthControllerApiTest,InteractionControllerApiTest,QuestionAdminControllerApiTest,NotificationOpsControllerApiTest,OpsControllerStatusApiTest,AuthInterceptorRedisFailureTest,OpsControllerWriteApiTest,HealthControllerReadinessTest" "-Dsurefire.failIfNoSpecifiedTests=false" test

Write-Host "Backend local verification passed."
