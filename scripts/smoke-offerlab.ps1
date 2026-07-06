param(
  [string]$BaseUrl = "http://127.0.0.1:8080",
  [string]$ReportPath = "C:\codeware\offerlab-smoke-report.json",
  [string]$AdminEmail = "",
  [string]$AdminPassword = "password123",
  [string]$KafkaBootstrap = "localhost:9092",
  [string]$KafkaHome = "C:\codeware\kafka\kafka_2.13-3.6.2",
  [string]$KafkaTopic = "post.published",
  [string]$KafkaConsumerGroup = "offerlab-feed-fanout",
  [string]$ElasticsearchUrl = "http://127.0.0.1:9200",
  [string[]]$ElasticsearchIndexes = @("post_idx", "question_idx"),
  [switch]$ReadOnlyProbe,
  [switch]$NoWriteReport,
  [switch]$EncodingSelfTest
)

$ErrorActionPreference = "Stop"

$Utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $Utf8NoBom
$OutputEncoding = $Utf8NoBom
$Utf8NoBomStrict = [System.Text.UTF8Encoding]::new($false, $true)

function Invoke-Json {
  param(
    [string]$Method,
    [string]$Path,
    [object]$Body = $null,
    [string]$Token = $null
  )

  $headers = @{}
  if ($Token) {
    $headers.Authorization = "Bearer $Token"
  }

  $args = @{
    Method = $Method
    Uri = "$BaseUrl$Path"
    Headers = $headers
    UseBasicParsing = $true
  }
  if ($null -ne $Body) {
    $args.ContentType = "application/json; charset=utf-8"
    $jsonBody = ($Body | ConvertTo-Json -Depth 20 -Compress)
    $args.Body = $script:Utf8NoBomStrict.GetBytes($jsonBody)
  }
  $response = Invoke-WebRequest @args
  if ($response.RawContentStream.CanSeek) {
    $response.RawContentStream.Position = 0
  }
  $reader = [System.IO.StreamReader]::new($response.RawContentStream, $script:Utf8NoBomStrict, $false, 1024, $true)
  try {
    $json = $reader.ReadToEnd()
  } finally {
    $reader.Dispose()
  }
  if ([string]::IsNullOrWhiteSpace($json)) {
    return $null
  }
  $json | ConvertFrom-Json
}

function Test-Mojibake {
  param([string]$Value)
  if ([string]::IsNullOrEmpty($Value)) {
    return $false
  }
  return ($Value -match "[\u0080-\u009F]" -or $Value -match "[\u00C3\u00C2\u00E2\u20AC]" -or $Value -match "[\u00E5\u00E7\u00E9\u00E4][\u0080-\u00FF]")
}

function Assert-Ok {
  param([string]$Name, [object]$Response)
  if ($Response.code -ne 0) {
    throw "$Name failed: code=$($Response.code), message=$($Response.message)"
  }
  $script:steps += [ordered]@{ name = $Name; ok = $true }
}

function Assert-True {
  param([string]$Name, [bool]$Condition)
  if (-not $Condition) {
    throw "$Name failed"
  }
  $script:steps += [ordered]@{ name = $Name; ok = $true }
}

function Add-Step {
  param([string]$Name, [bool]$Ok, [string]$Error = $null)
  $row = [ordered]@{ name = $Name; ok = $Ok }
  if ($Error) {
    $row.error = $Error
  }
  $script:steps += $row
}

