param(
  [string]$Root = "C:\codeware"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$esHome = Join-Path $Root "elasticsearch-8.14.3"
$esBat = Join-Path $esHome "bin\elasticsearch.bat"
$dataDir = Join-Path $esHome "data"
$logsDir = Join-Path $esHome "logs"

if (-not (Test-Path -LiteralPath $esBat)) {
  Write-Error "Elasticsearch launcher not found: $esBat"
}

$previousClasspath = $env:CLASSPATH
$previousJavaToolOptions = $env:JAVA_TOOL_OPTIONS

try {
  $env:CLASSPATH = ""
  $env:JAVA_TOOL_OPTIONS = ""
  Write-Output "Starting Elasticsearch in foreground with process-local CLASSPATH/JAVA_TOOL_OPTIONS cleared."
  Set-Location -LiteralPath $esHome
  & $esBat "-Epath.data=$dataDir" "-Epath.logs=$logsDir"
} finally {
  $env:CLASSPATH = $previousClasspath
  $env:JAVA_TOOL_OPTIONS = $previousJavaToolOptions
}

