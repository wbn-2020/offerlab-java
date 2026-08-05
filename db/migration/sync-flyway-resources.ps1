param(
  [switch] $Write,
  [switch] $AllowTrackedMigrationRewrite
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "migration-content-hash.ps1")

function Replace-LiteralOnce {
  param(
    [Parameter(Mandatory = $true)][string] $Content,
    [Parameter(Mandatory = $true)][string] $Before,
    [Parameter(Mandatory = $true)][string] $After,
    [Parameter(Mandatory = $true)][string] $Label
  )

  $start = $Content.IndexOf($Before, [System.StringComparison]::Ordinal)
  if ($start -lt 0) {
    throw "Immutable demo migration is missing expected $Label."
  }
  $duplicate = $Content.IndexOf(
    $Before,
    $start + $Before.Length,
    [System.StringComparison]::Ordinal
  )
  if ($duplicate -ge 0) {
    throw "Immutable demo migration contains duplicate $Label."
  }

  return $Content.Substring(0, $start) + $After + $Content.Substring($start + $Before.Length)
}

function ConvertTo-FreshInitDemoSeed {
  param([Parameter(Mandatory = $true)][string] $Content)

  $result = (ConvertTo-NormalizedNewlines $Content).TrimEnd([char[]]"`n")
  $legacyAuthorGuard = ConvertTo-NormalizedNewlines @'
SET @community_demo_uid := COALESCE(
    (SELECT id FROM t_user_account WHERE email = 'demo.author@offerlab.local' AND is_deleted = 0 LIMIT 1),
    (SELECT id FROM t_user_account WHERE email = 'admin' AND is_deleted = 0 LIMIT 1),
    (SELECT id FROM t_user_account WHERE is_deleted = 0 ORDER BY id LIMIT 1)
);

SET @community_seed_guard_sql := IF(
    @community_demo_uid IS NULL,
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''community seed requires an existing active author''',
    'DO 0'
);
PREPARE community_seed_guard_stmt FROM @community_seed_guard_sql;
EXECUTE community_seed_guard_stmt;
DEALLOCATE PREPARE community_seed_guard_stmt;
'@
  $legacyAuthorGuard = $legacyAuthorGuard.TrimEnd([char[]]"`n")
  $freshAuthorGuard = ConvertTo-NormalizedNewlines @'
-- Keep community demo content on the same reserved identity. In particular,
-- never fall back to a real admin or the first active account when the local
-- demo identity has been explicitly promoted to demo.admin.
SET @community_demo_uid := @offerlab_demo_user_uid;

SET @community_seed_identity_conflicts := IF(
    @community_demo_uid IS NULL OR NOT EXISTS (
        SELECT 1
        FROM t_user_account
        WHERE id = @community_demo_uid
          AND is_deleted = 0
    ),
    1,
    0
);
INSERT INTO offerlab_demo_seed_assertion (assertion_name, conflict_count)
VALUES ('community_demo_author', @community_seed_identity_conflicts);
'@
  $freshAuthorGuard = $freshAuthorGuard.TrimEnd([char[]]"`n")
  $result = Replace-LiteralOnce `
    -Content $result `
    -Before $legacyAuthorGuard `
    -After $freshAuthorGuard `
    -Label "community demo author guard"

  $guardOverrides = @(
    [pscustomobject]@{ Diagnostic = "tag"; Assertion = "community_tag_identity" },
    [pscustomobject]@{ Diagnostic = "topic"; Assertion = "community_topic_identity" },
    [pscustomobject]@{ Diagnostic = "topic tag"; Assertion = "community_topic_tag_identity" },
    [pscustomobject]@{ Diagnostic = "post"; Assertion = "community_post_identity" },
    [pscustomobject]@{ Diagnostic = "post tag"; Assertion = "community_post_tag_identity" }
  )
  foreach ($override in $guardOverrides) {
    $legacyGuard = ConvertTo-NormalizedNewlines @"
SET @community_seed_guard_sql := IF(
    @community_seed_identity_conflicts > 0,
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''community seed $($override.Diagnostic) identity conflict''',
    'DO 0'
);
PREPARE community_seed_guard_stmt FROM @community_seed_guard_sql;
EXECUTE community_seed_guard_stmt;
DEALLOCATE PREPARE community_seed_guard_stmt;
"@
    $legacyGuard = $legacyGuard.TrimEnd([char[]]"`n")
    $freshGuard = ConvertTo-NormalizedNewlines @"
INSERT INTO offerlab_demo_seed_assertion (assertion_name, conflict_count)
VALUES ('$($override.Assertion)', @community_seed_identity_conflicts);
"@
    $freshGuard = $freshGuard.TrimEnd([char[]]"`n")
    $result = Replace-LiteralOnce `
      -Content $result `
      -Before $legacyGuard `
      -After $freshGuard `
      -Label "community demo $($override.Diagnostic) guard"
  }

  return "$result`nDROP TEMPORARY TABLE offerlab_demo_seed_assertion;"
}

