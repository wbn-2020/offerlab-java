param(
  [ValidateRange(0, 10)]
  [double]$FailBuildOnCVSS = 7.0
)

$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $root

$arguments = @(
  "-B",
  "-ntp",
  "-Psecurity-audit",
  "-DskipTests",
  "-Ddependency-check.failBuildOnCVSS=$FailBuildOnCVSS"
)

if (-not [string]::IsNullOrWhiteSpace($env:NVD_API_KEY)) {
  $arguments += "-DnvdApiKey=$($env:NVD_API_KEY)"
}

$arguments += "verify"

& mvn @arguments
exit $LASTEXITCODE
