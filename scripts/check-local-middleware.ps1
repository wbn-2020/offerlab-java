param(
  [string]$Root = "C:\codeware",
  [string]$ElasticsearchUrl = "http://127.0.0.1:9200",
  [string]$KafkaBootstrap = "localhost:9092",
  [string]$KafkaTopic = "post.published",
  [string]$KafkaConsumerGroup = "offerlab-feed-fanout",
  [string]$BackendUrl = "http://127.0.0.1:8080",
  [string[]]$ElasticsearchIndexes = @("post_idx", "question_idx"),
  [switch]$SkipNetworkProbe,
  [switch]$RequireBackendReadiness,
  [switch]$RequireCoreApiProbes,
  [switch]$WarnOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$Utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $Utf8NoBom
$OutputEncoding = $Utf8NoBom
$Utf8NoBomStrict = [System.Text.UTF8Encoding]::new($false, $true)

$root = $Root
$repoRoot = [string](Resolve-Path (Join-Path $PSScriptRoot ".."))
$redis = Join-Path $root "redis"
$es = Join-Path (Join-Path $root "elasticsearch") "elasticsearch-8.14.3"
$kafka = Join-Path (Join-Path $root "kafka") "kafka_2.13-3.6.2"
$kafkaData = Join-Path $root "kafka-data"
$kafkaLogs = Join-Path $root "kafka-logs"
$kafkaMetadataDir = Join-Path $kafkaData "offerlab-metadata"

$expectedPaths = @($root, $redis, $es, $kafka, $kafkaData, $kafkaLogs)
$missing = New-Object System.Collections.Generic.List[string]
foreach ($path in $expectedPaths) {
  if (-not (Test-Path -LiteralPath $path)) {
    $missing.Add($path)
  }
}

$warnings = New-Object System.Collections.Generic.List[string]
$strictFailures = New-Object System.Collections.Generic.List[string]
$checks = New-Object System.Collections.Generic.List[string]
$esConfig = Join-Path $es "config\elasticsearch.yml"
if (Test-Path -LiteralPath $esConfig) {
  $content = Get-Content -LiteralPath $esConfig -Raw
  if ($content -match "C:[\\/](?:(?:my-claude)[\\/])?(?:comware|commonware)") {
    $warnings.Add("Elasticsearch config still references an old middleware directory instead of C:\codeware.")
  }
  if ($content -notmatch "path\.data" -or $content -notmatch "path\.logs") {
    $warnings.Add("Elasticsearch config does not explicitly declare path.data and path.logs.")
  }
  if ($content -notmatch "C:/codeware/elasticsearch/elasticsearch-8.14.3/data" -and $content -notmatch "C:\\codeware\\elasticsearch\\elasticsearch-8.14.3\\data") {
    $warnings.Add("Elasticsearch path.data does not reference the expected C:\codeware\elasticsearch\elasticsearch-8.14.3\data path.")
  }
  if ($content -notmatch "C:/codeware/elasticsearch/elasticsearch-8.14.3/logs" -and $content -notmatch "C:\\codeware\\elasticsearch\\elasticsearch-8.14.3\\logs") {
    $warnings.Add("Elasticsearch path.logs does not reference the expected C:\codeware\elasticsearch\elasticsearch-8.14.3\logs path.")
  }
} else {
  $warnings.Add("Elasticsearch config not found: $esConfig")
}

$offerlabKafkaConfig = Join-Path $repoRoot "scripts\kafka\offerlab-server-local.properties"
$kafkaConfig = $offerlabKafkaConfig
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

if (Test-Path -LiteralPath $offerlabKafkaConfig) {
  $content = Get-Content -LiteralPath $offerlabKafkaConfig -Raw
  if ($content -notmatch "log\.dirs\s*=\s*C:/codeware/kafka-data/offerlab-kraft-combined-logs") {
    $warnings.Add("OfferLab Kafka local profile must use the independent offerlab-kraft-combined-logs data directory.")
  }
  if ($content -notmatch "metadata\.log\.dir\s*=\s*C:/codeware/kafka-data/offerlab-metadata") {
    $warnings.Add("OfferLab Kafka local profile must use the independent offerlab-metadata directory.")
  }
  if ($content -notmatch "log\.retention\.ms\s*=\s*-1" -or $content -notmatch "log\.retention\.bytes\s*=\s*-1") {
    $warnings.Add("OfferLab Kafka local profile must disable time/size retention for Windows demo stability.")
  }
  $checks.Add("OfferLab Kafka local profile is present: $offerlabKafkaConfig")
} else {
  $warnings.Add("OfferLab Kafka local profile not found: $offerlabKafkaConfig")
}

foreach ($scriptName in @("start-local-redis.ps1", "start-local-elasticsearch.ps1", "start-local-kafka.ps1")) {
  $scriptPath = Join-Path $repoRoot "scripts\$scriptName"
  if (Test-Path -LiteralPath $scriptPath) {
    $checks.Add("OfferLab local startup script is present: $scriptPath")
  } else {
    $warnings.Add("OfferLab local startup script is missing: $scriptPath")
  }
}

$kafkaMetadataMetaProperties = Join-Path $kafkaMetadataDir "meta.properties"
if (Test-Path -LiteralPath $kafkaMetadataMetaProperties) {
  $readOnlyDeletedCheckpoints = @(Get-ChildItem -LiteralPath $kafkaMetadataDir -Filter "*.checkpoint.deleted" -File -ErrorAction SilentlyContinue | Where-Object { $_.IsReadOnly })
  if ($readOnlyDeletedCheckpoints.Count -gt 0) {
    $sample = ($readOnlyDeletedCheckpoints | Select-Object -First 3 | ForEach-Object { $_.FullName }) -join "; "
    $message = "Kafka metadata directory contains $($readOnlyDeletedCheckpoints.Count) read-only *.checkpoint.deleted file(s). This can reproduce Kafka AccessDeniedException during startup. Review attributes or use a clean test data directory after explicit confirmation. Sample: $sample"
    $warnings.Add($message)
    if ($RequireBackendReadiness) {
      $strictFailures.Add($message)
    }
  } else {
    $checks.Add("Kafka metadata storage is formatted and checkpoint files are writable or no *.checkpoint.deleted files were found")
  }
} else {
  $warnings.Add("Kafka metadata storage is not formatted: $kafkaMetadataMetaProperties")
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

function ConvertFrom-JsonResponse {
  param([object]$Response)
  if ($null -eq $Response) {
    return $null
  }
  if ($Response.RawContentStream) {
    if ($Response.RawContentStream.CanSeek) {
      $Response.RawContentStream.Position = 0
    }
    $reader = [System.IO.StreamReader]::new($Response.RawContentStream, $script:Utf8NoBomStrict, $false, 1024, $true)
    try {
      $json = $reader.ReadToEnd()
    } finally {
      $reader.Dispose()
    }
  } else {
    $json = [string]$Response.Content
  }
  if ([string]::IsNullOrWhiteSpace($json)) {
    return $null
  }
  $json | ConvertFrom-Json
}

function Get-JsonProperty {
  param([object]$Object, [string]$Name)
  if ($null -eq $Object) {
    return $null
  }
  $property = $Object.PSObject.Properties[$Name]
  if ($null -eq $property) {
    return $null
  }
  return $property.Value
}

function Add-ReadinessModeCheck {
  param([object]$Components, [string]$Name)
  $component = Get-JsonProperty -Object $Components -Name $Name
  if ($null -eq $component) {
    $warnings.Add("Backend readiness component '$Name' is missing.")
    return
  }
  $status = Get-JsonProperty -Object $component -Name "status"
  $enabled = Get-JsonProperty -Object $component -Name "enabled"
  $reachable = Get-JsonProperty -Object $component -Name "reachable"
  $checks.Add("Backend readiness $Name status=$status enabled=$enabled reachable=$reachable")
  if ($enabled -eq $false) {
    $warnings.Add("Backend readiness $Name is disabled by configuration.")
  } elseif ($reachable -eq $false) {
    $warnings.Add("Backend readiness $Name is not reachable.")
  }
}

function Invoke-BackendReadinessProbe {
  if (-not $BackendUrl) {
    return
  }
  $readinessUri = "$($BackendUrl.TrimEnd('/'))/api/v1/health/readiness"
  $readinessResponse = Invoke-ReadOnlyWeb "Backend readiness" $readinessUri
  if ($null -eq $readinessResponse) {
    if ($RequireBackendReadiness) {
      $strictFailures.Add("Backend readiness endpoint is required but not reachable: $readinessUri")
    }
    return
  }
  try {
    $readiness = ConvertFrom-JsonResponse -Response $readinessResponse
    $overallStatus = Get-JsonProperty -Object $readiness -Name "status"
    $checks.Add("Backend readiness overall status=$overallStatus")
    if ($RequireBackendReadiness -and $overallStatus -ne "UP") {
      $strictFailures.Add("Backend readiness overall status is not UP: $overallStatus")
    }
    $components = Get-JsonProperty -Object $readiness -Name "components"
    $schema = Get-JsonProperty -Object $components -Name "schema"
    if ($null -eq $schema) {
      $message = "Backend readiness response does not expose the schema component. The running backend is likely an old build."
      if ($RequireBackendReadiness) {
        $strictFailures.Add($message)
      } else {
        $warnings.Add($message)
      }
    } else {
      $schemaStatus = Get-JsonProperty -Object $schema -Name "status"
      $schemaReady = Get-JsonProperty -Object $schema -Name "ready"
      $checks.Add("Backend readiness schema status=$schemaStatus ready=$schemaReady")
      if ($RequireBackendReadiness -and ($schemaReady -eq $false -or ($schemaStatus -and $schemaStatus -ne "UP"))) {
        $strictFailures.Add("Backend schema readiness is not UP/ready: status=$schemaStatus ready=$schemaReady")
      }
    }
    Add-ReadinessModeCheck -Components $components -Name "kafka"
    Add-ReadinessModeCheck -Components $components -Name "elasticsearch"
    if ($RequireBackendReadiness) {
      $strictReadinessUri = "$($BackendUrl.TrimEnd('/'))/api/v1/health/readiness/strict"
      $strictReadinessResponse = Invoke-ReadOnlyWeb "Backend strict readiness" $strictReadinessUri
      if ($null -eq $strictReadinessResponse) {
        $strictFailures.Add("Backend strict readiness endpoint is required but not reachable: $strictReadinessUri")
      } elseif ([int]$strictReadinessResponse.StatusCode -ne 200) {
        $strictFailures.Add("Backend strict readiness did not return HTTP 200: $($strictReadinessResponse.StatusCode)")
      }
    }
  } catch {
    $message = "Backend readiness JSON parse failed: $($_.Exception.Message)"
    if ($RequireBackendReadiness) {
      $strictFailures.Add($message)
    } else {
      $warnings.Add($message)
    }
  }
}

function Add-CoreApiFailure {
  param([string]$Message)
  if ($RequireCoreApiProbes) {
    $strictFailures.Add($Message)
  } else {
    $warnings.Add($Message)
  }
}

function Invoke-CoreApiProbe {
  param([string]$Name, [string]$Path)
  if (-not $BackendUrl) {
    return
  }
  $uri = "$($BackendUrl.TrimEnd('/'))$Path"
  try {
    $response = Invoke-WebRequest -Uri $uri -Method GET -UseBasicParsing -TimeoutSec 3
    $checks.Add("Core API $Name GET $Path -> HTTP $($response.StatusCode)")
    if ([int]$response.StatusCode -ne 200) {
      Add-CoreApiFailure "Core API $Name did not return HTTP 200: $($response.StatusCode) $Path"
      return
    }
    try {
      $payload = ConvertFrom-JsonResponse -Response $response
      $code = Get-JsonProperty -Object $payload -Name "code"
      $success = Get-JsonProperty -Object $payload -Name "success"
      if ($null -ne $code -and [int]$code -ne 0) {
        Add-CoreApiFailure "Core API $Name returned non-zero business code: $code $Path"
      }
      if ($null -ne $success -and $success -eq $false) {
        Add-CoreApiFailure "Core API $Name returned success=false: $Path"
      }
    } catch {
      Add-CoreApiFailure "Core API $Name JSON parse failed: $($_.Exception.Message)"
    }
  } catch {
    Add-CoreApiFailure "Core API $Name GET $Path failed: $($_.Exception.Message)"
  }
}

function Invoke-CoreApiProbes {
  Invoke-CoreApiProbe "posts" "/api/v1/posts?size=3"
  Invoke-CoreApiProbe "search posts" "/api/v1/search/posts?q=Java&size=3"
  Invoke-CoreApiProbe "tags" "/api/v1/tags"
  Invoke-CoreApiProbe "topics" "/api/v1/topics?featured=true&limit=6"
  Invoke-CoreApiProbe "search status" "/api/v1/search/status"
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

if (-not $SkipNetworkProbe -or $RequireBackendReadiness) {
  Invoke-BackendReadinessProbe
}
if ($RequireCoreApiProbes) {
  Invoke-CoreApiProbes
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

if ($strictFailures.Count -gt 0) {
  $message = "Strict local middleware failure(s):`n- " + ($strictFailures -join "`n- ")
  if ($WarnOnly) {
    Write-Warning $message
  } else {
    Write-Error $message
  }
}

if ($checks.Count -gt 0) {
  Write-Output ("Read-only middleware probe(s):`n- " + ($checks -join "`n- "))
}

Write-Output "Local middleware path check completed for $root"
