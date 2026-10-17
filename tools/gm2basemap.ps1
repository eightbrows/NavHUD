<#
.SYNOPSIS
  国土地理院「地球地図日本」（Shapefile）から、背景地図の線（海岸線・県境）だけを取り出して、
  アプリの assets に置く小さなファイル（basemap.bin）に変換する。

.DESCRIPTION
  使い方（PowerShell 7）:
    1. 「地球地図日本」第2.2版の全レイヤの zip を取ってきて、mapdata/ に展開する（mapdata/ は git 管理外）。
         https://www1.gsi.go.jp/geowww/globalmap-gsi/download/data/gm-japan/gm-jpn-all_u_2_2.zip
         → mapdata/gm-jpn-all_u_2_2/coastl_jpn.shp など
    2. リポジトリの一番上で実行する:
         pwsh tools/gm2basemap.ps1            （変換して app/src/main/assets/basemap.bin を書く）
         pwsh tools/gm2basemap.ps1 -List      （入っているレイヤの一覧を出すだけ。書かない）

  取り出すもの（SPEC.md §8）:
    - 海岸線   : coastl_jpn  の全部（f_code = BA010）
    - 県境     : polbndl_jpn の use = 26（都道府県境。use = 30 は市区町村境なので取らない）
  座標系は緯度経度（ITRF94 / JGD2000）。GPS（WGS84）とは 1/100万の地図の精度より十分小さい差なので、直さずに使う。
  ライブラリは使わない（PowerShell の標準の機能だけ）。

  出力の形（リトルエンディアン。読む側は core/io/BaseMapFile.kt）:
    "NHBM"（4 バイト）、版 u8 = 1、単位 i32（座標 = 度 × 単位 の整数。100000 = 1e-5 度 ≒ 1.1 m）
    レイヤの数 u8、レイヤごとに 種類の番号 u8・名前の長さ u8・名前（UTF-8）
    線の数 u32、線ごとに:
      種類の番号 u8、点の数（varint）、範囲 最小緯度・最小経度・最大緯度・最大経度（zigzag varint）、
      点ごとに 緯度・経度（zigzag varint。1 点目は範囲の最小からの差、2 点目からは前の点からの差）
    長い線は ChunkPoints 点ずつに分ける（つなぎ目の点は両方に入れる）。画面の外の線を範囲ですぐ飛ばせるようにするため。

.PARAMETER Src
  展開したフォルダ。既定は mapdata/gm-jpn-all_u_2_2
.PARAMETER Out
  出力のファイル。既定は app/src/main/assets/basemap.bin
.PARAMETER ChunkPoints
  1 本の線の点の数の上限。既定は 64
.PARAMETER List
  入っているレイヤの一覧（形・数・属性）を出すだけにする
#>
param(
  [string]$Src = 'mapdata/gm-jpn-all_u_2_2',
  [string]$Out = 'app/src/main/assets/basemap.bin',
  [int]$ChunkPoints = 64,
  [switch]$List
)
$ErrorActionPreference = 'Stop'
[System.Text.Encoding]::RegisterProvider([System.Text.CodePagesEncodingProvider]::Instance)

# 種類の番号はアプリと同じ（core/io/BaseMapFile.kt の MapLayer）。あとで足すもの: 3 河川、4 道路、5 鉄道
$Layers = @(
  @{ Code = 1; Name = 'coastline';     File = 'coastl_jpn';  Keep = { param($row) $row['f_code'] -eq 'BA010' } }
  @{ Code = 2; Name = 'pref_boundary'; File = 'polbndl_jpn'; Keep = { param($row) $row['use'] -eq '26' } }
)
$Unit = 100000

$ShapeTypes = @{ 0 = 'なし'; 1 = '点'; 3 = '線'; 5 = '面'; 8 = '複数の点'; 11 = '点(Z)'; 13 = '線(Z)'; 15 = '面(Z)'; 21 = '点(M)'; 23 = '線(M)'; 25 = '面(M)' }

