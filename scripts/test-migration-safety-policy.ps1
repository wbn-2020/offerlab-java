$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$checker = Join-Path $root "scripts\check-migration-safety.ps1"
$shell = (Get-Process -Id $PID).Path
$systemTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$tempRoot = [System.IO.Path]::GetFullPath((Join-Path $systemTemp (
  "offerlab-migration-safety-" + [Guid]::NewGuid().ToString("N"))
))
if (-not $tempRoot.StartsWith($systemTemp, [StringComparison]::OrdinalIgnoreCase)) {
  throw "Refusing to use a migration fixture directory outside the system temp directory: $tempRoot"
}

function Invoke-PolicyCase {
  param(
    [string] $Name,
    [string] $Sql,
    [int] $ExpectedExitCode
  )

  $caseDirectory = Join-Path $tempRoot $Name
  New-Item -ItemType Directory -Path $caseDirectory -Force | Out-Null
  Set-Content -LiteralPath (Join-Path $caseDirectory "20260726_fixture.sql") -Value $Sql -Encoding utf8

  & $shell -NoProfile -File $checker `
    -MigrationDirectory $caseDirectory `
    -SkipManifestValidation `
    -SkipFlywaySync *> $null
  $actualExitCode = $LASTEXITCODE
  if ($actualExitCode -ne $ExpectedExitCode) {
    throw "Migration safety case '$Name' expected exit $ExpectedExitCode, got $actualExitCode"
  }
  $global:LASTEXITCODE = 0
}

try {
  New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null

  Invoke-PolicyCase "safe-create" @"
CREATE TABLE t_safe_fixture (
  id BIGINT NOT NULL PRIMARY KEY
);
"@ 0

  Invoke-PolicyCase "drop-database" "DROP DATABASE offerlab;" 1
  Invoke-PolicyCase "drop-column" "ALTER TABLE t_fixture DROP COLUMN legacy_value;" 1
  Invoke-PolicyCase "drop-column-shorthand" "ALTER TABLE t_fixture DROP legacy_value;" 1
  Invoke-PolicyCase "drop-index" "ALTER TABLE t_fixture DROP INDEX idx_fixture;" 1
  Invoke-PolicyCase "modify-column" "ALTER TABLE t_fixture MODIFY COLUMN value VARCHAR(8);" 1
  Invoke-PolicyCase "change-column" "ALTER TABLE t_fixture CHANGE COLUMN value value_new VARCHAR(8);" 1

  Invoke-PolicyCase "reviewed-drop-index" @"
-- migration-safety: allow DROP_INDEX reason=replaced atomically by a covering index
ALTER TABLE t_fixture DROP INDEX idx_fixture, ADD INDEX idx_fixture_v2 (id, value);
"@ 0

  Invoke-PolicyCase "reviewed-modify" @"
-- migration-safety: allow ALTER_MODIFY reason=widening a nullable text column
ALTER TABLE t_fixture MODIFY COLUMN value VARCHAR(512) NULL;
"@ 0

  Invoke-PolicyCase "unused-directive" @"
-- migration-safety: allow DROP_INDEX reason=stale exception must fail
CREATE INDEX idx_fixture ON t_fixture (id);
"@ 1
} finally {
  if (Test-Path -LiteralPath $tempRoot) {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force
  }
}

Write-Host "Migration safety policy fixtures passed."