function Test-KafkaTool {
  param([string]$Name)
  $tool = Join-Path $KafkaHome "bin\windows\$Name"
  if (Test-Path $tool) {
    return $tool
  }
  return $null
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

function Get-KafkaLagFromLine {
  param([string]$Line)
  if ([string]::IsNullOrWhiteSpace($Line)) {
    return $null
  }
  $parts = $Line -split "\s+"
  $numbers = @($parts | Where-Object { $_ -match "^\d+$" })
  if ($numbers.Count -eq 0) {
    return $null
  }
  return [int]$numbers[-1]
}

function Invoke-EsReadOnlyProbe {
  $result = [ordered]@{
    available = $false
    healthStatus = $null
    indexes = @()
    error = $null
  }
  try {
    $health = Invoke-RestMethod -Method GET -Uri "$ElasticsearchUrl/_cluster/health" -TimeoutSec 2
    $result.available = $true
    $result.healthStatus = $health.status
  } catch {
    $result.error = $_.Exception.Message
    return $result
  }
  foreach ($index in $ElasticsearchIndexes) {
    try {
      $response = Invoke-WebRequest -Method HEAD -Uri "$ElasticsearchUrl/$index" -UseBasicParsing -TimeoutSec 2
      $result.indexes += [ordered]@{ name = $index; exists = ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) }
    } catch {
      $result.indexes += [ordered]@{ name = $index; exists = $false; error = $_.Exception.Message }
    }
  }
  return $result
}

function Write-SmokeReport {
  param([object]$Report)
  if ($Report.PSObject.Properties.Name -contains "recommendReason" -and (Test-Mojibake "$($Report.recommendReason)")) {
    throw "recommendReason looks mojibake; refusing to write polluted report: $($Report.recommendReason)"
  }
  $json = $Report | ConvertTo-Json -Depth 20
  if (-not $NoWriteReport -and $ReportPath) {
    $dir = Split-Path -Parent $ReportPath
    if ($dir -and -not (Test-Path $dir)) {
      New-Item -ItemType Directory -Path $dir | Out-Null
    }
    [System.IO.File]::WriteAllText($ReportPath, $json, $script:Utf8NoBomStrict)
  }
  $json
}

if ($EncodingSelfTest) {
  $goodReason = -join @([char]0x5339, [char]0x914D, [char]0x4F60, [char]0x7684, [char]0x76EE, [char]0x6807, [char]0x516C, [char]0x53F8, [char]0xFF1A, "NebulaTech")
  $badReason = -join @([char]0x00E5, [char]0x008C, [char]0x00B9, [char]0x00E9, [char]0x0085, [char]0x008D, "NebulaTech")
  if (Test-Mojibake $goodReason) {
    throw "encoding self-test failed: valid Chinese recommendReason was flagged"
  }
  if (-not (Test-Mojibake $badReason)) {
    throw "encoding self-test failed: mojibake recommendReason was not flagged"
  }
  $json = Write-SmokeReport ([ordered]@{
    ok = $true
    encodingSelfTest = $true
    timestamp = (Get-Date).ToString("s")
    recommendReason = $goodReason
  })
  $roundTrip = $json | ConvertFrom-Json
  if ($roundTrip.recommendReason -ne $goodReason) {
    throw "encoding self-test failed: recommendReason did not round-trip as UTF-8 JSON"
  }
  return
}

$steps = @()
$kafkaOk = $false
$kafkaTopics = @()
$kafkaLag = $null
$topicsTool = Test-KafkaTool "kafka-topics.bat"
$groupsTool = Test-KafkaTool "kafka-consumer-groups.bat"
$kafkaEndpoint = Get-HostPort -Endpoint $KafkaBootstrap -DefaultPort 9092
$kafkaReachable = Test-TcpEndpoint -HostName $kafkaEndpoint.Host -Port $kafkaEndpoint.Port
if (-not $kafkaReachable) {
  if ($ReadOnlyProbe) {
    Add-Step "kafka tcp $KafkaBootstrap reachable" $false "tcp not reachable"
  } elseif ($topicsTool -or $groupsTool) {
    Assert-True "kafka tcp $KafkaBootstrap reachable" $false
  }
}
if ($kafkaReachable -and $topicsTool) {
  try {
    $kafkaTopics = @(& $topicsTool --bootstrap-server $KafkaBootstrap --list 2>$null)
    $kafkaOk = $kafkaTopics -contains $KafkaTopic
    if ($ReadOnlyProbe) {
      if ($kafkaOk) {
        Add-Step "kafka $KafkaTopic topic available" $true
      } else {
        Add-Step "kafka $KafkaTopic topic available" $false "topic not found"
      }
    } else {
      Assert-True "kafka $KafkaTopic topic available" $kafkaOk
    }
  } catch {
    if ($ReadOnlyProbe) {
      Add-Step "kafka $KafkaTopic topic available" $false $_.Exception.Message
    } else {
      Assert-True "kafka $KafkaTopic topic available" $false
    }
  }
} elseif ($ReadOnlyProbe) {
  if ($kafkaReachable) {
    Add-Step "kafka $KafkaTopic topic available" $false "tool not found: $(Join-Path $KafkaHome "bin\windows\kafka-topics.bat")"
  }
}

