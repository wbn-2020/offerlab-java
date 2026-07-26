param(
  [string] $MigrationDirectory = "",
  [switch] $SkipManifestValidation,
  [switch] $SkipFlywaySync
)

$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$canonicalMigrationDir = Join-Path $root "db\migration"
$usingCanonicalMigrationDir = [string]::IsNullOrWhiteSpace($MigrationDirectory)
$migrationDir = if ($usingCanonicalMigrationDir) {
  $canonicalMigrationDir
} else {
  (Resolve-Path -LiteralPath $MigrationDirectory).Path
}
$manifestPath = Join-Path $migrationDir "flyway-manifest.json"

if (-not (Test-Path $migrationDir)) {
  Write-Error "Migration directory not found: $migrationDir"
}

$files = Get-ChildItem -Path $migrationDir -Filter "*.sql" -File
$violations = New-Object System.Collections.Generic.List[string]

if (-not $SkipManifestValidation) {
  if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    $violations.Add("Flyway manifest is missing: $manifestPath")
  } else {
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $expectedMigrationCount = @($manifest.migrations).Count
    if ($files.Count -ne $expectedMigrationCount) {
      $violations.Add(
        "canonical migration count does not match the manifest: " +
        "expected $expectedMigrationCount, found $($files.Count)"
      )
    }
  }
}

$legacyAllowedRules = @{
  "20260530_notification_dedup_key.sql" = @("ALTER_MODIFY")
  "20260530_relation_unique_keys.sql" = @("DROP_INDEX")
  "20260707_contact_request_settings.sql" = @("ALTER_MODIFY")
  "20260707_contact_request.sql" = @("ALTER_MODIFY")
  "20260708_operation_soft_delete_unique_guard.sql" = @("DROP_INDEX")
  "20260712_unclassified_domain.sql" = @("ALTER_MODIFY")
  "20260713_trusted_content_stage1.sql" = @("DROP_INDEX", "ALTER_MODIFY")
}

$explicitlyAllowableRules = @("DROP_INDEX", "ALTER_MODIFY", "ALTER_CHANGE")

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
  $pendingAllowedRules = New-Object System.Collections.Generic.List[string]
  $startLine = 0
  $lines = @(Get-Content -Path $Path)

  for ($i = 0; $i -lt $lines.Count; $i++) {
    $lineNumber = $i + 1
    $line = $lines[$i]
    $trimmed = $line.Trim()

    if ($trimmed -match "^--\s*migration-safety:\s*allow\s+([A-Z_]+)\s+reason=(\S.*)$") {
      $rule = $Matches[1].ToUpperInvariant()
      if ($explicitlyAllowableRules -notcontains $rule) {
        throw "$Path`:$lineNumber declares unsupported migration safety rule: $rule"
      }
      $pendingAllowedRules.Add($rule)
      continue
    }
    if ($trimmed -match "^--\s*migration-safety:") {
      throw "$Path`:$lineNumber contains an invalid migration safety directive"
    }
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
        AllowedRules = @($pendingAllowedRules)
      })
      $buffer.Clear()
      $pendingAllowedRules.Clear()
      $startLine = 0
    }
  }

  if ($buffer.Count -gt 0) {
    $statements.Add([pscustomobject]@{
      StartLine = $startLine
      Text = ($buffer -join "`n")
      AllowedRules = @($pendingAllowedRules)
    })
    $pendingAllowedRules.Clear()
  }

  if ($pendingAllowedRules.Count -gt 0) {
    throw "$Path contains a migration safety directive without a following SQL statement"
  }

  return $statements
}

function Test-DestructiveRuleAllowed {
  param(
    [string] $FileName,
    [object] $Statement,
    [string] $Rule
  )

  if (@($Statement.AllowedRules) -contains $Rule) {
    return $true
  }
  return $legacyAllowedRules.ContainsKey($FileName) -and
    @($legacyAllowedRules[$FileName]) -contains $Rule
}

foreach ($file in $files) {
  foreach ($statement in Get-SqlStatements -Path $file.FullName) {
    $sql = Remove-SqlComments -Sql $statement.Text
    $lineNumber = $statement.StartLine
    $matchedRules = New-Object System.Collections.Generic.List[string]

    if ($sql -match "(?is)\bDROP\s+DATABASE\b") {
      $violations.Add("$($file.Name):$lineNumber contains DROP DATABASE")
    }
    if ($sql -match "(?is)\bDROP\s+TABLE\b") {
      $violations.Add("$($file.Name):$lineNumber contains DROP TABLE")
    }
    if ($sql -match '(?is)\bALTER\s+TABLE\s+[`"\w.]+\s+(?:(?!;|\$\$).)*?\bDROP\s+(?:COLUMN\s+(?:IF\s+EXISTS\s+)?[`"\w]+|(?:IF\s+EXISTS\s+)?(?!INDEX\b|KEY\b|PRIMARY\b|FOREIGN\b|CHECK\b|CONSTRAINT\b|PARTITION\b|PROCEDURE\b|FUNCTION\b|TRIGGER\b|EVENT\b|DATABASE\b|TABLE\b|VIEW\b|USER\b|ROLE\b|SEQUENCE\b)[`"\w]+)') {
      $violations.Add("$($file.Name):$lineNumber contains DROP COLUMN")
    }
    if ($sql -match "(?is)\bDROP\s+INDEX\b") {
      $matchedRules.Add("DROP_INDEX")
      if (-not (Test-DestructiveRuleAllowed -FileName $file.Name -Statement $statement -Rule "DROP_INDEX")) {
        $violations.Add(
          "$($file.Name):$lineNumber contains DROP INDEX without " +
          "'-- migration-safety: allow DROP_INDEX reason=...'")
      }
    }
    if ($sql -match '(?is)\bMODIFY\s+(?:COLUMN\s+)?[`"\w]') {
      $matchedRules.Add("ALTER_MODIFY")
      if (-not (Test-DestructiveRuleAllowed -FileName $file.Name -Statement $statement -Rule "ALTER_MODIFY")) {
        $violations.Add(
          "$($file.Name):$lineNumber contains MODIFY without " +
          "'-- migration-safety: allow ALTER_MODIFY reason=...'")
      }
    }
    if ($sql -match '(?is)\bCHANGE\s+(?:COLUMN\s+)?[`"\w]') {
      $matchedRules.Add("ALTER_CHANGE")
      if (-not (Test-DestructiveRuleAllowed -FileName $file.Name -Statement $statement -Rule "ALTER_CHANGE")) {
        $violations.Add(
          "$($file.Name):$lineNumber contains CHANGE without " +
          "'-- migration-safety: allow ALTER_CHANGE reason=...'")
      }
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
    foreach ($allowedRule in @($statement.AllowedRules)) {
      if (-not $matchedRules.Contains($allowedRule)) {
        $violations.Add(
          "$($file.Name):$lineNumber declares unused migration safety rule $allowedRule")
      }
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

if (-not $SkipFlywaySync) {
  if (-not $usingCanonicalMigrationDir) {
    throw "Flyway sync is only supported for the canonical migration directory"
  }
  & (Join-Path $migrationDir "sync-flyway-resources.ps1")
}

Write-Host "Migration safety check passed for $($files.Count) migration file(s)."
