$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $root

Write-Host "Running migration safety check..."
& .\scripts\check-migration-safety.ps1

Write-Host "Running backend focused test suite..."
& mvn -pl community-domain-user,community-infrastructure,community-domain-search,community-domain-question,community-archtest -am test

Write-Host "Backend local verification passed."