if ($ReadOnlyProbe) {
  if (-not $kafkaReachable) {
    Add-Step "kafka $KafkaConsumerGroup consumer group row" $false "tcp not reachable"
  } elseif ($groupsTool) {
    try {
      $groupLines = @(& $groupsTool --bootstrap-server $KafkaBootstrap --describe --group $KafkaConsumerGroup 2>$null)
      $dataLine = $groupLines | Where-Object { $_ -match [Regex]::Escape($KafkaTopic) } | Select-Object -First 1
      if ($dataLine) {
        $kafkaLag = Get-KafkaLagFromLine $dataLine
        Add-Step "kafka $KafkaConsumerGroup consumer group row" $true
      } else {
        Add-Step "kafka $KafkaConsumerGroup consumer group row" $false "topic row not found"
      }
    } catch {
      Add-Step "kafka $KafkaConsumerGroup consumer group row" $false $_.Exception.Message
    }
  } else {
    Add-Step "kafka $KafkaConsumerGroup consumer group row" $false "tool not found: $(Join-Path $KafkaHome "bin\windows\kafka-consumer-groups.bat")"
  }
}

$esProbe = Invoke-EsReadOnlyProbe

if ($ReadOnlyProbe) {
  $readinessStatus = $null
  $readinessComponents = $null
  try {
    $readiness = Invoke-RestMethod -Method GET -Uri "$BaseUrl/api/v1/health/readiness" -TimeoutSec 3
    $readinessStatus = $readiness.status
    $readinessComponents = $readiness.components
  } catch {
    $steps += [ordered]@{ name = "readiness read-only probe"; ok = $false; error = $_.Exception.Message }
  }
  $esMissingIndexes = @($esProbe.indexes | Where-Object { -not $_.exists }).Count
  $report = [ordered]@{
    ok = ($readinessStatus -eq "UP" -and $kafkaOk -and $esProbe.available -and $esMissingIndexes -eq 0)
    readOnlyProbe = $true
    baseUrl = $BaseUrl
    timestamp = (Get-Date).ToString("s")
    readinessStatus = $readinessStatus
    readinessComponents = $readinessComponents
    kafkaOk = $kafkaOk
    kafkaTopicCount = @($kafkaTopics).Count
    kafkaLag = $kafkaLag
    elasticsearch = $esProbe
    steps = $steps
  }
  Write-SmokeReport $report
  return
}

$suffix = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
$authorEmail = "flow-author-$suffix@offerlab.local"
$actorEmail = "flow-actor-$suffix@offerlab.local"
$password = "password123"

$authorRegister = Invoke-Json "POST" "/api/v1/auth/register" @{ email = $authorEmail; password = $password; nickname = "ReviewAuthor" }
Assert-Ok "register author" $authorRegister

$actorRegister = Invoke-Json "POST" "/api/v1/auth/register" @{ email = $actorEmail; password = $password; nickname = "ReviewActor" }
Assert-Ok "register actor" $actorRegister

$authorLogin = Invoke-Json "POST" "/api/v1/auth/login" @{ email = $authorEmail; password = $password }
Assert-Ok "login author" $authorLogin
$authorToken = $authorLogin.data.token

