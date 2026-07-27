function Get-MigrationContentSha256 {
  param(
    [Parameter(Mandatory = $true)]
    [string] $Path
  )

  $text = [System.IO.File]::ReadAllText(
    $Path,
    [System.Text.UTF8Encoding]::new($false)
  )
  if ($text.Length -gt 0 -and $text[0] -eq [char] 0xFEFF) {
    $text = $text.Substring(1)
  }
  $normalized = (($text -replace "`r`n", "`n") -replace "`r", "`n")
  $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($normalized)
  $sha256 = [System.Security.Cryptography.SHA256]::Create()
  try {
    return ([System.BitConverter]::ToString($sha256.ComputeHash($bytes))).
      Replace("-", "").
      ToLowerInvariant()
  } finally {
    $sha256.Dispose()
  }
}
