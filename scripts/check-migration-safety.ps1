$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$migrationDir = Join-Path $root "db\migration"

if (-not (Test-Path $migrationDir)) {
  Write-Error "Migration directory not found: $migrationDir"
}

$files = Get-ChildItem -Path $migrationDir -Filter "*.sql" -File
$violations = New-Object System.Collections.Generic.List[string]

foreach ($file in $files) {
  $lines = Get-Content -Path $file.FullName
  for ($i = 0; $i -lt $lines.Count; $i++) {
    $lineNumber = $i + 1
    $line = $lines[$i].Trim()
    if ($line -eq "" -or $line.StartsWith("--")) {
      continue
    }

    if ($line -match "(?i)\bDROP\s+TABLE\b") {
      $violations.Add("$($file.Name):$lineNumber contains DROP TABLE")
    }
    if ($line -match "(?i)\bTRUNCATE\b") {
      $violations.Add("$($file.Name):$lineNumber contains TRUNCATE")
    }
    if ($line -match "(?i)^\s*DELETE\s+FROM\b" -and $line -notmatch "(?i)\bWHERE\b") {
      $violations.Add("$($file.Name):$lineNumber contains DELETE without WHERE")
    }
    if ($line -match "(?i)^\s*UPDATE\b" -and $line -notmatch "(?i)\bWHERE\b") {
      $violations.Add("$($file.Name):$lineNumber contains UPDATE without WHERE")
    }
  }
}

if ($violations.Count -gt 0) {
  Write-Host "Migration safety check failed:" -ForegroundColor Red
  foreach ($violation in $violations) {
    Write-Host "- $violation" -ForegroundColor Red
  }
  exit 1
}

Write-Host "Migration safety check passed for $($files.Count) migration file(s)."