$actorLogin = Invoke-Json "POST" "/api/v1/auth/login" @{ email = $actorEmail; password = $password }
Assert-Ok "login actor" $actorLogin
$actorToken = $actorLogin.data.token

$adminToken = $authorToken
$adminAccount = $authorEmail
if ($AdminEmail) {
  $adminLogin = Invoke-Json "POST" "/api/v1/auth/login" @{ email = $AdminEmail; password = $AdminPassword }
  Assert-Ok "login admin" $adminLogin
  $adminToken = $adminLogin.data.token
  $adminAccount = $AdminEmail
}

$follow = Invoke-Json "POST" "/api/v1/users/$($authorRegister.data.uid)/follow" $null $actorToken
Assert-Ok "actor follows author" $follow

$postContent = "Java backend project review keyword $suffix. The writeup covers Redis cache breakdown, MySQL index tuning, Kafka fanout, failure recovery, observability gaps, and reusable lessons for future architecture reviews."
Assert-True "live flow project review content satisfies community length" ($postContent.Trim().Length -ge 80)

$postBody = @{
  postType = 11
  title = "OfferLab architecture review $suffix"
  content = $postContent
  visibility = 1
  extJson = '{"contentType":"PROJECT_REVIEW","difficulty":"Practical","scenario":"architecture-review","techStacks":["Java","Spring Boot","Redis","Kafka"],"summary":"Architecture review with reusable backend lessons."}'
  tagNames = @("Java", "Architecture")
}
$publish = Invoke-Json "POST" "/api/v1/posts" $postBody $authorToken
Assert-Ok "publish post" $publish
$postId = $publish.data.postId

$detail = Invoke-Json "GET" "/api/v1/posts/$postId"
Assert-Ok "post detail" $detail
Assert-True "post detail has author" ($null -ne $detail.data.author -and $detail.data.author.uid -eq $authorRegister.data.uid)
Assert-True "post detail has counter" ($null -ne $detail.data.counter)

$comment = Invoke-Json "POST" "/api/v1/posts/$postId/comments" @{ content = "Follow up comment $suffix" } $actorToken
Assert-Ok "comment post" $comment
$commentId = $comment.data.commentId

$like = Invoke-Json "POST" "/api/v1/posts/$postId/like" $null $actorToken
Assert-Ok "like post" $like

$favorite = Invoke-Json "POST" "/api/v1/posts/$postId/favorite" $null $actorToken
Assert-Ok "favorite post" $favorite

$interactionState = Invoke-Json "GET" "/api/v1/posts/$postId/interaction" $null $actorToken
Assert-Ok "post interaction state" $interactionState
Assert-True "post interaction liked favorited" ($interactionState.data.liked -and $interactionState.data.favorited)

$commentLike = Invoke-Json "POST" "/api/v1/comments/$commentId/like" $null $authorToken
Assert-Ok "like comment" $commentLike

$reply = Invoke-Json "POST" "/api/v1/posts/$postId/comments" @{
  content = "Author reply $suffix"
  parentId = $commentId
  replyToUid = $actorRegister.data.uid
} $authorToken
Assert-Ok "reply comment" $reply
$replyId = $reply.data.commentId

$commentsWithReply = Invoke-Json "GET" "/api/v1/posts/$postId/comments?size=10" $null $authorToken
Assert-Ok "list comments with reply" $commentsWithReply
$rootComment = @($commentsWithReply.data.items) | Where-Object { "$($_.id)" -eq "$commentId" } | Select-Object -First 1
Assert-True "comment root enriched" ($null -ne $rootComment -and $null -ne $rootComment.author -and $rootComment.author.uid -eq $actorRegister.data.uid)
Assert-True "comment root liked state" ($rootComment.myLiked -eq $true -and $rootComment.canDelete -eq $true)
$replyComment = @($rootComment.replies) | Where-Object { "$($_.id)" -eq "$replyId" } | Select-Object -First 1
Assert-True "comment reply enriched" ($null -ne $replyComment -and $null -ne $replyComment.author -and $replyComment.author.uid -eq $authorRegister.data.uid)
Assert-True "comment reply target user" ($null -ne $replyComment.replyToUser -and $replyComment.replyToUser.uid -eq $actorRegister.data.uid)

