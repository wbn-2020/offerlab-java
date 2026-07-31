param(
  [switch]$SkipCompile
)

$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $root

$arguments = @("-B", "-ntp", "-Psbom", "-DskipTests")
if ($SkipCompile) {
  $arguments += "validate"
  $arguments += "org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom"
} else {
  $arguments += "package"
}

& mvn @arguments
if ($LASTEXITCODE -ne 0) {
  exit $LASTEXITCODE
}

Write-Host "SBOM generated under $root\target."
