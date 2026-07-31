param(
  [Parameter(Mandatory = $true, ValueFromRemainingArguments = $true)]
  [string[]]$Paths
)

$ErrorActionPreference = "Stop"
$gbk = [System.Text.Encoding]::GetEncoding(936)
$utf8 = [System.Text.Encoding]::UTF8
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$strongMarkerChars = @(
  0x6D93, 0x9359, 0x93C4, 0x7455, 0x93B4, 0x7487, 0x9422, 0x7F03, 0x9A9E,
  0x6434, 0x6924, 0x5BEE, 0x59DD, 0x93C8, 0x93C3, 0x7EFE, 0x951B, 0x9286,
  0x9225
) | ForEach-Object { [char]$_ }
$strongMarkers = [regex]("[" + [regex]::Escape((-join $strongMarkerChars)) + "]")

foreach ($path in $Paths) {
  $resolved = (Resolve-Path -LiteralPath $path).Path
  $source = [System.IO.File]::ReadAllText($resolved, $utf8)
  $lines = [regex]::Split($source, "\r?\n")
  $changed = $false

  for ($index = 0; $index -lt $lines.Length; $index++) {
    $line = $lines[$index]
    if ($strongMarkers.IsMatch($line)) {
      $candidate = $utf8.GetString($gbk.GetBytes($line))
      if ($candidate -notmatch "[\x00-\x08\x0B\x0C\x0E-\x1F]") {
        $line = $candidate
      }
    }

    $replacementQuestion = ([string][char]0xFFFD) + "?"
    $line = $line.Replace($replacementQuestion, [string][char]0x3002)
    $line = $line.Replace([string][char]0xFFFD, "")
    if ($line -ne $lines[$index]) {
      $lines[$index] = $line
      $changed = $true
    }
  }

  if ($changed) {
    [System.IO.File]::WriteAllText($resolved, ($lines -join "`n"), $utf8NoBom)
    Write-Host "Finished $resolved"
  }
}
