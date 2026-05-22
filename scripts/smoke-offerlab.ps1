param(
  [string]$BaseUrl = "http://127.0.0.1:8080",
  [string]$ReportPath = "C:\codeware\offerlab-smoke-report.json",
  [string]$AdminEmail = "",
  [string]$AdminPassword = "password123",
  [string]$KafkaBootstrap = "localhost:9092",
  [string]$KafkaHome = "C:\codeware\kafka_2.13-3.6.2"
)

$ErrorActionPreference = "Stop"

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
  }
  if ($null -ne $Body) {
    $args.ContentType = "application/json"
    $args.Body = ($Body | ConvertTo-Json -Depth 20 -Compress)
  }
  Invoke-RestMethod @args
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

function Test-KafkaTool {
  param([string]$Name)
  $tool = Join-Path $KafkaHome "bin\windows\$Name"
  if (Test-Path $tool) {
    return $tool
  }
  return $null
}

$kafkaOk = $false
$kafkaTopics = @()
$kafkaLag = $null
$topicsTool = Test-KafkaTool "kafka-topics.bat"
$groupsTool = Test-KafkaTool "kafka-consumer-groups.bat"
if ($topicsTool) {
  try {
    $kafkaTopics = @(& $topicsTool --bootstrap-server $KafkaBootstrap --list 2>$null)
    $kafkaOk = $kafkaTopics -contains "post.published"
    Assert-True "kafka post.published topic available" $kafkaOk
  } catch {
    Assert-True "kafka post.published topic available" $false
  }
}

$steps = @()
$suffix = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
$authorEmail = "smoke-author-$suffix@offerlab.local"
$actorEmail = "smoke-actor-$suffix@offerlab.local"
$password = "password123"

$authorRegister = Invoke-Json "POST" "/api/v1/auth/register" @{ email = $authorEmail; password = $password; nickname = "SmokeAuthor" }
Assert-Ok "register author" $authorRegister

$actorRegister = Invoke-Json "POST" "/api/v1/auth/register" @{ email = $actorEmail; password = $password; nickname = "SmokeActor" }
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

$postBody = @{
  postType = 1
  title = "Smoke OfferLab $suffix"
  content = "Smoke content with @SmokeActor and Java backend search keyword $suffix"
  visibility = 1
  extJson = '{"company":"SmokeCo","position":"Backend","yearsOfExp":3,"interviewResult":0}'
  tagNames = @("Java", "Smoke")
}
$publish = Invoke-Json "POST" "/api/v1/posts" $postBody $authorToken
Assert-Ok "publish post" $publish
$postId = $publish.data.postId

$detail = Invoke-Json "GET" "/api/v1/posts/$postId"
Assert-Ok "post detail" $detail
Assert-True "post detail has author" ($null -ne $detail.data.author -and $detail.data.author.uid -eq $authorRegister.data.uid)
Assert-True "post detail has counter" ($null -ne $detail.data.counter)

$comment = Invoke-Json "POST" "/api/v1/posts/$postId/comments" @{ content = "Smoke comment $suffix" } $actorToken
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
  content = "Smoke reply $suffix"
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
  targetCompanies = @("SmokeCo")
  targetPositions = @("Backend")
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
  reason = "smoke-feedback"
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

$userSearch = Invoke-Json "GET" "/api/v1/users/search?q=SmokeActor&size=5"
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

if ($groupsTool) {
  try {
    $groupLines = @(& $groupsTool --bootstrap-server $KafkaBootstrap --describe --group offerlab-feed-fanout 2>$null)
    $dataLine = $groupLines | Where-Object { $_ -match "post\.published" } | Select-Object -First 1
    if ($dataLine) {
      $parts = $dataLine -split "\s+"
      $lagText = @($parts | Where-Object { $_ -match "^\d+$" })[-1]
      $kafkaLag = [int]$lagText
      Assert-True "kafka feed fanout lag zero" ($kafkaLag -eq 0)
    }
  } catch {
    Assert-True "kafka feed fanout lag zero" $false
  }
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
  searchAuthorNickname = $firstSearchItem.author.nickname
  interactionLiked = $interactionState.data.liked
  interactionFavorited = $interactionState.data.favorited
  commentRootAuthor = $rootComment.author.nickname
  commentReplyAuthor = $replyComment.author.nickname
  commentLikedAfterLike = $rootComment.myLiked
  commentLikedAfterUnlike = $rootAfterUnlike.myLiked
  privacyIntentVisibility = $privacyUpdate.data.intentVisibility
  hiddenIntentIsNull = ($null -eq $hiddenIntent.data)
  outboxRows = @($outbox.data).Count
  steps = $steps
}

$dir = Split-Path -Parent $ReportPath
if ($dir -and -not (Test-Path $dir)) {
  New-Item -ItemType Directory -Path $dir | Out-Null
}
$report | ConvertTo-Json -Depth 20 | Set-Content -Path $ReportPath -Encoding UTF8
$report | ConvertTo-Json -Depth 20
