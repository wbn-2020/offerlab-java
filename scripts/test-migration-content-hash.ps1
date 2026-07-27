$ErrorActionPreference = "Stop"

$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
. (Join-Path $root "db\migration\migration-content-hash.ps1")

$systemTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$tempRoot = [System.IO.Path]::GetFullPath((Join-Path $systemTemp (
  "offerlab-migration-hash-" + [Guid]::NewGuid().ToString("N")
)))
if (-not $tempRoot.StartsWith($systemTemp, [StringComparison]::OrdinalIgnoreCase)) {
  throw "Refusing to use a hash fixture directory outside the system temp directory: $tempRoot"
}

$utf8 = [System.Text.UTF8Encoding]::new($false)
$utf8Bom = [System.Text.UTF8Encoding]::new($true)
$content = "-- portable migration hash`nCREATE TABLE t_hash_fixture (id BIGINT);`n"

try {
  New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
  $lfPath = Join-Path $tempRoot "lf.sql"
  $crlfPath = Join-Path $tempRoot "crlf.sql"
  $bomPath = Join-Path $tempRoot "bom.sql"

  [System.IO.File]::WriteAllBytes($lfPath, $utf8.GetBytes($content))
  [System.IO.File]::WriteAllBytes(
    $crlfPath,
    $utf8.GetBytes($content.Replace("`n", "`r`n"))
  )
  [System.IO.File]::WriteAllBytes($bomPath, $utf8Bom.GetBytes($content))

  $hashes = @(
    Get-MigrationContentSha256 -Path $lfPath
    Get-MigrationContentSha256 -Path $crlfPath
    Get-MigrationContentSha256 -Path $bomPath
  )
  if (($hashes | Sort-Object -Unique).Count -ne 1) {
    throw "Migration content hash must be stable across LF, CRLF, and UTF-8 BOM variants"
  }
} finally {
  if (Test-Path -LiteralPath $tempRoot) {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force
  }
}

Write-Host "Portable migration content hash fixtures passed."
