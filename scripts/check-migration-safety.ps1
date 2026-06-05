$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$migrationDir = Join-Path $root "db\migration"

if (-not (Test-Path $migrationDir)) {
  Write-Error "Migration directory not found: $migrationDir"
}

$files = Get-ChildItem -Path $migrationDir -Filter "*.sql" -File
$violations = New-Object System.Collections.Generic.List[string]

function Remove-SqlComments {
  param([string] $Sql)

  $withoutBlockComments = [regex]::Replace($Sql, "(?s)/\*.*?\*/", " ")
  $lines = $withoutBlockComments -split "`r?`n"
  return (($lines | ForEach-Object { [regex]::Replace($_, "--.*$", " ") }) -join "`n")
}

function Get-SqlStatements {
  param([string] $Path)

  $statements = New-Object System.Collections.Generic.List[object]
  $buffer = New-Object System.Collections.Generic.List[string]
  $startLine = 0
  $lines = Get-Content -Path $Path

  for ($i = 0; $i -lt $lines.Count; $i++) {
    $lineNumber = $i + 1
    $line = $lines[$i]
    $trimmed = $line.Trim()

    if ($trimmed -eq "" -or $trimmed.StartsWith("--")) {
      continue
    }

    if ($startLine -eq 0) {
      $startLine = $lineNumber
    }
    $buffer.Add($line)

    if ($line -match ";") {
      $statements.Add([pscustomobject]@{
        StartLine = $startLine
        Text = ($buffer -join "`n")
      })
      $buffer.Clear()
      $startLine = 0
    }
  }

  if ($buffer.Count -gt 0) {
    $statements.Add([pscustomobject]@{
      StartLine = $startLine
      Text = ($buffer -join "`n")
    })
  }

  return $statements
}

foreach ($file in $files) {
  foreach ($statement in Get-SqlStatements -Path $file.FullName) {
    $sql = Remove-SqlComments -Sql $statement.Text
    $lineNumber = $statement.StartLine

    if ($sql -match "(?is)\bDROP\s+TABLE\b") {
      $violations.Add("$($file.Name):$lineNumber contains DROP TABLE")
    }
    if ($sql -match "(?is)\bTRUNCATE\b") {
      $violations.Add("$($file.Name):$lineNumber contains TRUNCATE")
    }
    if ($sql -match "(?is)^\s*DELETE\s+FROM\b" -and $sql -notmatch "(?is)\bWHERE\b") {
      $violations.Add("$($file.Name):$lineNumber contains DELETE without WHERE")
    }
    if ($sql -match "(?is)^\s*UPDATE\b" -and $sql -notmatch "(?is)\bWHERE\b") {
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