$sourceDir = [System.IO.Path]::GetFullPath($PSScriptRoot)
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $sourceDir "..\.."))
$resourceRoot = [System.IO.Path]::GetFullPath(
  (Join-Path $repoRoot "community-bootstrap\src\main\resources\db\flyway")
)
$manifestPath = Join-Path $sourceDir "flyway-manifest.json"
$initSeedPath = Join-Path $repoRoot "db\init\99_seed.sql"
$demoSeedSourcePath = Join-Path $sourceDir "20260712_demo_community_seed.sql"
$initMirrorMappings = @(
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260714_collaboration_stage2.sql"
    Destination = Join-Path $repoRoot "db\init\14_collaboration.sql"
  },
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260714_incentive_stage3_stage5.sql"
    Destination = Join-Path $repoRoot "db\init\15_incentive.sql"
  },
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260714_database_integrity_hardening.sql"
    Destination = Join-Path $repoRoot "db\init\16_database_integrity_hardening.sql"
  }
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260715_trusted_distribution_revisit.sql"
    Destination = Join-Path $repoRoot "db\init\17_trusted_distribution_revisit.sql"
  }
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260715_content_maintenance_stage8.sql"
    Destination = Join-Path $repoRoot "db\init\18_content_maintenance_stage8.sql"
  }
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260717_collab_need_submission.sql"
    Destination = Join-Path $repoRoot "db\init\19_collab_need_submission.sql"
  }
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260718_collab_need_lifecycle.sql"
    Destination = Join-Path $repoRoot "db\init\20_collab_need_lifecycle.sql"
  },
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260719_collab_need_claim_cycle_revision.sql"
    Destination = Join-Path $repoRoot "db\init\21_collab_need_claim_cycle_revision.sql"
  },
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260719_feed_feedback_control.sql"
    Destination = Join-Path $repoRoot "db\init\22_feed_feedback_control.sql"
  },
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260719_user_subscription_preference.sql"
    Destination = Join-Path $repoRoot "db\init\23_user_subscription_preference.sql"
  },
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260719_projection_reconcile_request.sql"
    Destination = Join-Path $repoRoot "db\init\24_projection_reconcile_request.sql"
  },
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260720_search_index_rebuild_task.sql"
    Destination = Join-Path $repoRoot "db\init\25_search_index_rebuild_task.sql"
  },
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260720_knowledge_lifecycle.sql"
    Destination = Join-Path $repoRoot "db\init\26_knowledge_lifecycle.sql"
  },
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260720_knowledge_lifecycle_backfill.sql"
    Destination = Join-Path $repoRoot "db\init\27_knowledge_lifecycle_backfill.sql"
  },
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260726_event_consumer_inbox.sql"
    Destination = Join-Path $repoRoot "db\init\28_event_consumer_inbox.sql"
  },
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260726_query_path_indexes.sql"
    Destination = Join-Path $repoRoot "db\init\29_query_path_indexes.sql"
  },
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260727_operation_topic_draft_revision.sql"
    Destination = Join-Path $repoRoot "db\init\30_operation_topic_draft_revision.sql"
  },
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260730_moderation_keyword_seed.sql"
    Destination = Join-Path $repoRoot "db\init\31_moderation_keyword_seed.sql"
  },
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260730_disable_unfulfillable_benefits.sql"
    Destination = Join-Path $repoRoot "db\init\32_disable_unfulfillable_benefits.sql"
  },
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260731_subscription_update_digest.sql"
    Destination = Join-Path $repoRoot "db\init\33_subscription_update_digest.sql"
  },
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260801_entitlement_ai_assist_fulfillment.sql"
    Destination = Join-Path $repoRoot "db\init\34_entitlement_ai_assist_fulfillment.sql"
  },
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260802_ai_entitlement_recovery_operations.sql"
    Destination = Join-Path $repoRoot "db\init\35_ai_entitlement_recovery_operations.sql"
  },
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260802_creator_growth_challenges_badges.sql"
    Destination = Join-Path $repoRoot "db\init\36_creator_growth_challenges_badges.sql"
  }
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260802_user_controls_trustworthy_distribution.sql"
    Destination = Join-Path $repoRoot "db\init\37_user_controls_trustworthy_distribution.sql"
  }
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260803_creator_quality_signal_query_index.sql"
    Destination = Join-Path $repoRoot "db\init\38_creator_quality_signal_query_index.sql"
  }
  [pscustomobject]@{
    Source = Join-Path $sourceDir "20260804_creator_content_revision_boundary.sql"
    Destination = Join-Path $repoRoot "db\init\39_creator_content_revision_boundary.sql"
  }
)
$generatedSeedStart = "-- BEGIN GENERATED FROM db/migration/20260712_demo_community_seed.sql"
$generatedSeedEnd = "-- END GENERATED FROM db/migration/20260712_demo_community_seed.sql"
$existingManifest = $null
$existingVersionsBySource = @{}
$existingMigrationsBySource = @{}
$usedVersions = [System.Collections.Generic.HashSet[string]]::new(
  [System.StringComparer]::OrdinalIgnoreCase
)
$nextSequenceByDate = @{}

