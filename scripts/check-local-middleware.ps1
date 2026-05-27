Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = "C:\codeware"
$redis = Join-Path $root "redis6.2.18"
$es = Join-Path $root "elasticsearch-8.14.3"
$kafka = Join-Path $root "kafka_2.13-3.6.2"
$kafkaData = Join-Path $root "kafka-data"
$kafkaLogs = Join-Path $root "kafka-logs"

$expectedPaths = @($root, $redis, $es, $kafka, $kafkaData, $kafkaLogs)
$missing = New-Object System.Collections.Generic.List[string]
foreach ($path in $expectedPaths) {
  if (-not (Test-Path -LiteralPath $path)) {
    $missing.Add($path)
  }
}

$warnings = New-Object System.Collections.Generic.List[string]
$esConfig = Join-Path $es "config\elasticsearch.yml"
if (Test-Path -LiteralPath $esConfig) {
  $content = Get-Content -LiteralPath $esConfig -Raw
  if ($content -match "C:\\my-claude\\comware") {
    $warnings.Add("Elasticsearch config still references C:\my-claude\comware instead of C:\codeware.")
  }
  if ($content -notmatch "path\.data" -or $content -notmatch "path\.logs") {
    $warnings.Add("Elasticsearch config does not explicitly declare path.data and path.logs.")
  }
} else {
  $warnings.Add("Elasticsearch config not found: $esConfig")
}

if ($missing.Count -gt 0) {
  Write-Error ("Missing local middleware path(s):`n- " + ($missing -join "`n- "))
}

if ($warnings.Count -gt 0) {
  Write-Warning ("Local middleware warning(s):`n- " + ($warnings -join "`n- "))
}

Write-Output "Local middleware path check completed for $root"
