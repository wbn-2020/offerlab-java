param(
  [string]$BaseUrl = "http://127.0.0.1:8080",
  [string]$ReportPath = "C:\codeware\offerlab-smoke-report.json",
  [string]$AdminEmail = "",
  [string]$AdminPassword = "password123"
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

$comment = Invoke-Json "POST" "/api/v1/posts/$postId/comments" @{ content = "Smoke comment $suffix" } $actorToken
Assert-Ok "comment post" $comment
$commentId = $comment.data.commentId

$like = Invoke-Json "POST" "/api/v1/posts/$postId/like" $null $actorToken
Assert-Ok "like post" $like

$favorite = Invoke-Json "POST" "/api/v1/posts/$postId/favorite" $null $actorToken
Assert-Ok "favorite post" $favorite

$commentLike = Invoke-Json "POST" "/api/v1/comments/$commentId/like" $null $authorToken
Assert-Ok "like comment" $commentLike

Start-Sleep -Seconds 2

$notifications = Invoke-Json "GET" "/api/v1/notifications/unread-count" $null $authorToken
Assert-Ok "author unread notifications" $notifications

$search = Invoke-Json "GET" "/api/v1/search/posts?q=$suffix"
Assert-Ok "search post" $search

$trend = Invoke-Json "GET" "/api/v1/dashboard/trend?range=7d"
Assert-Ok "trend dashboard" $trend

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

$ops = Invoke-Json "GET" "/api/v1/ops/status" $null $adminToken
Assert-Ok "ops status" $ops

$outbox = Invoke-Json "GET" "/api/v1/ops/outbox?limit=10" $null $adminToken
Assert-Ok "outbox list" $outbox

$report = [ordered]@{
  ok = $true
  baseUrl = $BaseUrl
  timestamp = (Get-Date).ToString("s")
  authorUid = $authorRegister.data.uid
  actorUid = $actorRegister.data.uid
  adminAccount = $adminAccount
  postId = $postId
  commentId = $commentId
  notificationTotal = $notifications.data.total
  trendTotalPosts = $trend.data.totalPosts
  privacyIntentVisibility = $privacyUpdate.data.intentVisibility
  outboxRows = @($outbox.data).Count
  steps = $steps
}

$dir = Split-Path -Parent $ReportPath
if ($dir -and -not (Test-Path $dir)) {
  New-Item -ItemType Directory -Path $dir | Out-Null
}
$report | ConvertTo-Json -Depth 20 | Set-Content -Path $ReportPath -Encoding UTF8
$report | ConvertTo-Json -Depth 20