$commentUnlike = Invoke-Json "DELETE" "/api/v1/comments/$commentId/like" $null $authorToken
Assert-Ok "unlike comment" $commentUnlike

$commentsAfterUnlike = Invoke-Json "GET" "/api/v1/posts/$postId/comments?size=10" $null $authorToken
Assert-Ok "list comments after unlike" $commentsAfterUnlike
$rootAfterUnlike = @($commentsAfterUnlike.data.items) | Where-Object { "$($_.id)" -eq "$commentId" } | Select-Object -First 1
Assert-True "comment unlike reflected" ($null -ne $rootAfterUnlike -and $rootAfterUnlike.myLiked -eq $false)

$deleteReply = Invoke-Json "DELETE" "/api/v1/comments/$replyId" $null $authorToken
Assert-Ok "delete reply comment" $deleteReply

$commentsAfterDelete = Invoke-Json "GET" "/api/v1/posts/$postId/comments?size=10" $null $authorToken
Assert-Ok "list comments after reply delete" $commentsAfterDelete
$rootAfterDelete = @($commentsAfterDelete.data.items) | Where-Object { "$($_.id)" -eq "$commentId" } | Select-Object -First 1
$deletedReply = @($rootAfterDelete.replies) | Where-Object { "$($_.id)" -eq "$replyId" } | Select-Object -First 1
Assert-True "deleted reply hidden" ($null -ne $rootAfterDelete -and $null -eq $deletedReply)

Start-Sleep -Seconds 2

$notifications = Invoke-Json "GET" "/api/v1/notifications/unread-count" $null $authorToken
Assert-Ok "author unread notifications" $notifications
Assert-True "author has unread notifications" ($notifications.data.total -gt 0)

$notificationList = Invoke-Json "GET" "/api/v1/notifications?size=10" $null $authorToken
Assert-Ok "author notification list" $notificationList
$firstNotification = @($notificationList.data.items) | Select-Object -First 1
Assert-True "notification list not empty" ($null -ne $firstNotification)
Assert-True "notification sender enriched" ($null -ne $firstNotification.sender -and $null -ne $firstNotification.sender.nickname)
Assert-True "notification unread flag" ($firstNotification.isRead -eq $false)

$notificationTypeList = Invoke-Json "GET" "/api/v1/notifications?type=comment&size=10" $null $authorToken
Assert-Ok "author comment notification list" $notificationTypeList
Assert-True "comment notification filter works" (@($notificationTypeList.data.items).Count -gt 0)

$markOneRead = Invoke-Json "POST" "/api/v1/notifications/read" @{ ids = @($firstNotification.id) } $authorToken
Assert-Ok "mark one notification read" $markOneRead

$notificationsAfterRead = Invoke-Json "GET" "/api/v1/notifications/unread-count" $null $authorToken
Assert-Ok "author unread notifications after one read" $notificationsAfterRead
Assert-True "unread decreases after one read" ($notificationsAfterRead.data.total -lt $notifications.data.total)

$markAllRead = Invoke-Json "POST" "/api/v1/notifications/read-all" $null $authorToken
Assert-Ok "mark all notifications read" $markAllRead

$notificationsAfterReadAll = Invoke-Json "GET" "/api/v1/notifications/unread-count" $null $authorToken
Assert-Ok "author unread notifications after read all" $notificationsAfterReadAll
Assert-True "unread zero after read all" ($notificationsAfterReadAll.data.total -eq 0)