if (Test-Path -LiteralPath $manifestPath -PathType Leaf) {
  $existingManifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
  foreach ($migration in @($existingManifest.migrations)) {
    $source = [string] $migration.source
    $version = [string] $migration.version
    if ($source -notmatch "^db/migration/20\d{6}_[a-z0-9_]+\.sql$") {
      throw "Existing Flyway manifest contains an invalid source: $source"
    }
    if ($version -notmatch "^(?<date>\d{8})\.(?<sequence>\d{2})$") {
      throw "Existing Flyway manifest contains an invalid version: $version"
    }
    if ($existingVersionsBySource.ContainsKey($source)) {
      throw "Existing Flyway manifest contains a duplicate source: $source"
    }
    if (-not $usedVersions.Add($version)) {
      throw "Existing Flyway manifest contains a duplicate version: $version"
    }
    $existingVersionsBySource[$source] = $version
    $existingMigrationsBySource[$source] = $migration
    $date = $Matches.date
    $sequence = [int] $Matches.sequence
    $nextSequenceByDate[$date] = [Math]::Max(
      $sequence,
      [int] ($nextSequenceByDate[$date] | ForEach-Object { $_ } | Select-Object -First 1)
    )
  }
}

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
if ($sourceFiles.Count -eq 0) {
  throw "No canonical migration files were found in $sourceDir."
}

$canonicalSources = [System.Collections.Generic.HashSet[string]]::new(
  [System.StringComparer]::OrdinalIgnoreCase
)
foreach ($sourceFile in $sourceFiles) {
  [void] $canonicalSources.Add("db/migration/$($sourceFile.Name)")
}
foreach ($existingSource in $existingVersionsBySource.Keys) {
  if (-not $canonicalSources.Contains($existingSource)) {
    throw "Refusing to remove a migration already tracked by the Flyway manifest: $existingSource"
  }
}