function Read-Dbf([string]$path) {
  $d = [System.IO.File]::ReadAllBytes($path)
  $enc = [System.Text.Encoding]::GetEncoding(932)
  $count = [BitConverter]::ToInt32($d, 4); $headLen = [BitConverter]::ToUInt16($d, 8); $recLen = [BitConverter]::ToUInt16($d, 10)
  $fields = @(); $p = 32
  while ($d[$p] -ne 0x0D) {
    $fields += [pscustomobject]@{ Name = [System.Text.Encoding]::ASCII.GetString($d, $p, 11).TrimEnd([char]0); Type = [char]$d[$p + 11]; Len = [int]$d[$p + 16] }
    $p += 32
  }
  $rows = [System.Collections.Generic.List[hashtable]]::new()
  for ($r = 0; $r -lt $count; $r++) {
    $row = @{}; $off = $headLen + $r * $recLen + 1
    foreach ($f in $fields) { $row[$f.Name] = $enc.GetString($d, $off, $f.Len).Trim(); $off += $f.Len }
    $rows.Add($row)
  }
  [pscustomobject]@{ Fields = $fields; Rows = $rows }
}

# 線（3）・線(Z)（13）・線(M)（23）の部分ごとの点（経度 x・緯度 y）。点の配列は [x0, y0, x1, y1, …]
function Read-ShpLines([string]$path) {
  $b = [System.IO.File]::ReadAllBytes($path)
  $type = [BitConverter]::ToInt32($b, 32)
  $records = [System.Collections.Generic.List[object]]::new()
  $pos = 100
  while ($pos -lt $b.Length) {
    $len = ([int]$b[$pos + 4] -shl 24) -bor ([int]$b[$pos + 5] -shl 16) -bor ([int]$b[$pos + 6] -shl 8) -bor [int]$b[$pos + 7]
    $c = $pos + 8
    $t = [BitConverter]::ToInt32($b, $c)
    $parts = [System.Collections.Generic.List[double[]]]::new()
    if ($t -eq 3 -or $t -eq 13 -or $t -eq 23) {
      $np = [BitConverter]::ToInt32($b, $c + 36); $n = [BitConverter]::ToInt32($b, $c + 40)
      $p0 = $c + 44 + 4 * $np
      for ($i = 0; $i -lt $np; $i++) {
        $a = [BitConverter]::ToInt32($b, $c + 44 + 4 * $i)
        $e = if ($i + 1 -lt $np) { [BitConverter]::ToInt32($b, $c + 48 + 4 * $i) } else { $n }
        $xy = [double[]]::new(2 * ($e - $a))
        for ($k = $a; $k -lt $e; $k++) {
          $xy[2 * ($k - $a)] = [BitConverter]::ToDouble($b, $p0 + 16 * $k)
          $xy[2 * ($k - $a) + 1] = [BitConverter]::ToDouble($b, $p0 + 16 * $k + 8)
        }
        $parts.Add($xy)
      }
    }
    $records.Add($parts)
    $pos = $c + 2 * $len
  }
  [pscustomobject]@{ Type = $type; Records = $records }
}

