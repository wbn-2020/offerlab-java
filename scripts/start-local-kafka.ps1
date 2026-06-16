param(
  [string]$Root = "C:\codeware",
  [string]$ConfigPath = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
  $ConfigPath = Join-Path $repoRoot "scripts\kafka\offerlab-server-local.properties"
}

$kafkaHome = Join-Path $Root "kafka_2.13-3.6.2"
$startBat = Join-Path $kafkaHome "bin\windows\kafka-server-start.bat"

if (-not (Test-Path -LiteralPath $startBat)) {
  Write-Error "Kafka launcher not found: $startBat"
}
if (-not (Test-Path -LiteralPath $ConfigPath)) {
  Write-Error "Kafka OfferLab local config not found: $ConfigPath"
}

function Test-TcpEndpoint {
  param([string]$HostName, [int]$Port)
  $client = [System.Net.Sockets.TcpClient]::new()
  try {
    $connect = $client.BeginConnect($HostName, $Port, $null, $null)
    if (-not $connect.AsyncWaitHandle.WaitOne(1000)) {
      return $false
    }
    $client.EndConnect($connect)
    return $true
  } catch {
    return $false
  } finally {
    $client.Dispose()
  }
}

function Get-PropertyValue {
  param([string]$Name)
  foreach ($line in Get-Content -LiteralPath $ConfigPath) {
    if ($line -match "^\s*$([Regex]::Escape($Name))\s*=\s*(.+?)\s*$") {
      return $Matches[1].Trim()
    }
  }
  return $null
}

function Convert-ConfigPath {
  param([string]$Value)
  if ([string]::IsNullOrWhiteSpace($Value)) {
    return $null
  }
  return $Value.Replace("/", "\")
}

if ((Test-TcpEndpoint "127.0.0.1" 9092) -or (Test-TcpEndpoint "127.0.0.1" 9093)) {
  Write-Output "Kafka port 9092 or 9093 is already reachable; not starting another local broker."
  exit 0
}

$logDirs = Convert-ConfigPath (Get-PropertyValue "log.dirs")
$metadataDir = Convert-ConfigPath (Get-PropertyValue "metadata.log.dir")
$storageDirs = @($logDirs, $metadataDir) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

foreach ($dir in $storageDirs) {
  if (-not (Test-Path -LiteralPath $dir)) {
    Write-Error "Kafka storage directory is missing: $dir. Review scripts\kafka\offerlab-server-local.properties and format a clean local directory manually if needed."
  }
  $metaProperties = Join-Path $dir "meta.properties"
  if (-not (Test-Path -LiteralPath $metaProperties)) {
    Write-Error "Kafka storage directory is not formatted: $dir. Do not auto-format existing data. If this is a clean local test directory, review and run kafka-storage.bat format manually."
  }
}

foreach ($dir in $storageDirs) {
  $lockedDeletedCheckpoints = @(Get-ChildItem -LiteralPath $dir -Filter "*.checkpoint.deleted" -File -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.IsReadOnly })
  if ($lockedDeletedCheckpoints.Count -gt 0) {
    $sample = ($lockedDeletedCheckpoints | Select-Object -First 3 | ForEach-Object { $_.FullName }) -join "; "
    Write-Error "Kafka storage contains read-only *.checkpoint.deleted files that can break startup. Stop Kafka, review these exact paths, then choose a targeted attribute fix or clean local data directory after explicit confirmation. Sample: $sample"
  }
}

Write-Output "Starting Kafka in foreground with OfferLab local config: $ConfigPath"
Set-Location -LiteralPath $kafkaHome
& $startBat $ConfigPath