$migrations = foreach ($sourceFile in $sourceFiles) {
  if ($sourceFile.BaseName -notmatch "^(?<date>\d{8})_(?<description>[a-z0-9_]+)$") {
    throw "Migration name is not canonical: $($sourceFile.Name)"
  }

  $date = $Matches.date
  $description = $Matches.description
  $sourceRelative = "db/migration/$($sourceFile.Name)"
  if ($existingVersionsBySource.ContainsKey($sourceRelative)) {
    $version = $existingVersionsBySource[$sourceRelative]
    if (-not $version.StartsWith("$date.", [System.StringComparison]::Ordinal)) {
      throw "Migration date does not match its preserved Flyway version: $sourceRelative -> $version"
    }
  } else {
    $sequence = 1 + [int] (
      $nextSequenceByDate[$date] | ForEach-Object { $_ } | Select-Object -First 1
    )
    do {
      $version = "$date.$($sequence.ToString('00'))"
      $sequence++
    } while ($usedVersions.Contains($version))
    $nextSequenceByDate[$date] = $sequence - 1
    [void] $usedVersions.Add($version)
  }
  $stream = if ($description.StartsWith("demo_")) { "demo" } else { "core" }
  $flywayName = "V${version}__${description}.sql"
  $resourceRelative = "community-bootstrap/src/main/resources/db/flyway/$stream/$flywayName"
  $resourcePath = Join-Path $repoRoot ($resourceRelative.Replace("/", "\"))
  $sourceHash = Get-MigrationContentSha256 -Path $sourceFile.FullName
  $flywayChecksum = Get-FlywayChecksum -Path $sourceFile.FullName
  if ($existingMigrationsBySource.ContainsKey($sourceRelative)) {
    $trackedMigration = $existingMigrationsBySource[$sourceRelative]
    $trackedHash = ([string] $trackedMigration.sha256).ToLowerInvariant()
    $trackedChecksum = [int] $trackedMigration.flywayChecksum
    $contentChanged = $trackedHash -ne $sourceHash -or $trackedChecksum -ne $flywayChecksum
    if ($contentChanged -and -not $AllowTrackedMigrationRewrite) {
      throw (
        "Tracked migration content changed: $sourceRelative. " +
        "Published migrations are immutable. If this migration is known to be " +
        "unreleased, rerun with -AllowTrackedMigrationRewrite and record the reason."
      )
    }
  }

  if ($Write) {
    $targetDir = Split-Path -Parent $resourcePath
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    Copy-Item -LiteralPath $sourceFile.FullName -Destination $resourcePath -Force
  } else {
    if (-not (Test-Path -LiteralPath $resourcePath -PathType Leaf)) {
      throw "Flyway resource is missing: $resourceRelative"
    }
    $resourceHash = Get-MigrationContentSha256 -Path $resourcePath
    if ($resourceHash -ne $sourceHash) {
      throw "Flyway resource drift detected: $resourceRelative"
    }
  }

  [ordered]@{
    version = $version
    description = $description
    stream = $stream
    source = $sourceRelative
    resource = $resourceRelative
    sha256 = $sourceHash
    flywayChecksum = $flywayChecksum
  }
}
$migrations = @($migrations | Sort-Object { [string] $_["version"] })

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

$initMirrorMappings | ForEach-Object {
  if ($Write) {
    Copy-Item -LiteralPath $_.Source -Destination $_.Destination -Force
  } else {
    if (-not (Test-Path -LiteralPath $_.Destination -PathType Leaf)) {
      throw "Database init mirror is missing: $($_.Destination)"
    }
    $sourceHash = Get-MigrationContentSha256 -Path $_.Source
    $destinationHash = Get-MigrationContentSha256 -Path $_.Destination
    if ($sourceHash -ne $destinationHash) {
      throw "Database init mirror drift detected: $($_.Destination)"
    }
  }
}

$manifest = [ordered]@{
  formatVersion = 3
  contentHashAlgorithm = "sha256-utf8-lf-no-bom-v1"
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
$demoSeedBody = ConvertTo-FreshInitDemoSeed -Content $demoSeedText
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
  if (($existingManifest | ConvertTo-Json -Depth 8) -ne $manifestJson) {
    throw "Flyway manifest drift detected. Run this script with -Write."
  }
  Write-Host "Flyway resource synchronization check passed for $($migrations.Count) migration(s)."
}
