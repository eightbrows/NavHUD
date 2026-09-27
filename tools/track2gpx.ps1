<#
.SYNOPSIS
  track.csv（GPS ロガーの生データ）を、エミュレータの Location 再生用の GPX（trkpt に時刻付き）に変換する。

.DESCRIPTION
  列はヘッダ名で参照する（track.csv と同じ方針）。latitude / longitude が読めない行は飛ばす。
  出力は sample/ に置く（sample/ は git 管理外）。
  エミュレータの Extended Controls → Location → Routes / Load GPX/KML で読み込み、再生する。

.PARAMETER Track
  入力の track.csv。既定は sample/session_20260814_075234/track.csv

.PARAMETER Out
  出力の GPX。既定は sample/<入力のフォルダ名>.gpx

.PARAMETER From
  この時刻（UTC、ISO 8601。例 2026-08-14T01:06:00Z）以降だけを出す。省略で最初から

.PARAMETER To
  この時刻（UTC）より前だけを出す。省略で最後まで

.EXAMPLE
  pwsh tools/track2gpx.ps1
.EXAMPLE
  # 62 秒欠損の前後だけ（POSITION LOST の確認用）
  pwsh tools/track2gpx.ps1 -From 2026-08-14T01:06:00Z -To 2026-08-14T01:09:30Z -Out sample/track_gap.gpx
#>
param(
    [string]$Track = (Join-Path $PSScriptRoot '..\sample\session_20260814_075234\track.csv'),
    [string]$Out,
    [string]$From,
    [string]$To
)

$ErrorActionPreference = 'Stop'
$inv = [Globalization.CultureInfo]::InvariantCulture

$Track = (Resolve-Path $Track).Path
if (-not $Out) {
    $name = Split-Path (Split-Path $Track -Parent) -Leaf
    $Out = Join-Path $PSScriptRoot "..\sample\$name.gpx"
}
$fromTime = if ($From) { [DateTimeOffset]::Parse($From, $inv) } else { $null }
$toTime = if ($To) { [DateTimeOffset]::Parse($To, $inv) } else { $null }

$lines = [IO.File]::ReadAllLines($Track, [Text.Encoding]::UTF8)
$header = $lines[0].TrimStart([char]0xFEFF).Split(',') | ForEach-Object { $_.Trim() }
$col = @{}
for ($i = 0; $i -lt $header.Count; $i++) { $col[$header[$i]] = $i }
foreach ($need in 'latitude', 'longitude') {
    if (-not $col.ContainsKey($need)) { throw "列 $need がありません: $Track" }
}

function Field($f, $name) {
    if (-not $col.ContainsKey($name)) { return $null }
    $i = $col[$name]
    if ($i -ge $f.Count) { return $null }
    $v = $f[$i].Trim()
    if ($v -eq '') { return $null }
    return $v
}

$sb = [Text.StringBuilder]::new()
[void]$sb.AppendLine('<?xml version="1.0" encoding="UTF-8"?>')
[void]$sb.AppendLine('<gpx version="1.1" creator="NavHUD tools/track2gpx.ps1" xmlns="http://www.topografix.com/GPX/1/1">')
[void]$sb.AppendLine('  <trk><name>' + [Security.SecurityElement]::Escape((Split-Path $Track -Leaf)) + '</name><trkseg>')

$count = 0
$skipped = 0
for ($n = 1; $n -lt $lines.Count; $n++) {
    if ($lines[$n].Trim() -eq '') { continue }
    $f = $lines[$n].Split(',')
    $lat = 0.0; $lon = 0.0
    if (-not [double]::TryParse((Field $f 'latitude'), [Globalization.NumberStyles]::Float, $inv, [ref]$lat) -or
        -not [double]::TryParse((Field $f 'longitude'), [Globalization.NumberStyles]::Float, $inv, [ref]$lon)) {
        $skipped++; continue
    }
    # 時刻: epoch_ms 優先、なければ utc_iso8601
    $time = $null
    $ms = Field $f 'epoch_ms'
    if ($ms) { $time = [DateTimeOffset]::FromUnixTimeMilliseconds([long]$ms) }
    elseif (Field $f 'utc_iso8601') { $time = [DateTimeOffset]::Parse((Field $f 'utc_iso8601'), $inv) }
    if (-not $time) { $skipped++; continue }
    if ($fromTime -and $time -lt $fromTime) { continue }
    if ($toTime -and $time -ge $toTime) { continue }

    $pt = '    <trkpt lat="' + $lat.ToString('R', $inv) + '" lon="' + $lon.ToString('R', $inv) + '">'
    $ele = Field $f 'altitude_ellipsoid_m'
    if ($ele) { $pt += '<ele>' + $ele + '</ele>' }
    $pt += '<time>' + $time.UtcDateTime.ToString("yyyy-MM-dd'T'HH:mm:ss.fff'Z'", $inv) + '</time></trkpt>'
    [void]$sb.AppendLine($pt)
    $count++
}
[void]$sb.AppendLine('  </trkseg></trk>')
[void]$sb.AppendLine('</gpx>')

$outDir = Split-Path $Out -Parent
if ($outDir -and -not (Test-Path $outDir)) { New-Item -ItemType Directory -Force $outDir | Out-Null }
[IO.File]::WriteAllText((Join-Path (Resolve-Path $outDir).Path (Split-Path $Out -Leaf)), $sb.ToString(), [Text.UTF8Encoding]::new($false))
Write-Output "$count 点を書き出しました（読めない行 $skipped）: $Out"
