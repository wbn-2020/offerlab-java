param(
  [string]$Root = "C:\codeware"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$redisHome = Join-Path $Root "redis"
$redisExe = Join-Path $redisHome "redis-server.exe"
$redisConfig = Join-Path $redisHome "redis.conf"

if (-not (Test-Path -LiteralPath $redisExe)) {
  Write-Error "Redis executable not found: $redisExe"
}
if (-not (Test-Path -LiteralPath $redisConfig)) {
  Write-Error "Redis config not found: $redisConfig"
}

Write-Output "Starting Redis in foreground with working directory: $redisHome"
Set-Location -LiteralPath $redisHome
& $redisExe ".\redis.conf"

