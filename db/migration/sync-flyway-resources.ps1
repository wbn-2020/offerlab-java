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
$initSeedPath = Join-Path $repoRoot "db\init\99_seed.sql"
$demoSeedSourcePath = Join-Path $sourceDir "20260712_demo_community_seed.sql"
$generatedSeedStart = "-- BEGIN GENERATED FROM db/migration/20260712_demo_community_seed.sql"
$generatedSeedEnd = "-- END GENERATED FROM db/migration/20260712_demo_community_seed.sql"

if (-not $resourceRoot.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
  throw "Resolved Flyway resource directory escapes the repository: $resourceRoot"
}

function ConvertTo-NormalizedNewlines {
  param([string] $Text)

  return (($Text -replace "`r`n", "`n") -replace "`r", "`n")
}

function Get-FlywayChecksum {
  param([string] $Path)

  if (-not ("OfferLabMigrationTools.FlywayChecksumCalculator" -as [type])) {
    Add-Type -TypeDefinition @"
using System;
using System.IO;
using System.Text;

namespace OfferLabMigrationTools
{
    public static class FlywayChecksumCalculator
    {
        public static int Calculate(string path)
        {
            uint crc = 0xFFFFFFFFu;
            using (var reader = new StreamReader(path, new UTF8Encoding(false), true))
            {
                string line;
                while ((line = reader.ReadLine()) != null)
                {
                    if (line.Length > 0 && line[0] == '\uFEFF')
                    {
                        line = line.Substring(1);
                    }
                    foreach (byte value in Encoding.UTF8.GetBytes(line))
                    {
                        crc ^= value;
                        for (int bit = 0; bit < 8; bit++)
                        {
                            crc = (crc & 1u) != 0
                                ? (crc >> 1) ^ 0xEDB88320u
                                : crc >> 1;
                        }
                    }
                }
            }
            return unchecked((int)(crc ^ 0xFFFFFFFFu));
        }
    }
}
"@
  }

  return [OfferLabMigrationTools.FlywayChecksumCalculator]::Calculate($Path)
}

$sourceFiles = Get-ChildItem -LiteralPath $sourceDir -Filter "20*.sql" -File |
  Sort-Object Name
if ($sourceFiles.Count -lt 56) {
  throw "Expected at least 56 canonical migration files, found $($sourceFiles.Count)."
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
  $flywayChecksum = Get-FlywayChecksum -Path $sourceFile.FullName

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
    flywayChecksum = $flywayChecksum
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
  formatVersion = 2
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

$demoSeedText = ConvertTo-NormalizedNewlines(
  [System.IO.File]::ReadAllText($demoSeedSourcePath, [System.Text.Encoding]::UTF8)
)
$demoSeedBody = $demoSeedText.TrimEnd([char[]]"`n")
$generatedSeedBlock = "$generatedSeedStart`n$demoSeedBody`n$generatedSeedEnd"

if (-not (Test-Path -LiteralPath $initSeedPath -PathType Leaf)) {
  throw "Database init seed is missing: $initSeedPath"
}

$initSeedText = ConvertTo-NormalizedNewlines(
  [System.IO.File]::ReadAllText($initSeedPath, [System.Text.Encoding]::UTF8)
)
$generatedSeedPattern = "(?s)" +
  [regex]::Escape($generatedSeedStart) +
  ".*?" +
  [regex]::Escape($generatedSeedEnd)

if ($Write) {
  if ([regex]::IsMatch($initSeedText, $generatedSeedPattern)) {
    $initSeedText = [regex]::Replace(
      $initSeedText,
      $generatedSeedPattern,
      [System.Text.RegularExpressions.MatchEvaluator] { param($match) $generatedSeedBlock },
      1
    )
  } elseif ($initSeedText.Contains("-- Comprehensive community cold-start pack.")) {
    $initSeedText = [regex]::Replace(
      $initSeedText,
      "(?s)-- Comprehensive community cold-start pack\..*\z",
      [System.Text.RegularExpressions.MatchEvaluator] { param($match) $generatedSeedBlock },
      1
    )
  } else {
    throw "Generated community seed markers are missing from $initSeedPath"
  }

  [System.IO.File]::WriteAllText(
    $initSeedPath,
    $initSeedText.TrimEnd([char[]]"`n") + "`n",
    [System.Text.UTF8Encoding]::new($false)
  )
} else {
  $generatedSeedMatch = [regex]::Match(
    $initSeedText,
    "(?s)" +
      [regex]::Escape($generatedSeedStart) +
      "\n(?<body>.*?)\n" +
      [regex]::Escape($generatedSeedEnd)
  )
  if (-not $generatedSeedMatch.Success) {
    throw "Generated community seed markers are missing from $initSeedPath"
  }
  if ($generatedSeedMatch.Groups["body"].Value -ne $demoSeedBody) {
    throw "Database init seed drift detected. Run this script with -Write."
  }
}

$manifestJson = $manifest | ConvertTo-Json -Depth 8
if ($Write) {
  [System.IO.File]::WriteAllText(
    $manifestPath,
    $manifestJson + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
  )
  Write-Host "Synchronized $($migrations.Count) Flyway resource(s), $manifestPath, and $initSeedPath."
} else {
  if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw "Flyway manifest is missing: $manifestPath"
  }
  $existingManifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
  if (($existingManifest | ConvertTo-Json -Depth 8) -ne $manifestJson) {
    throw "Flyway manifest drift detected. Run this script with -Write."
  }
  Write-Host "Flyway resource synchronization check passed for $($migrations.Count) migration(s)."
}
