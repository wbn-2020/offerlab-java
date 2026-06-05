param(
  [string]$Root = "C:\codeware",
  [string]$ElasticsearchUrl = "http://127.0.0.1:9200",
  [string]$KafkaBootstrap = "localhost:9092",
  [string]$KafkaTopic = "post.published",
  [string]$KafkaConsumerGroup = "offerlab-feed-fanout",
  [string[]]$ElasticsearchIndexes = @("post_idx", "question_idx"),
  [switch]$SkipNetworkProbe,
  [switch]$WarnOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = $Root
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
$checks = New-Object System.Collections.Generic.List[string]
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

$kafkaConfig = Join-Path $kafka "config\kraft\offerlab-server.properties"
if (Test-Path -LiteralPath $kafkaConfig) {
  $content = Get-Content -LiteralPath $kafkaConfig -Raw
  if ($content -notmatch "process\.roles\s*=\s*broker,controller") {
    $warnings.Add("Kafka KRaft config does not declare broker,controller roles: $kafkaConfig")
  }
  if ($content -notmatch "listeners\s*=" -or $content -notmatch "advertised\.listeners\s*=") {
    $warnings.Add("Kafka KRaft config does not explicitly declare listeners and advertised.listeners.")
  }
  if ($content -notmatch "C:/codeware/kafka-data" -and $content -notmatch "C:\\codeware\\kafka-data") {
    $warnings.Add("Kafka KRaft config does not reference the expected C:\codeware\kafka-data path.")
  }
} else {
  $warnings.Add("Kafka KRaft config not found: $kafkaConfig")
}

function Test-TcpEndpoint {
  param([string]$HostName, [int]$Port)
  $client = [System.Net.Sockets.TcpClient]::new()
  try {
    $connect = $client.BeginConnect($HostName, $Port, $null, $null)
    if (-not $connect.AsyncWaitHandle.WaitOne(1200)) {
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

function Add-TcpProbe {
  param([string]$Name, [string]$HostName, [int]$Port)
  if (Test-TcpEndpoint -HostName $HostName -Port $Port) {
    $checks.Add("$Name tcp $HostName`:$Port reachable")
    return $true
  } else {
    $warnings.Add("$Name tcp $HostName`:$Port is not reachable.")
    return $false
  }
}

function Invoke-ReadOnlyWeb {
  param([string]$Name, [string]$Uri, [string]$Method = "GET")
  try {
    $response = Invoke-WebRequest -Uri $Uri -Method $Method -UseBasicParsing -TimeoutSec 2
    $checks.Add("$Name $Method $Uri -> HTTP $($response.StatusCode)")
    return $response
  } catch {
    $warnings.Add("$Name $Method $Uri failed: $($_.Exception.Message)")
    return $null
  }
}

function Get-HostPort {
  param([string]$Endpoint, [int]$DefaultPort)
  $value = $Endpoint -replace "^https?://", ""
  $value = $value.Split("/")[0]
  $parts = $value.Split(":")
  $portValue = 0
  if ($parts.Count -ge 2 -and [int]::TryParse($parts[-1], [ref]$portValue)) {
    return @{ Host = ($parts[0..($parts.Count - 2)] -join ":"); Port = $portValue }
  }
  return @{ Host = $value; Port = $DefaultPort }
}

if (-not $SkipNetworkProbe) {
  Add-TcpProbe "Redis" "127.0.0.1" 6379 | Out-Null
  $esEndpoint = Get-HostPort -Endpoint $ElasticsearchUrl -DefaultPort 9200
  Add-TcpProbe "Elasticsearch" $esEndpoint.Host $esEndpoint.Port | Out-Null
  $kafkaEndpoint = Get-HostPort -Endpoint $KafkaBootstrap -DefaultPort 9092
  $kafkaReachable = Add-TcpProbe "Kafka" $kafkaEndpoint.Host $kafkaEndpoint.Port

  Invoke-ReadOnlyWeb "Elasticsearch cluster" "$ElasticsearchUrl/_cluster/health"
  foreach ($index in $ElasticsearchIndexes) {
    Invoke-ReadOnlyWeb "Elasticsearch index $index" "$ElasticsearchUrl/$index" "HEAD" | Out-Null
  }

  $topicsTool = Join-Path $kafka "bin\windows\kafka-topics.bat"
  if (-not $kafkaReachable) {
    $warnings.Add("Skipping Kafka CLI topic probe because $KafkaBootstrap is not reachable.")
  } elseif (Test-Path -LiteralPath $topicsTool) {
    try {
      $topics = @(& $topicsTool --bootstrap-server $KafkaBootstrap --list 2>$null)
      if ($topics -contains $KafkaTopic) {
        $checks.Add("Kafka topic $KafkaTopic exists")
      } else {
        $warnings.Add("Kafka topic $KafkaTopic was not found.")
      }
    } catch {
      $warnings.Add("Kafka topic probe failed: $($_.Exception.Message)")
    }
  } else {
    $warnings.Add("Kafka topics tool not found: $topicsTool")
  }

  $groupsTool = Join-Path $kafka "bin\windows\kafka-consumer-groups.bat"
  if (-not $kafkaReachable) {
    $warnings.Add("Skipping Kafka CLI consumer group probe because $KafkaBootstrap is not reachable.")
  } elseif (Test-Path -LiteralPath $groupsTool) {
    try {
      $groupLines = @(& $groupsTool --bootstrap-server $KafkaBootstrap --describe --group $KafkaConsumerGroup 2>$null)
      $dataLine = $groupLines | Where-Object { $_ -match [Regex]::Escape($KafkaTopic) } | Select-Object -First 1
      if ($dataLine) {
        $checks.Add("Kafka consumer group $KafkaConsumerGroup has offsets for $KafkaTopic")
      } else {
        $warnings.Add("Kafka consumer group $KafkaConsumerGroup has no row for $KafkaTopic.")
      }
    } catch {
      $warnings.Add("Kafka consumer group probe failed: $($_.Exception.Message)")
    }
  } else {
    $warnings.Add("Kafka consumer groups tool not found: $groupsTool")
  }
}

if ($missing.Count -gt 0) {
  $message = "Missing local middleware path(s):`n- " + ($missing -join "`n- ")
  if ($WarnOnly) {
    Write-Warning $message
  } else {
    Write-Error $message
  }
}

if ($warnings.Count -gt 0) {
  Write-Warning ("Local middleware warning(s):`n- " + ($warnings -join "`n- "))
}

if ($checks.Count -gt 0) {
  Write-Output ("Read-only middleware probe(s):`n- " + ($checks -join "`n- "))
}

Write-Output "Local middleware path check completed for $root"