if ($List) {
  foreach ($shp in Get-ChildItem $Src -Filter *.shp | Sort-Object Name) {
    $b = [System.IO.File]::ReadAllBytes($shp.FullName)
    $type = [BitConverter]::ToInt32($b, 32)
    $dbf = Read-Dbf ([System.IO.Path]::ChangeExtension($shp.FullName, '.dbf'))
    "{0}  形={1}  件数={2}" -f $shp.Name, $ShapeTypes[$type], $dbf.Rows.Count
    foreach ($f in $dbf.Fields) {
      $vals = $dbf.Rows | Group-Object { $_[$f.Name] } | Sort-Object Count -Descending
      $shown = if ($vals.Count -le 8) { ($vals | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ' ' } else { "（$($vals.Count) 種類）" }
      "    {0}: {1}" -f $f.Name, $shown
    }
  }
  return
}

# zigzag varint（符号つきの小さい数を少ないバイトで書く）
$script:buf = [System.IO.MemoryStream]::new()
function Write-UVarint([long]$v) { while ($v -ge 0x80) { $script:buf.WriteByte([byte](($v -band 0x7F) -bor 0x80)); $v = $v -shr 7 }; $script:buf.WriteByte([byte]$v) }
function Write-SVarint([long]$v) { Write-UVarint (($v -shl 1) -bxor ($v -shr 63)) }
function Write-Bytes([byte[]]$bytes) { $script:buf.Write($bytes, 0, $bytes.Length) }

$lines = [System.Collections.Generic.List[object]]::new()   # @{ Code; Lat = int[]; Lon = int[] }
$summary = @()
foreach ($layer in $Layers) {
  $shp = Read-ShpLines (Join-Path $Src "$($layer.File).shp")
  $dbf = Read-Dbf (Join-Path $Src "$($layer.File).dbf")
  if ($shp.Records.Count -ne $dbf.Rows.Count) { throw "$($layer.File): .shp と .dbf の件数が違う" }
  $kept = 0; $points = 0; $chunks = 0
  for ($r = 0; $r -lt $shp.Records.Count; $r++) {
    if (-not (& $layer.Keep $dbf.Rows[$r])) { continue }
    $kept++
    foreach ($xy in $shp.Records[$r]) {
      # 単位の整数にして、続けて同じになった点は 1 つにする
      $lat = [System.Collections.Generic.List[int]]::new(); $lon = [System.Collections.Generic.List[int]]::new()
      for ($k = 0; $k -lt $xy.Length; $k += 2) {
        $x = [int][math]::Round($xy[$k] * $Unit); $y = [int][math]::Round($xy[$k + 1] * $Unit)
        if ($lat.Count -gt 0 -and $lat[$lat.Count - 1] -eq $y -and $lon[$lon.Count - 1] -eq $x) { continue }
        $lat.Add($y); $lon.Add($x)
      }
      if ($lat.Count -lt 2) { continue }
      $points += $lat.Count
      # ChunkPoints 点ずつに分ける（つなぎ目の点は両方に入れる）
      for ($s = 0; $s -lt $lat.Count - 1; $s += $ChunkPoints - 1) {
        $e = [math]::Min($s + $ChunkPoints, $lat.Count)
        $lines.Add(@{ Code = $layer.Code; Lat = $lat.GetRange($s, $e - $s).ToArray(); Lon = $lon.GetRange($s, $e - $s).ToArray() })
        $chunks++
      }
    }
  }
  $summary += [pscustomobject]@{ Layer = $layer.Name; File = $layer.File; Kept = $kept; Points = $points; Lines = $chunks }
}

Write-Bytes ([System.Text.Encoding]::ASCII.GetBytes('NHBM'))
$script:buf.WriteByte(1)
Write-Bytes ([BitConverter]::GetBytes([int]$Unit))
$script:buf.WriteByte([byte]$Layers.Count)
foreach ($layer in $Layers) {
  $name = [System.Text.Encoding]::UTF8.GetBytes($layer.Name)
  $script:buf.WriteByte([byte]$layer.Code); $script:buf.WriteByte([byte]$name.Length); Write-Bytes $name
}
Write-Bytes ([BitConverter]::GetBytes([uint32]$lines.Count))
foreach ($ln in $lines) {
  $minLat = ($ln.Lat | Measure-Object -Minimum).Minimum; $maxLat = ($ln.Lat | Measure-Object -Maximum).Maximum
  $minLon = ($ln.Lon | Measure-Object -Minimum).Minimum; $maxLon = ($ln.Lon | Measure-Object -Maximum).Maximum
  $script:buf.WriteByte([byte]$ln.Code)
  Write-UVarint $ln.Lat.Length
  Write-SVarint $minLat; Write-SVarint $minLon; Write-SVarint $maxLat; Write-SVarint $maxLon
  $pLat = $minLat; $pLon = $minLon
  for ($i = 0; $i -lt $ln.Lat.Length; $i++) {
    Write-SVarint ($ln.Lat[$i] - $pLat); Write-SVarint ($ln.Lon[$i] - $pLon)
    $pLat = $ln.Lat[$i]; $pLon = $ln.Lon[$i]
  }
}

$dir = Split-Path $Out -Parent
if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory $dir | Out-Null }
[System.IO.File]::WriteAllBytes((Join-Path (Get-Location) $Out), $script:buf.ToArray())
$summary | Format-Table -AutoSize | Out-String | Write-Host
"書いた: $Out（$($script:buf.Length) バイト、線 $($lines.Count) 本）"