$search = Invoke-Json "GET" "/api/v1/search/posts?q=$suffix"
Assert-Ok "search post" $search
$firstSearchItem = @($search.data.items)[0]
Assert-True "search result enriched" (@($search.data.items).Count -gt 0 -and $null -ne $firstSearchItem.author -and $null -ne $firstSearchItem.counter)

$trend = Invoke-Json "GET" "/api/v1/dashboard/trend?range=7d"
Assert-Ok "trend dashboard" $trend

$intent = Invoke-Json "PUT" "/api/v1/users/me/intent" @{
  targetCompanies = @("Java")
  targetPositions = @("architecture-review")
  yearsOfExp = 3
  expectedCity = "Shanghai"
  techStack = @("Java", "Spring Boot")
} $authorToken
Assert-Ok "update author intent" $intent

$recommendFeed = Invoke-Json "GET" "/api/v1/feeds/recommend?size=10" $null $authorToken
Assert-Ok "recommend feed" $recommendFeed
$recommendedPost = @($recommendFeed.data.items) | Where-Object { "$($_.post.id)" -eq "$postId" } | Select-Object -First 1
Assert-True "recommend feed includes intent matched post" ($null -ne $recommendedPost)
$firstRecommendReason = ($recommendedPost.recommendationReasons | Where-Object { -not [string]::IsNullOrWhiteSpace("$($_)") } | Select-Object -First 1)
Assert-True "recommend feed explains reason" ($null -ne $firstRecommendReason)

$recommendFeedback = Invoke-Json "POST" "/api/v1/feeds/feedback" @{
  postId = $postId
  action = "not_interested"
  reason = "live-flow-feedback"
} $authorToken
Assert-Ok "record recommend feedback" $recommendFeedback

$recommendAfterFeedback = Invoke-Json "GET" "/api/v1/feeds/recommend?size=20" $null $authorToken
Assert-Ok "recommend feed after feedback" $recommendAfterFeedback
$hiddenRecommendedPost = @($recommendAfterFeedback.data.items) | Where-Object { "$($_.post.id)" -eq "$postId" } | Select-Object -First 1
Assert-True "recommend feedback hides post" ($null -eq $hiddenRecommendedPost)

$hotFeed = Invoke-Json "GET" "/api/v1/feeds/hot?size=10" $null $authorToken
Assert-Ok "hot feed" $hotFeed
Assert-True "hot feed not empty" (@($hotFeed.data.items).Count -gt 0)

$searchHot = Invoke-Json "GET" "/api/v1/search/posts?q=$suffix&sort=hot&size=5"
Assert-Ok "search post hot sort" $searchHot
Assert-True "search hot result enriched" (@($searchHot.data.items).Count -gt 0 -and $null -ne @($searchHot.data.items)[0].author)

$searchLatest = Invoke-Json "GET" "/api/v1/search/posts?q=$suffix&sort=latest&size=5"
Assert-Ok "search post latest sort" $searchLatest
Assert-True "search latest result enriched" (@($searchLatest.data.items).Count -gt 0 -and $null -ne @($searchLatest.data.items)[0].counter)

$searchEmpty = Invoke-Json "GET" "/api/v1/search/posts?q=NoSuchOfferLabKeyword$suffix&company=NoSuchCompany&position=NoSuchPosition&sort=hot&size=5"
Assert-Ok "search empty state api" $searchEmpty
Assert-True "search empty returns no items" (@($searchEmpty.data.items).Count -eq 0)

$userSearch = Invoke-Json "GET" "/api/v1/users/search?q=ReviewActor&size=5"
Assert-Ok "search users" $userSearch

$recommendedUsers = Invoke-Json "GET" "/api/v1/users/search?size=5"
Assert-Ok "recommend users" $recommendedUsers
Assert-True "recommend users not empty" (@($recommendedUsers.data).Count -gt 0)

$privacy = Invoke-Json "GET" "/api/v1/users/me/privacy-settings" $null $authorToken
Assert-Ok "privacy settings" $privacy

