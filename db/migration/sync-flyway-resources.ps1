param(
  [switch] $Write
)

$ErrorActionPreference = "Stop"

$sourceDir = [System.IO.Path]::GetFullPath($PSScriptRoot)
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $sourceDir "..\.."))
$resourceRoot = [System.IO.Path]::GetFullPath(
  (Join-Path $repoRoot "community-bootstrap\src\main\resources\db\flyway")
)
$manifestPath = Join-Path $sourceDir "flyway-manifest.json"

if (-not $resourceRoot.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
  throw "Resolved Flyway resource directory escapes the repository: $resourceRoot"
}

$sourceFiles = Get-ChildItem -LiteralPath $sourceDir -Filter "20*.sql" -File |
  Sort-Object Name
if ($sourceFiles.Count -ne 53) {
  throw "Expected 53 canonical migration files, found $($sourceFiles.Count)."
}

$dateSequence = @{}
$migrations = foreach ($sourceFile in $sourceFiles) {
  if ($sourceFile.BaseName -notmatch "^(?<date>\d{8})_(?<description>[a-z0-9_]+)$") {
    throw "Migration name is not canonical: $($sourceFile.Name)"
  }

  $date = $Matches.date
  $description = $Matches.description
  $dateSequence[$date] = 1 + ($dateSequence[$date] | ForEach-Object { $_ } | Select-Object -First 1)
  $sequence = $dateSequence[$date]
  $version = "$date.$($sequence.ToString('00'))"
  $stream = if ($description.StartsWith("demo_")) { "demo" } else { "core" }
  $flywayName = "V${version}__${description}.sql"
  $resourceRelative = "community-bootstrap/src/main/resources/db/flyway/$stream/$flywayName"
  $resourcePath = Join-Path $repoRoot ($resourceRelative.Replace("/", "\"))
  $sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $sourceFile.FullName).Hash.ToLowerInvariant()

  if ($Write) {
    $targetDir = Split-Path -Parent $resourcePath
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    Copy-Item -LiteralPath $sourceFile.FullName -Destination $resourcePath -Force
  } else {
    if (-not (Test-Path -LiteralPath $resourcePath -PathType Leaf)) {
      throw "Flyway resource is missing: $resourceRelative"
    }
    $resourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $resourcePath).Hash.ToLowerInvariant()
    if ($resourceHash -ne $sourceHash) {
      throw "Flyway resource drift detected: $resourceRelative"
    }
  }

  [ordered]@{
    version = $version
    description = $description
    stream = $stream
    source = "db/migration/$($sourceFile.Name)"
    resource = $resourceRelative
    sha256 = $sourceHash
  }
}

$expectedResources = @($migrations | ForEach-Object { $_.resource })
if ($Write -and (Test-Path -LiteralPath $resourceRoot)) {
  Get-ChildItem -LiteralPath $resourceRoot -Filter "*.sql" -File -Recurse | ForEach-Object {
    $resolved = [System.IO.Path]::GetFullPath($_.FullName)
    if (-not $resolved.StartsWith($resourceRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
      throw "Refusing to remove resource outside Flyway root: $resolved"
    }
    $relative = $resolved.Substring($repoRoot.Length + 1).Replace("\", "/")
    if ($relative -notin $expectedResources) {
      Remove-Item -LiteralPath $resolved -Force
    }
  }
}

$manifest = [ordered]@{
  formatVersion = 1
  baselineVersion = "0"
  schemaHistoryTable = "flyway_schema_history"
  generatedFrom = "db/migration/20*.sql"
  streams = [ordered]@{
    core = [ordered]@{
      location = "classpath:db/flyway/core"
      autoMigrate = $true
      expectedMigrations = @($migrations | Where-Object stream -eq "core").Count
    }
    demo = [ordered]@{
      location = "classpath:db/flyway/demo"
      autoMigrate = $false
      expectedMigrations = @($migrations | Where-Object stream -eq "demo").Count
      historyTable = "flyway_demo_schema_history"
    }
  }
  migrations = @($migrations)
}

$manifestJson = $manifest | ConvertTo-Json -Depth 8
if ($Write) {
  [System.IO.File]::WriteAllText(
    $manifestPath,
    $manifestJson + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
  )
  Write-Host "Synchronized $($migrations.Count) Flyway resource(s) and wrote $manifestPath."
} else {
  if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw "Flyway manifest is missing: $manifestPath"
  }
  $existingManifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
  if (($existingManifest.migrations | ConvertTo-Json -Depth 8) -ne
      ($manifest.migrations | ConvertTo-Json -Depth 8)) {
    throw "Flyway manifest drift detected. Run this script with -Write."
  }
  Write-Host "Flyway resource synchronization check passed for $($migrations.Count) migration(s)."
}