$privacyUpdate = Invoke-Json "PUT" "/api/v1/users/me/privacy-settings" @{
  profileVisibility = "FOLLOWERS"
  intentVisibility = "PRIVATE"
  searchable = $false
  interactionNotification = $true
  systemNotification = $false
} $authorToken
Assert-Ok "update privacy settings" $privacyUpdate

$hiddenIntent = Invoke-Json "GET" "/api/v1/users/$($authorRegister.data.uid)/intent" $null $actorToken
Assert-Ok "privacy intent hidden" $hiddenIntent

$postReport = Invoke-Json "POST" "/api/v1/posts/$postId/reports" @{
  reason = "SPAM"
  detail = "Manual post moderation report $suffix"
} $actorToken
Assert-Ok "report post" $postReport
$postReportId = $postReport.data.reportId

$postReports = Invoke-Json "GET" "/api/v1/posts/admin/reports?status=0&limit=20" $null $adminToken
Assert-Ok "admin list post reports" $postReports
$pendingPostReport = @($postReports.data) | Where-Object { "$($_.id)" -eq "$postReportId" } | Select-Object -First 1
Assert-True "admin sees post report" ($null -ne $pendingPostReport)

$rejectPostReport = Invoke-Json "POST" "/api/v1/posts/admin/reports/$postReportId/review" @{
  approved = $false
  note = "Reject duplicate report"
} $adminToken
Assert-Ok "reject post report" $rejectPostReport
Assert-True "post report rejected" ($rejectPostReport.data.reportStatus -eq 2)

$commentReport = Invoke-Json "POST" "/api/v1/comments/$commentId/reports" @{
  reason = "ABUSE"
  detail = "Manual comment moderation report $suffix"
} $authorToken
Assert-Ok "report comment" $commentReport
$commentReportId = $commentReport.data.reportId

$commentReports = Invoke-Json "GET" "/api/v1/comments/admin/reports?status=0&limit=20" $null $adminToken
Assert-Ok "admin list comment reports" $commentReports
$pendingCommentReport = @($commentReports.data) | Where-Object { "$($_.id)" -eq "$commentReportId" } | Select-Object -First 1
Assert-True "admin sees comment report" ($null -ne $pendingCommentReport)

$approveCommentReport = Invoke-Json "POST" "/api/v1/comments/admin/reports/$commentReportId/review" @{
  approved = $true
  note = "Hide abusive comment"
} $adminToken
Assert-Ok "approve comment report" $approveCommentReport
Assert-True "comment report approved" ($approveCommentReport.data.reportStatus -eq 1)

$commentsAfterModeration = Invoke-Json "GET" "/api/v1/posts/$postId/comments?size=10" $null $authorToken
Assert-Ok "list comments after moderation" $commentsAfterModeration
$hiddenModeratedComment = @($commentsAfterModeration.data.items) | Where-Object { "$($_.id)" -eq "$commentId" } | Select-Object -First 1
Assert-True "moderated comment hidden" ($null -eq $hiddenModeratedComment)

$changePassword = Invoke-Json "PUT" "/api/v1/users/me/password" @{
  oldPassword = $password
  newPassword = "password456"
} $actorToken
Assert-Ok "change password" $changePassword

$actorRelogin = Invoke-Json "POST" "/api/v1/auth/login" @{ email = $actorEmail; password = "password456" }
Assert-Ok "login changed password" $actorRelogin
$actorChangedToken = $actorRelogin.data.token

$logoutAll = Invoke-Json "POST" "/api/v1/users/me/logout-all" $null $actorChangedToken
Assert-Ok "logout all sessions" $logoutAll

$ops = Invoke-Json "GET" "/api/v1/ops/status" $null $adminToken
Assert-Ok "ops status" $ops

$outbox = Invoke-Json "GET" "/api/v1/ops/outbox?limit=10" $null $adminToken
Assert-Ok "outbox list" $outbox

$searchRetryTasks = Invoke-Json "GET" "/api/v1/ops/search-index-retry-tasks?limit=10" $null $adminToken
Assert-Ok "search index retry task list" $searchRetryTasks

$notificationRetryTasks = Invoke-Json "GET" "/api/v1/ops/notification-retry-tasks?limit=10" $null $adminToken
Assert-Ok "notification retry task list" $notificationRetryTasks

if ($groupsTool -and $kafkaReachable) {
  try {
    $groupLines = @(& $groupsTool --bootstrap-server $KafkaBootstrap --describe --group $KafkaConsumerGroup 2>$null)
    $dataLine = $groupLines | Where-Object { $_ -match [Regex]::Escape($KafkaTopic) } | Select-Object -First 1
    if ($dataLine) {
      $kafkaLag = Get-KafkaLagFromLine $dataLine
      Assert-True "kafka $KafkaConsumerGroup lag zero" ($kafkaLag -eq 0)
    }
  } catch {
    Assert-True "kafka $KafkaConsumerGroup lag zero" $false
  }
} elseif ($groupsTool) {
  Assert-True "kafka $KafkaConsumerGroup lag zero" $false
}

$report = [ordered]@{
  ok = $true
  baseUrl = $BaseUrl
  timestamp = (Get-Date).ToString("s")
  authorEmail = $authorEmail
  actorEmail = $actorEmail
  authorUid = $authorRegister.data.uid
  actorUid = $actorRegister.data.uid
  adminAccount = $adminAccount
  postId = $postId
  commentId = $commentId
  replyId = $replyId
  postReportId = $postReportId
  commentReportId = $commentReportId
  notificationTotal = $notifications.data.total
  notificationRows = @($notificationList.data.items).Count
  firstNotificationType = $firstNotification.type
  firstNotificationSender = $firstNotification.sender.nickname
  notificationUnreadAfterOneRead = $notificationsAfterRead.data.total
  notificationUnreadAfterReadAll = $notificationsAfterReadAll.data.total
  trendTotalPosts = $trend.data.totalPosts
  userSearchRows = @($userSearch.data).Count
  recommendedUserRows = @($recommendedUsers.data).Count
  recommendFeedRows = @($recommendFeed.data.items).Count
  recommendAfterFeedbackRows = @($recommendAfterFeedback.data.items).Count
  hotFeedRows = @($hotFeed.data.items).Count
  recommendContainsPost = ($null -ne $recommendedPost)
  recommendReason = "$firstRecommendReason"
  recommendHiddenAfterFeedback = ($null -eq $hiddenRecommendedPost)
  searchHotRows = @($searchHot.data.items).Count
  searchLatestRows = @($searchLatest.data.items).Count
  searchEmptyRows = @($searchEmpty.data.items).Count
  kafkaOk = $kafkaOk
  kafkaTopicCount = @($kafkaTopics).Count
  kafkaLag = $kafkaLag
  elasticsearch = $esProbe
  searchAuthorNickname = $firstSearchItem.author.nickname
  interactionLiked = $interactionState.data.liked
  interactionFavorited = $interactionState.data.favorited
  commentRootAuthor = $rootComment.author.nickname
  commentReplyAuthor = $replyComment.author.nickname
  commentLikedAfterLike = $rootComment.myLiked
  commentLikedAfterUnlike = $rootAfterUnlike.myLiked
  privacyIntentVisibility = $privacyUpdate.data.intentVisibility
  hiddenIntentIsNull = ($null -eq $hiddenIntent.data)
  postReportRejected = ($rejectPostReport.data.reportStatus -eq 2)
  commentReportApproved = ($approveCommentReport.data.reportStatus -eq 1)
  moderatedCommentHidden = ($null -eq $hiddenModeratedComment)
  outboxRows = @($outbox.data).Count
  searchIndexRetryRows = @($searchRetryTasks.data).Count
  notificationRetryRows = @($notificationRetryTasks.data).Count
  steps = $steps
}

Write-SmokeReport $report
