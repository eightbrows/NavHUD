# 飛行場の一覧の資料（docs/airports.html）と、飛行場の番号の対応表（tools/airport_ids.csv）を書く（20261024-D06）。
# tools/gm2basemap.ps1 が basemap.bin を書いたあとに読み込んで動かす（. で読む。変換の変数 $apReport・$rwReport・$rwCheck などをそのまま使う）。
# データやしきい値を変えて basemap.bin を作り直すと、資料も同じ判定で作り直される。
#
# 飛行場の番号（A001 の形の通し番号）:
#   - 対応表（$AirportIds）の 1 行が 1 つの飛行場。国土数値情報の空港は「名前（C28_005）と所在地のコード（C28_001）」、OurAirports の飛行場は id で見分ける。
#   - 一度付けた番号は変えない。対応表にない飛行場が増えたら、一覧の順（都道府県の番号 → 北から）に、いちばん大きい番号の続きを付けて対応表に足す。
#   - データからなくなった飛行場の行は対応表に残す（番号は使い回さない）。
#   - 「入れていない」飛行場にも番号を付ける。最初に作ったとき（20261024-D06）は、一覧の順（都道府県の番号 → 北から）に A001 から付けた。

$PrefNames = @('', '北海道', '青森県', '岩手県', '宮城県', '秋田県', '山形県', '福島県', '茨城県', '栃木県', '群馬県', '埼玉県', '千葉県', '東京都', '神奈川県', '新潟県', '富山県', '石川県', '福井県', '山梨県', '長野県', '岐阜県', '静岡県', '愛知県', '三重県', '滋賀県', '京都府', '大阪府', '兵庫県', '奈良県', '和歌山県', '鳥取県', '島根県', '岡山県', '広島県', '山口県', '徳島県', '香川県', '愛媛県', '高知県', '福岡県', '佐賀県', '長崎県', '熊本県', '大分県', '宮崎県', '鹿児島県', '沖縄県')
function Esc($s) { [System.Net.WebUtility]::HtmlEncode("$s") }
function Fmt($v) { if ($null -eq $v -or "$v" -eq '') { '' } else { '{0:N0}' -f [double]$v } }
function Fmt1($v) { if ($null -eq $v -or "$v" -eq '') { '' } else { '{0:N1}' -f [double]$v } }

$oaAll = @(Import-Csv (Join-Path (Get-Location) $OaAirports) -Encoding utf8 | Where-Object iso_country -eq 'JP')
$oaRwAll = @(Import-Csv (Join-Path (Get-Location) $OaRunways) -Encoding utf8)
$oaById2 = @{}; foreach ($x in $oaAll) { $oaById2[$x.id] = $x }
$rwById = @{}
foreach ($r in $oaRwAll) { if (-not $rwById.ContainsKey($r.airport_ref)) { $rwById[$r.airport_ref] = [System.Collections.Generic.List[object]]::new() }; $rwById[$r.airport_ref].Add($r) }
$rwByCode = @{}
foreach ($r in $rwReport) { if (-not $r.ident) { continue }; if (-not $rwByCode.ContainsKey($r.code)) { $rwByCode[$r.code] = [System.Collections.Generic.List[object]]::new() }; $rwByCode[$r.code].Add($r) }
function NewRow($h) {
  $base = [ordered]@{ No = ''; Key = ''; Order = 9999; KsjCat = 0; Src = ''; Shown = $false; Code = ''; Iata = ''; NameJa = ''; NameEn = ''; PrefNo = 0; Pref = ''; Lat = 0.0; Lon = 0.0; Mil = $false; Large = $false; Cls = ''
    LenM = $null; LenSrc = ''; KsjLen = $null; OaLen = $null; RwCount = $null; RwDrawn = $null; RwEst = 0; Area = $false; HasPoly = $false; Status = ''
    Problems = [System.Collections.Generic.List[string]]::new(); Notes = [System.Collections.Generic.List[string]]::new(); Reasons = [System.Collections.Generic.List[string]]::new()
    CodeFrom = $null; MarkFrom = $null; Runways = @(); KsjStatus = ''; OaType = ''; OaId = ''; KsjName = ''; KsjCode = '' }
  foreach ($k in $h.Keys) { $base[$k] = $h[$k] }
  [pscustomobject]$base
}

# 一覧の行: 変換の判定（$apReport。国土数値情報の空港と、国土数値情報にない OurAirports の候補）
$rows = [System.Collections.Generic.List[object]]::new()
$order = 0
foreach ($a in $apReport) {
  $order++
  $isKsj = $a.src -eq 'KSJ'
  $oa = if ($a.oaId) { $oaById2[$a.oaId] } else { $null }
  $prefNo = if ($isKsj -and $a.locCode) { [int]("$($a.locCode)".Substring(0, 2)) } elseif ($oa) { [int]($oa.iso_region -replace 'JP-', '') } else { 0 }
  $ksjLens = @("$($a.ksjLens)" -split ',' | Where-Object { $_ -match '^\d+$' } | ForEach-Object { [int]$_ })
  $openRw = if ($oa -and $rwById.ContainsKey($oa.id)) { @($rwById[$oa.id] | Where-Object { $_.closed -ne '1' -and $_.length_ft -match '^\d+(\.\d+)?$' }) } else { @() }
  $oaMax = ($openRw | Where-Object { $_.length_ft -match '^\d+' } | ForEach-Object { [double]$_.length_ft * 0.3048 } | Measure-Object -Maximum).Maximum
  $myRw = if ($a.symbol -and $rwByCode.ContainsKey($a.code)) { @($rwByCode[$a.code]) } else { @() }
  $rows.Add((NewRow @{ Order = $order; Key = $(if ($isKsj) { "KSJ|$($a.name)|$($a.locCode)" } else { "OA|$($a.oaId)" }); KsjCat = $(if ($isKsj -and "$($a.cat)" -match '^\d+$') { [int]$a.cat } else { 0 }); Src = $a.src; Shown = [bool]$a.symbol
    Code = $a.code; Iata = $(if ($oa) { $oa.iata_code } else { '' }); NameJa = $(if ($isKsj) { $a.name } else { '' }); NameEn = $(if ($oa) { $oa.name } elseif (-not $isKsj) { $a.name } else { '' })
    PrefNo = $prefNo; Pref = $PrefNames[$prefNo]; Lat = [double]$a.lat; Lon = [double]$a.lon; Mil = (-not $isKsj); Large = ($a.cls -eq 'large'); Cls = $a.cls
    LenM = $(if ("$($a.runwayM)" -ne '') { [double]$a.runwayM } else { $null }); LenSrc = $(if ($isKsj) { 'KSJ' } else { 'OA' })
    KsjLen = $(if ($ksjLens.Count) { ($ksjLens | Measure-Object -Maximum).Maximum } else { $null }); OaLen = $oaMax
    RwCount = $(if ($oa) { $openRw.Count } elseif ($ksjLens.Count) { $ksjLens.Count } else { $null })
    RwDrawn = $(if ($a.symbol) { @($myRw | Where-Object drawn).Count } else { $null }); RwEst = @($myRw | Where-Object estDrawn).Count
    Area = [bool]$a.area; HasPoly = $isKsj; Runways = $myRw; KsjStatus = $a.status; OaType = $(if ($oa) { $oa.type } else { $a.oaType }); OaId = $a.oaId
    KsjName = $(if ($isKsj) { $a.name } else { '' }); KsjCode = $(if ($isKsj) { $a.locCode } else { '' }) }))
}
# OurAirports の略号のある飛行場のうち、変換の候補にならなかったもの（ヘリポート・形の違う略号など）。
# 大分県央飛行場（OurAirports では gps_code ROIT）は、国土数値情報の行にまとめる
$candIds = [System.Collections.Generic.HashSet[string]]::new(); foreach ($r in $rows) { if ($r.OaId) { [void]$candIds.Add($r.OaId) } }
$oita = $rows | Where-Object NameJa -eq '大分県央飛行場' | Select-Object -First 1
$roit = $oaAll | Where-Object gps_code -eq 'ROIT' | Select-Object -First 1
foreach ($x in $oaAll) {
  $code = if ($x.icao_code) { $x.icao_code } elseif ($x.gps_code) { $x.gps_code } else { $x.ident }
  if (-not ($x.icao_code -or $x.gps_code -or $x.ident -cmatch '^R[JO][A-Z]{2}$')) { continue }
  if ($candIds.Contains($x.id)) { continue }
  if ($oita -and $roit -and $x.id -eq $roit.id) { continue }
  $la = [double]$x.latitude_deg; $pn = [int]($x.iso_region -replace 'JP-', '')
  $open = if ($rwById.ContainsKey($x.id)) { @($rwById[$x.id] | Where-Object { $_.closed -ne '1' -and $_.length_ft -match '^\d+(\.\d+)?$' }) } else { @() }
  $mx = ($open | Where-Object { $_.length_ft -match '^\d+' } | ForEach-Object { [double]$_.length_ft * 0.3048 } | Measure-Object -Maximum).Maximum
  $r = NewRow @{ Key = "OA|$($x.id)"; Src = 'OA'; Code = $code; Iata = $x.iata_code; NameEn = $x.name; PrefNo = $pn; Pref = $PrefNames[$pn]; Lat = $la; Lon = [double]$x.longitude_deg; Mil = $true
    LenM = $mx; LenSrc = 'OA'; OaLen = $mx; RwCount = $open.Count; Status = 'out'; OaType = $x.type; OaId = $x.id }
  if ($x.type -eq 'heliport') { $r.Reasons.Add('HELI|') }
  elseif ($code -cmatch '^RO[A-Z]{2}$' -and $la -ge $OaRoMaxLat) { $r.Reasons.Add('RONORTH|') }
  elseif (-not ($code -cmatch '^R[JO][A-Z]{2}$')) { $r.Reasons.Add("CODEFORM|略号 $code") }
  elseif ($x.type -notin $OaTypes) { $r.Reasons.Add("OTHER|種類 $($x.type)") }
  else { $r.Reasons.Add('OTHER|') }
  $rows.Add($r)
}
$rows = @($rows | Sort-Object PrefNo, @{ Expression = 'Lat'; Descending = $true }, Key)
$dupKeys = @($rows | Group-Object Key | Where-Object Count -gt 1)
if ($dupKeys.Count) { throw "飛行場の見分け方が重なる: $(($dupKeys | ForEach-Object Name) -join ', ')" }

# 番号: 対応表を読み、ない飛行場に続きの番号を付けて足す
$idPath = Join-Path (Get-Location) $AirportIds
$idRows = [System.Collections.Generic.List[object]]::new()
if (Test-Path $idPath) { foreach ($x in (Import-Csv $idPath -Encoding utf8)) { $idRows.Add($x) } }
$noByKey = @{}
foreach ($x in $idRows) {
  $k = if ($x.src -eq 'KSJ') { "KSJ|$($x.ksj_name)|$($x.ksj_code)" } else { "OA|$($x.oa_id)" }
  if ($noByKey.ContainsKey($k)) { throw "${AirportIds}: 同じ飛行場の行が 2 つある: $k" }
  $noByKey[$k] = $x.no
}
$maxNo = ($idRows | ForEach-Object { [int]("$($_.no)".Substring(1)) } | Measure-Object -Maximum).Maximum
if ($null -eq $maxNo) { $maxNo = 0 }
$added = [System.Collections.Generic.List[object]]::new()
foreach ($r in $rows) {
  if ($noByKey.ContainsKey($r.Key)) { $r.No = $noByKey[$r.Key]; continue }
  $maxNo++; $r.No = 'A{0:D3}' -f $maxNo; $noByKey[$r.Key] = $r.No
  $x = [pscustomobject][ordered]@{ no = $r.No; src = $r.Src; ksj_name = $r.KsjName; ksj_code = $r.KsjCode; oa_id = $(if ($r.Src -eq 'OA') { $r.OaId } else { '' }); memo = "$(if ($r.Code) { "$($r.Code) " })$(if ($r.NameJa) { $r.NameJa } else { $r.NameEn })" }
  $idRows.Add($x); $added.Add($x)
}
if ($added.Count) {
  $csv = ($idRows | ConvertTo-Csv -NoTypeInformation -UseQuotes AsNeeded) -join "`n"
  [System.IO.File]::WriteAllText($idPath, "$csv`n", [System.Text.UTF8Encoding]::new($false))
  "飛行場の番号: $($added.Count) か所に番号を付けて $AirportIds に足した（$($added[0].no)〜$($added[-1].no)）" | Write-Host
}
$liveKeys = [System.Collections.Generic.HashSet[string]]::new(); foreach ($r in $rows) { [void]$liveKeys.Add($r.Key) }
$goneIds = @($idRows | Where-Object { -not $liveKeys.Contains($(if ($_.src -eq 'KSJ') { "KSJ|$($_.ksj_name)|$($_.ksj_code)" } else { "OA|$($_.oa_id)" })) })

# 略号・記号の重なり（core/view/BaseMapBuilder.kt の pickCodeLabels と同じ考え方の見積もり）。
# 画面は北向き・幅 360dp の端末で、R1 の距離環が半径 86dp。記号・文字の大きさは core/Tuning.kt の値（20261024-D05）を写したもの
$R1Steps = @(0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10, 25, 50, 100)
$DpPerR1 = 86.0
$kf = 10.0 / 11.0; $charW = 7 * $kf; $lineH = 16 * $kf; $pad = 1.25
function MarkDp($r) { if ($r.Mil) { if ($r.Large) { 16 } else { 11 } } else { if ($r.Large) { 17 } else { 11 } } }
function CodeOff($r) { if (-not $r.Mil -and $r.Large) { [math]::Max(8.5, 12 * 0.8) } else { (MarkDp $r) / 2 } }
function MarkR($r) { if (-not $r.Mil -and $r.Large) { 12 + 0.6 } else { (MarkDp $r) / 2 } }
function Prio($r) { $(if ($r.Large) { 2 } else { 0 }) + $(if ($r.Mil) { 0 } else { 1 }) }
$sorted = @($rows | Where-Object Shown | Sort-Object @{ Expression = { Prio $_ }; Descending = $true }, Order)
for ($i = 0; $i -lt $sorted.Count; $i++) {
  $a = $sorted[$i]
  for ($j = 0; $j -lt $i; $j++) {
    $b = $sorted[$j]
    $dist = KmBetween $a.Lat $a.Lon $b.Lat $b.Lon
    if ($dist -gt 400) { continue }
    $e = ($b.Lon - $a.Lon) * 111.32 * [math]::Cos($a.Lat * [math]::PI / 180); $n = ($b.Lat - $a.Lat) * 111.32
    foreach ($r1 in $R1Steps) {
      $sc = $DpPerR1 / $r1
      if ($dist * $sc -lt (MarkR $a) + (MarkR $b) - 2) { if ($null -eq $a.MarkFrom -or $r1 -lt $a.MarkFrom[0]) { $a.MarkFrom = @($r1, $b) }; break }
    }
    foreach ($r1 in $R1Steps) {
      $sc = $DpPerR1 / $r1
      $oa_ = CodeOff $a; $ob = CodeOff $b
      $ax = $oa_; $ay = -$oa_; $bx = $e * $sc + $ob; $by = -$n * $sc - $ob; $w = 4 * $charW
      $hit = ($ax - $pad) -lt ($bx + $w + $pad) -and ($bx - $pad) -lt ($ax + $w + $pad) -and ($ay - $lineH - $pad) -lt ($by + $pad) -and ($by - $lineH - $pad) -lt ($ay + $pad)
      if ($hit) { if ($null -eq $a.CodeFrom -or $r1 -lt $a.CodeFrom[0]) { $a.CodeFrom = @($r1, $b) }; break }
    }
  }
}

# 推定の向きの書き方（20261024-D07: 最初の向きで外れたら、もう一方の求め方で試す）
function HdgSrcText($src, $decl, $ident) { if ($src -eq 'degT') { 'OurAirports の真方位' } else { "番号 $($ident.Split('/')[0]) から（偏角 $(Fmt1 $decl)°）" } }
function AltText($w) {
  if (-not $w.estAltTried -or "$($w.estAltHdg)" -eq '') { return '' }
  if ($w.estDrawn) { "。最初の $(HdgSrcText $w.estAltHdgSrc $w.estAltDecl $w.ident)（$(Fmt1 $w.estAltHdg)°）では端が敷地の面から $(Fmt $w.estAltLeDistM)・$(Fmt $w.estAltHeDistM) m で外れたので、こちらを使った" }
  else { "。もう一方の $(HdgSrcText $w.estAltHdgSrc $w.estAltDecl $w.ident)（$(Fmt1 $w.estAltHdg)°）でも $(Fmt $w.estAltLeDistM)・$(Fmt $w.estAltHeDistM) m で外れた" }
}

# 理由
$CodeDefaultMaxR1 = 25
function NoName($r) { "$($r.No) $(if ($r.NameJa) { $r.NameJa } else { $r.NameEn })" }
foreach ($r in $rows) {
  if ($r.Shown) {
    foreach ($w in $r.Runways) {
      if ($w.estDrawn) {
        $why = if ($w.ends -eq 'both') { "OurAirports の座標は端が敷地の面から $(Fmt $w.leDistM)・$(Fmt $w.heDistM) m" } else { '両端の座標がない' }
        $r.Notes.Add("EST|$($w.ident)（$why。向き $(Fmt1 $w.estHdg)°・$(HdgSrcText $w.estHdgSrc $w.estDecl $w.ident)$(AltText $w)）")
      } elseif ($w.est) {
        $r.Problems.Add("ESTOUT|$($w.ident)（推定した端と敷地の面の距離 $(Fmt $w.estLeDistM)・$(Fmt $w.estHeDistM) m。向き $(Fmt1 $w.estHdg)°・$(HdgSrcText $w.estHdgSrc $w.estDecl $w.ident)$(AltText $w)。長さ $(Fmt $w.estLenM) m）")
      } elseif ($w.ends -ne 'both') {
        $extra = if (-not $w.lengthM) { '。長さ・路面の分からない滑走路（古い滑走路の行の可能性）' } elseif (@($r.Runways).Count -gt 1) { '。滑走路が 2 本以上あるので推定しない' } else { '' }
        $r.Problems.Add("NOENDS|$($w.ident)$extra")
      } elseif ($w.outside) {
        $r.Problems.Add("OUTSIDE|$($w.ident)（端と敷地の面の距離 $(Fmt $w.leDistM)・$(Fmt $w.heDistM) m$(if (@($r.Runways).Count -gt 1) { '。滑走路が 2 本以上あるので推定しない' })）")
      } elseif ($w.lengthM -and $w.calcM -and [math]::Abs([double]$w.calcM - [double]$w.lengthM) / [double]$w.lengthM -gt 0.1) {
        $r.Problems.Add("LENGTH|$($w.ident)（データ $(Fmt $w.lengthM) m・座標から $(Fmt $w.calcM) m）")
      }
    }
    if ($r.CodeFrom) {
      $o = $r.CodeFrom[1]; $t = "R1 $($r.CodeFrom[0]) km 以上で、$($o.Code)（$(NoName $o)）の略号を優先"
      if ($r.CodeFrom[0] -le $CodeDefaultMaxR1) { $r.Problems.Add("CODE|$t") } else { $r.Notes.Add("CODEWIDE|$t") }
    }
    if ($r.MarkFrom) { $o = $r.MarkFrom[1]; $r.Notes.Add("MARK|R1 $($r.MarkFrom[0]) km 以上で、$($o.Code)（$($o.No)）の記号と重なる（$('{0:N1}' -f (KmBetween $r.Lat $r.Lon $o.Lat $o.Lon)) km）") }
    if ($r.Mil) { $r.Notes.Add('NOAREA|') }
    if ($r.Src -eq 'KSJ' -and $r.KsjCat -eq 6) { $r.Notes.Add('SHARED|国土数値情報の種別が「共用空港」') }
    elseif ($r.Src -eq 'KSJ' -and $r.NameEn -match 'JASDF|JMSDF|JGSDF|Air Base|Air Station') { $r.Notes.Add("SHARED|OurAirports の名前が「$($r.NameEn)」（国土数値情報の種別は共用空港でない）") }
    if ($r.Src -eq 'KSJ' -and $r.KsjLen -and $r.OaLen -and [math]::Abs($r.KsjLen - $r.OaLen) / $r.KsjLen -gt 0.1) {
      $cls2 = if ($r.OaLen -ge $AirportLargeRunwayM) { '大' } else { '小' }; $cls1 = if ($r.Large) { '大' } else { '小' }
      $r.Notes.Add("LENDIFF|国土数値情報 $(Fmt $r.KsjLen) m・OurAirports $(Fmt ([math]::Round($r.OaLen))) m$(if ($cls1 -ne $cls2) { "（OurAirports の値なら区分は$cls2）" })")
    }
    $r.Status = if ($r.Problems.Count) { 'part' } else { 'ok' }
  } elseif ($r.Status -ne 'out') {
    $r.Status = 'out'
    if ($r.Src -eq 'KSJ') {
      if ($r.KsjStatus -ne '供用中') { $r.Reasons.Add("PAUSED|$($r.KsjStatus)") }
      if ($r.NameJa -match '（.+地区）$') { $r.Reasons.Add('DISTRICT|') }
      elseif (-not $r.Code) { $r.Reasons.Add('NOCODE|') }
      elseif ($r.Cls -eq 'tiny') { $r.Reasons.Add("TINY|いちばん長い滑走路 $(Fmt $r.LenM) m") }
    } else {
      if ($r.Cls -eq 'unknown') {
        $all = if ($rwById.ContainsKey($r.OaId)) { @($rwById[$r.OaId]) } else { @() }
        $r.Reasons.Add($(if ($all.Count -eq 0) { 'NOLEN|runways.csv に滑走路の行がない' } else { "NOLEN|runways.csv の滑走路 $($all.Count) 本（閉鎖 $(@($all | Where-Object closed -eq '1').Count)・長さなし $(@($all | Where-Object { -not $_.length_ft }).Count)）" }))
      } elseif ($r.Cls -eq 'tiny') { $r.Reasons.Add("TINY|いちばん長い滑走路 $(Fmt $r.LenM) m") }
    }
  }
}
if ($oita -and $roit) {
  $oita.Reasons.Add("ROIT|OurAirports の $($roit.ident)（$($roit.name)、gps_code ROIT）が同じ飛行場（$('{0:N2}' -f (KmBetween $oita.Lat $oita.Lon ([double]$roit.latitude_deg) ([double]$roit.longitude_deg))) km）")
  $oita.NameEn = $roit.name
}

$ReasonInfo = [ordered]@{
  NOENDS   = @('問題', '滑走路の両端の座標がなく、推定もしていない（その滑走路・延長線を描けない）')
  OUTSIDE  = @('問題', "滑走路の端が敷地の面から $RunwayMaxOutsideM m より外なので外し、推定もしていない（その滑走路・延長線が出ない）")
  ESTOUT   = @('問題', "滑走路を推定したが、推定した端が敷地の面から $RunwayMaxOutsideM m より外なので描いていない（推定が外れたと見なす）")
  LENGTH   = @('問題', '滑走路の長さが、座標から求めた長さと 1 割以上違う（描いているが怪しい）')
  CODE     = @('問題', '略号が近くの飛行場と重なり、初期値（略号は 〜25k）の縮尺でも略号を出さないことがある')
  EST      = @('注記', '滑走路の両端を推定して描いている（「推定」。中心は標点、長さは滑走路延長、向きは真方位か番号から）')
  CODEWIDE = @('注記', '略号が近くの飛行場と重なり、略号を 〜50k・〜100k にしたときの広域で出さないことがある')
  MARK     = @('注記', '記号が近くの飛行場の記号と重なる（広域で）')
  LENDIFF  = @('注記', 'いちばん長い滑走路の長さが、国土数値情報と OurAirports で 1 割以上違う（区分は国土数値情報の値）')
  NOAREA   = @('注記', '軍用（OurAirports だけ）なので敷地の面がない')
  SHARED   = @('注記', '自衛隊・米軍との共用。国土数値情報にあるので民間の記号')
  PAUSED   = @('入れていない', '休止中（国土数値情報）')
  NOCODE   = @('入れていない', "略号が付かない（$AirportIcaoMaxKm km 以内に「RJ／RO＋英字 2 文字」の略号の飛行場がない）")
  ROIT     = @('入れていない', "OurAirports にはあるが、略号が北緯 $OaRoMaxLat 度より北の「RO」なので認めていない")
  DISTRICT = @('入れていない', 'ほかの空港の一部の地区（記号・略号は入れず、敷地の面だけ入れている）')
  NOLEN    = @('入れていない', '滑走路の長さが分からない')
  TINY     = @('入れていない', "いちばん長い滑走路が $(Fmt $AirportMinRunwayM) m 未満（極小）")
  HELI     = @('入れていない', 'ヘリポート（OurAirports の種類）')
  RONORTH  = @('入れていない', "「RO」の略号だが北緯 $OaRoMaxLat 度より北")
  CODEFORM = @('入れていない', '略号の形が「RJ／RO＋英字 2 文字」でない（地域の識別番号）')
  OTHER    = @('入れていない', 'そのほか')
}
$cnt = @{ ok = @($rows | Where-Object Status -eq 'ok').Count; part = @($rows | Where-Object Status -eq 'part').Count; out = @($rows | Where-Object Status -eq 'out').Count }
$StatusLabel = @{ ok = '問題なし'; part = '一部に問題あり'; out = '入れていない' }

function Mk($b) { if ($b -eq $true) { '<span class="y">○</span>' } elseif ($b -eq $false) { '<span class="n">×</span>' } else { '<span class="n">−</span>' } }
function TagOf($kind) { switch ($kind) { '問題' { 'rp' } '注記' { 'rn' } default { 'ro' } } }
function ReasonHtml($list) {
  (@($list | Where-Object { $_ }) | ForEach-Object { $key, $detail = $_ -split '\|', 2; $info = $ReasonInfo[$key]
    "<li class='$(TagOf $info[0])'><span class='tag'>$($info[0])</span>$(Esc $info[1])$(if ($detail) { "：$(Esc $detail)" })</li>" }) -join ''
}
function AllReasons($r) { @($r.Problems) + @($r.Reasons) + @($r.Notes) }

# 一覧の表
$tr = [System.Text.StringBuilder]::new()
$lastPref = -1
foreach ($r in $rows) {
  if ($r.PrefNo -ne $lastPref) { [void]$tr.Append("<tr class='group'><th colspan='13'>$(Esc $r.Pref)</th></tr>`n"); $lastPref = $r.PrefNo }
  $kind = if ($r.Status -eq 'out' -and $r.Cls -notin 'large', 'small') { '−' } else { "$(if ($r.Mil) { '軍用' } else { '民間' })（$(if ($r.Large) { '大' } else { '小' })）" }
  $len = if ($r.LenM) { "$(Fmt $r.LenM)<small> $($r.LenSrc)</small>" } else { '' }
  $other = if ($r.Src -eq 'KSJ' -and $r.OaLen) { "<br><small>OA $(Fmt ([math]::Round($r.OaLen)))</small>" } else { '' }
  $rwc = if ($null -ne $r.RwCount) { "$(if ($r.Shown) { "$($r.RwDrawn) / " })$($r.RwCount)$(if ($r.RwEst) { "<br><span class='est'>推定 $($r.RwEst)</span>" })" } else { '' }
  $codeMk = if (-not $r.Shown) { Mk $false } elseif ($r.CodeFrom -and $r.CodeFrom[0] -le $CodeDefaultMaxR1) { '<span class="w">△</span>' } else { Mk $true }
  $rwMk = if (-not $r.Shown) { Mk $false } elseif ($r.RwDrawn -eq 0) { Mk $false } elseif ($r.RwDrawn -lt $r.RwCount) { '<span class="w">△</span>' } else { Mk $true }
  if ($r.RwEst) { $rwMk += "<br><span class='est'>推定</span>" }
  $name = "<span class='no'>$($r.No)</span> $(if ($r.NameJa) { "<b>$(Esc $r.NameJa)</b><br>" })<small>$(Esc $r.NameEn)</small>"
  [void]$tr.Append("<tr class='s-$($r.Status)' data-st='$($r.Status)'><td class='nm'>$name</td><td class='nw'><span class='pill $($r.Status)'>$($StatusLabel[$r.Status])</span></td><td class='mono'>$(Esc $r.Code)</td><td class='mono'>$(Esc $r.Iata)</td><td class='nw'>$kind</td><td class='num'>$len$other</td><td class='num'>$rwc</td><td class='c'>$(Mk $r.HasPoly)</td><td class='c'>$(Mk $r.Shown)</td><td class='c'>$codeMk</td><td class='c'>$(Mk $r.Area)</td><td class='c'>$rwMk</td><td class='rs'><ul>$(ReasonHtml (AllReasons $r))</ul></td></tr>`n")
}

# 理由ごとの表
$rt = [System.Text.StringBuilder]::new()
foreach ($key in $ReasonInfo.Keys) {
  $hits = @($rows | Where-Object { (AllReasons $_) | Where-Object { $_ -like "$key|*" } })
  if (-not $hits.Count) { continue }
  $info = $ReasonInfo[$key]
  $items = ($hits | ForEach-Object { $r = $_; $d = (AllReasons $r) | Where-Object { $_ -like "$key|*" } | ForEach-Object { ($_ -split '\|', 2)[1] } | Where-Object { $_ }
    "<li><span class='no'>$($r.No)</span> <span class='mono'>$(Esc $r.Code)</span> $(Esc $(if ($r.NameJa) { $r.NameJa } else { $r.NameEn }))$(if ($d) { "<span class='note'>：$(Esc ($d -join '／'))</span>" })</li>" }) -join ''
  [void]$rt.Append("<tr><td class='nw'><span class='tag $(TagOf $info[0])'>$($info[0])</span></td><td class='why'><b>$(Esc $info[1])</b><div class='note'>$($hits.Count) か所</div></td><td><ul class='hits'>$items</ul></td></tr>`n")
}

# 推定した滑走路の表
$noByCode = @{}; foreach ($r in $rows) { if ($r.Shown -and $r.Code) { $noByCode[$r.Code] = $r.No } }
$et = [System.Text.StringBuilder]::new()
foreach ($w in @($rwReport | Where-Object est | Sort-Object { $noByCode[$_.code] })) {
  $alt = if ($w.estAltTried -and "$($w.estAltHdg)" -ne '') { "<br><small>$(if ($w.estDrawn) { '最初の向き' } else { 'もう一方の向き' }) $(Fmt1 $w.estAltHdg)°（$(if ($w.estAltHdgSrc -eq 'degT') { '真方位' } else { '番号から' })）: $(Fmt $w.estAltLeDistM)・$(Fmt $w.estAltHeDistM) m で外れる</small>" } else { '' }
  $fit = if ($w.estDrawn) { if ("$($w.estLeDistM)" -eq '') { '<span class="y">描く</span><br><small>敷地の面がないので調べない</small>' } else { "<span class='y'>収まる</span><br><small>$(Fmt $w.estLeDistM)・$(Fmt $w.estHeDistM) m</small>$alt" } } else { "<span class='w'>外れる</span><br><small>$(Fmt $w.estLeDistM)・$(Fmt $w.estHeDistM) m</small>$alt" }
  $hdg = "$(Fmt1 $w.estHdg)°<br><small>$(if ($w.estHdgSrc -eq 'degT') { 'OurAirports の真方位' } else { "番号 $($w.ident.Split('/')[0]) − 偏角 $(Fmt1 $w.estDecl)°" })$(if ($w.estAltTried -and $w.estDrawn) { '（もう一方の向きで外れたので）' })</small>"
  $nm = if ($w.name) { $w.name } else { '' }
  [void]$et.Append("<tr><td class='nm'><span class='no'>$($noByCode[$w.code])</span> <span class='mono'>$(Esc $w.code)</span> $(Esc $nm)</td><td class='mono'>$(Esc $w.ident)</td><td class='mono'>$('{0:F5}' -f [double]$w.estLat), $('{0:F5}' -f [double]$w.estLon)<br><small>$(if ($w.centerSrc -eq 'KSJ') { '国土数値情報の標点' } else { 'OurAirports の位置' })</small></td><td class='num'>$(Fmt $w.estLenM)<br><small>$(if ($w.estLenSrc -eq 'KSJ') { '国土数値情報' } else { 'OurAirports' })</small></td><td class='num'>$hdg</td><td class='c'>$fit</td></tr>`n")
}
# 推定の確かめ（実際の座標がある滑走路で同じ推定をしたとき）
function Stat($vals) { $v = @($vals | ForEach-Object { [math]::Abs([double]$_) } | Sort-Object); if (-not $v.Count) { return @('', '', '', 0) }; @($v[[int][math]::Floor(($v.Count - 1) / 2)], $v[[int][math]::Floor(($v.Count - 1) * 0.9)], $v[-1], $v.Count) }
$okChk = @($rwCheck | Where-Object { -not $_.outside })
$sNum = Stat ($okChk | Where-Object { "$($_.numDiff)" -ne '' } | ForEach-Object numDiff)
$sDeg = Stat ($okChk | Where-Object { "$($_.degTDiff)" -ne '' } | ForEach-Object degTDiff)
$sCen = Stat ($okChk | Where-Object single | ForEach-Object centerM)
$sEnd = Stat ($okChk | Where-Object { $_.single -and "$($_.endM)" -ne '' } | ForEach-Object endM)
$fitN = @($okChk | Where-Object { $_.estFits -eq $true }).Count; $fitAll = @($okChk | Where-Object { "$($_.estFits)" -ne '' }).Count
$ct = @"
<tr><th>向き（番号から。偏角で真方位に直す）</th><td class='num'>$(Fmt1 $sNum[0])°</td><td class='num'>$(Fmt1 $sNum[1])°</td><td class='num'>$(Fmt1 $sNum[2])°</td><td class='num'>$($sNum[3])</td></tr>
<tr><th>向き（OurAirports の真方位）</th><td class='num'>$(Fmt1 $sDeg[0])°</td><td class='num'>$(Fmt1 $sDeg[1])°</td><td class='num'>$(Fmt1 $sDeg[2])°</td><td class='num'>$($sDeg[3])</td></tr>
<tr><th>中心（標点と滑走路の中ほどの距離。滑走路が 1 本だけの空港）</th><td class='num'>$(Fmt $sCen[0]) m</td><td class='num'>$(Fmt $sCen[1]) m</td><td class='num'>$(Fmt $sCen[2]) m</td><td class='num'>$($sCen[3])</td></tr>
<tr><th>端（推定した端と実際の端の距離の大きい方。1 本だけの空港）</th><td class='num'>$(Fmt $sEnd[0]) m</td><td class='num'>$(Fmt $sEnd[1]) m</td><td class='num'>$(Fmt $sEnd[2]) m</td><td class='num'>$($sEnd[3])</td></tr>
"@

$ksjN = @($rows | Where-Object Src -eq 'KSJ').Count
$shownN = @($rows | Where-Object Shown).Count
$civN = @($rows | Where-Object { $_.Shown -and -not $_.Mil }).Count
$milN = $shownN - $civN
$oaDate = (Get-Item (Join-Path (Get-Location) $OaAirports)).LastWriteTime.ToString('yyyy-MM-dd')
$oaRwDate = (Get-Item (Join-Path (Get-Location) $OaRunways)).LastWriteTime.ToString('yyyy-MM-dd')
$openRwN = @($rwReport | Where-Object ident).Count
$drawnRw = @($rwReport | Where-Object drawn).Count
$estRw = @($rwReport | Where-Object estDrawn).Count
$noLenText = (@($rwNoLen | Where-Object { $oaById2.ContainsKey($_.airport_ref) }) | ForEach-Object { $x = $_; $rr = $rows | Where-Object OaId -eq $x.airport_ref | Select-Object -First 1
  "$(if ($rr) { "$($rr.No) $(if ($rr.NameJa) { $rr.NameJa } else { $rr.NameEn })" } else { $x.airport_ident }) の $($x.le_ident)/$($x.he_ident)" }) -join '、'
$appVer = if ((Get-Content app/build.gradle.kts -Raw) -match 'appVersionName = "([^"]+)"') { $Matches[1] } else { '?' }
$oaCoded = @($oaAll | Where-Object { $_.icao_code -or $_.gps_code -or $_.ident -cmatch '^R[JO][A-Z]{2}$' }).Count

$html = @"
<!DOCTYPE html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>NavHUD 飛行場の一覧</title>
<style>
  :root {
    --bg: #eef0f3; --paper: #ffffff; --ink: #14181f; --muted: #586070; --rule: #cdd2da; --soft: #f1f3f6; --head: #14181f;
    --ok: #1d7a46; --ok-soft: #e3f3e9; --part: #9a5600; --part-soft: #fff1da; --est: #1f5fa8; --est-soft: #e3edf9;
    --sans: "Hiragino Sans", "Hiragino Kaku Gothic ProN", "Yu Gothic UI", "Meiryo", system-ui, sans-serif;
    --mono: ui-monospace, "Cascadia Mono", "Consolas", "Menlo", "MS Gothic", monospace;
    color-scheme: light;
  }
  @media screen and (prefers-color-scheme: dark) {
    :root:not([data-theme="light"]) {
      --bg: #07090c; --paper: #10141a; --ink: #e6e9ee; --muted: #98a0ae; --rule: #2a313c; --soft: #181d25; --head: #e6e9ee;
      --ok: #6fd39a; --ok-soft: #12261b; --part: #f0b45a; --part-soft: #2b2112; --est: #8cbcf0; --est-soft: #132233;
      color-scheme: dark;
    }
  }
  :root[data-theme="dark"] {
    --bg: #07090c; --paper: #10141a; --ink: #e6e9ee; --muted: #98a0ae; --rule: #2a313c; --soft: #181d25; --head: #e6e9ee;
    --ok: #6fd39a; --ok-soft: #12261b; --part: #f0b45a; --part-soft: #2b2112; --est: #8cbcf0; --est-soft: #132233;
    color-scheme: dark;
  }
  * { box-sizing: border-box; }
  body { margin: 0; background: var(--bg); color: var(--ink); font-family: var(--sans); font-size: 14px; line-height: 1.6; padding: 16px; }
  .sheet { background: var(--paper); max-width: 1240px; margin: 0 auto; padding: 16px 20px; border-radius: 8px; }
  header { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 14px; border-bottom: 2px solid var(--head); padding-bottom: 6px; margin-bottom: 12px; }
  h1 { margin: 0; font-size: 1.5em; line-height: 1.2; }
  .ver { margin: 0; color: var(--muted); font-size: .86em; }
  h2 { margin: 1.2em 0 .5em; font-size: 1.1em; line-height: 1.3; padding: .2em .55em; background: var(--head); color: var(--paper); border-radius: 3px; }
  h3 { margin: 1em 0 .35em; font-size: 1em; line-height: 1.3; border-bottom: 1px solid var(--rule); padding-bottom: .15em; }
  p { margin: 0 0 .5em; }
  ul { margin: 0 0 .5em; padding-left: 1.3em; }
  li { margin: 0 0 .2em; }
  code, .mono { font-family: var(--mono); font-size: .92em; }
  code { background: var(--soft); padding: 0 .25em; border-radius: 3px; overflow-wrap: anywhere; }
  .note, small { color: var(--muted); font-size: .92em; }
  .tw { overflow-x: auto; margin: 0 0 .6em; }
  table { width: 100%; border-collapse: collapse; }
  th, td { text-align: left; vertical-align: top; padding: .25em .5em; border-bottom: 1px solid var(--rule); }
  thead th { border-bottom: 2px solid var(--head); white-space: nowrap; font-size: .92em; }
  tbody th { font-weight: 700; }
  td.nw { white-space: nowrap; }
  td.num { text-align: right; white-space: nowrap; font-variant-numeric: tabular-nums; }
  td.c { text-align: center; }
  td.why { min-width: 16em; width: 24%; }
  .counts { display: flex; flex-wrap: wrap; gap: 6px 28px; margin: 0 0 .6em; }
  .counts div { display: flex; align-items: baseline; gap: 8px; }
  .counts b { font-size: 1.6em; font-variant-numeric: tabular-nums; }
  .no { font-family: var(--mono); font-size: .92em; color: var(--muted); }
  .pill { display: inline-block; font-size: .86em; padding: 0 .5em; border-radius: 3px; white-space: nowrap; }
  .pill.ok { background: var(--ok-soft); color: var(--ok); }
  .pill.part { background: var(--part-soft); color: var(--part); font-weight: 700; }
  .pill.out { background: var(--soft); color: var(--muted); }
  .est { display: inline-block; font-size: .8em; padding: 0 .35em; border-radius: 3px; background: var(--est-soft); color: var(--est); white-space: nowrap; }
  .y { color: var(--ok); } .n { color: var(--muted); } .w { color: var(--part); font-weight: 700; }
  .tag { display: inline-block; font-size: .8em; padding: 0 .4em; margin-right: .4em; border-radius: 3px; background: var(--soft); color: var(--muted); white-space: nowrap; }
  .rp .tag, .tag.rp { background: var(--part-soft); color: var(--part); font-weight: 700; }
  .rn .tag, .tag.rn { background: var(--ok-soft); color: var(--ok); }
  table.ap { font-size: 13px; }
  table.ap td.nm { min-width: 12em; }
  table.ap td.rs { min-width: 22em; }
  table.ap tr.group th { background: var(--soft); font-size: .92em; }
  table.ap tr.s-part td { background: var(--part-soft); }
  table.ap tr.s-out td, table.ap tr.s-out td b { color: var(--muted); }
  td.rs ul, ul.hits { list-style: none; padding-left: 0; margin: 0; }
  ul.hits { display: flex; flex-wrap: wrap; gap: 2px 16px; }
  .filter { display: flex; flex-wrap: wrap; gap: 6px 16px; align-items: center; margin: 0 0 .5em; }
  .filter label { display: inline-flex; gap: 6px; align-items: center; cursor: pointer; }
  @page { size: A4 landscape; margin: 10mm; }
  @media print {
    :root { --bg: #fff; --paper: #fff; --ink: #000; --muted: #333; --rule: #999; --soft: #eee; --head: #000; color-scheme: light; }
    body { padding: 0; font-size: 8pt; line-height: 1.4; }
    .sheet { max-width: none; padding: 0; }
    h2 { -webkit-print-color-adjust: exact; print-color-adjust: exact; break-after: avoid; }
    tr { break-inside: avoid; }
    .filter { display: none; }
    .tw { overflow: visible; }
  }
</style>
</head>
<body>
<main class="sheet">
  <header>
    <h1>NavHUD 飛行場の一覧</h1>
    <p class="ver">背景地図の飛行場の表示の状態と、その理由 ・ アプリ $appVer の basemap.bin ・ tools/gm2basemap.ps1 が basemap.bin と一緒に書き出す（手で直さない）</p>
  </header>

  <h2>1 まとめ</h2>
  <div class="counts">
    <div><span>載せた飛行場</span><b>$($rows.Count)</b></div>
    <div><span class="pill ok">問題なし</span><b>$($cnt.ok)</b></div>
    <div><span class="pill part">一部に問題あり</span><b>$($cnt.part)</b></div>
    <div><span class="pill out">入れていない</span><b>$($cnt.out)</b></div>
  </div>
  <ul>
    <li>地図に記号を出している飛行場は <b>$shownN か所</b>（民間 $civN・軍用 $milN）です。問題なし $($cnt.ok) と一部に問題あり $($cnt.part) を足した数です。</li>
    <li>入れていない $($cnt.out) か所は、国土数値情報の空港で記号を入れていないもの（$(@($rows | Where-Object { $_.Src -eq 'KSJ' -and -not $_.Shown }).Count)）と、OurAirports の略号のある飛行場で入れていないもの（$(@($rows | Where-Object { $_.Src -eq 'OA' -and -not $_.Shown }).Count)）です。</li>
    <li>滑走路: 地図に出す飛行場の、閉じていない滑走路 $openRwN 本のうち、<b>$drawnRw 本</b>を描いています。そのうち <span class="est">推定</span> は $estRw 本です（両端の座標がないので、標点・長さ・向きから推定した滑走路。見た目は同じ）。</li>
    <li>状態の決め方: <span class="pill part">一部に問題あり</span> は、記号は出ているが「問題」の理由が 1 つ以上あるもの（滑走路・延長線が出ない、怪しい滑走路を描いている、初期値の縮尺で略号が出ないことがある）。<span class="pill ok">問題なし</span> は「問題」の理由がないもの（「注記」は、そういう作りなので問題に数えない）。<span class="pill out">入れていない</span> は記号・略号を入れていないもの。</li>
    <li>番号（A001 など）は飛行場ごとに決めた番号で、データが変わっても変えません（対応表は tools/airport_ids.csv）。新しい飛行場には続きの番号を付け、なくなった飛行場の番号は使い回しません。$(if ($goneIds.Count) { "対応表にあって今のデータにない番号: $(($goneIds | ForEach-Object { "$($_.no)（$(Esc $_.memo)）" }) -join '、')。" })</li>
  </ul>

  <h2>2 データと決まり</h2>
  <div class="tw"><table>
    <thead><tr><th>データ</th><th>日付</th><th>使っている所</th></tr></thead>
    <tbody>
      <tr><th>国土数値情報「空港データ」（C28）</th><td class="nw">2020 年版（C28-20）</td><td>民間の空港の標点・敷地の面・供用の状況・種別・滑走路の長さ（$ksjN の空港）</td></tr>
      <tr><th>OurAirports airports.csv</th><td class="nw">$oaDate に取得<br><small>ファイルの日時</small></td><td>略号（ICAO）・IATA・英語の名前・都道府県（iso_region）・軍用の飛行場（略号のある飛行場 $oaCoded 件）</td></tr>
      <tr><th>OurAirports runways.csv</th><td class="nw">$oaRwDate に取得<br><small>ファイルの日時</small></td><td>滑走路の本数・長さ・幅・両端の座標・真方位・閉鎖</td></tr>
    </tbody>
  </table></div>
  <ul>
    <li>入れる飛行場: 国土数値情報の空港は、供用中で、$AirportIcaoMaxKm km 以内の OurAirports の飛行場と突き合わせて略号が付いたもの。OurAirports だけの飛行場は、種類が空港・水上機の基地で、略号が「RJ＋英字 2 文字」か「RO＋英字 2 文字」（RO は北緯 $OaRoMaxLat 度より南だけ）のもの。どちらも、いちばん長い滑走路が $(Fmt $AirportMinRunwayM) m 以上（$(Fmt $AirportLargeRunwayM) m 以上が「大」）。</li>
    <li>滑走路: 閉じていない滑走路の両端の座標を結んで描きます。国土数値情報の空港は、端が敷地の面から <b>$RunwayMaxOutsideM m</b> より外なら、座標が怪しいので描きません（敷地の面がない軍用の飛行場は調べない）。</li>
    <li>長さの分からない滑走路の行（runways.csv の length_ft が空）は、ないものとして扱います（本数にも問題にも数えない）。今のデータでは $(if ($noLenText) { $noLenText } else { 'ありません' })。</li>
    <li>推定: 閉じていない滑走路が 1 本だけの空港で、その滑走路の両端の座標がないか、端が敷地の面から外れているときは、中心 = 国土数値情報の標点（なければ OurAirports の位置）、長さ = 国土数値情報の滑走路延長（なければ OurAirports）、向き = OurAirports の真方位（なければ滑走路の番号 × 10° の磁方位から、その場所の偏角を引いた真方位）で両端を推定します。推定した端が敷地の面から $RunwayMaxOutsideM m より外なら、向きのもう一方の求め方（真方位 ⇔ 番号から）でもう一度推定し、収まった方を使います。どちらも外れたら描きません。</li>
    <li>偏角は国土地理院の近似式（2020.0 年値）で求めます: D = 8°15.822′ + 18.462′Δφ − 7.726′Δλ + 0.007′Δφ² − 0.007′ΔφΔλ − 0.655′Δλ²（Δφ = 緯度 − 37°、Δλ = 経度 − 138°。西偏が正）。真方位 = 磁方位 − D。</li>
  </ul>

  <h2>3 理由ごとの飛行場</h2>
  <div class="tw"><table>
    <thead><tr><th>種類</th><th>理由</th><th>飛行場</th></tr></thead>
    <tbody>
$($rt.ToString())
    </tbody>
  </table></div>
  <p class="note">略号・記号の重なりは、アプリの決め方（重なる略号は、民間（大）→ 軍用（大）→ 民間（小）→ 軍用（小）の順に 1 つだけ出す）で、北向き・幅 360dp の画面（R1 の距離環が半径 86dp）として見積もりました。2 つずつ比べた見積もりなので、3 つ以上が重なる所では、実際に出る略号が少し違うことがあります。</p>

  <h2>4 推定した滑走路</h2>
  <div class="tw"><table>
    <thead><tr><th>飛行場</th><th>番号</th><th>中心</th><th>長さ [m]</th><th>向き（真方位）</th><th>敷地の面</th></tr></thead>
    <tbody>
$($et.ToString())
    </tbody>
  </table></div>
  <p class="note">「敷地の面」は、推定した両端と国土数値情報の敷地の面との距離（le 側・he 側。0 は面の中）。$RunwayMaxOutsideM m より外なら描きません。</p>
  <h3>推定の確かめ</h3>
  <p>実際の両端の座標がある滑走路（端が敷地から外れているものを除く）で同じ推定をしたときの、実際とのずれです。</p>
  <div class="tw"><table>
    <thead><tr><th>ずれ</th><th>中央値</th><th>9 割がこれ以下</th><th>いちばん大きい</th><th>本数</th></tr></thead>
    <tbody>
$ct
    </tbody>
  </table></div>
  <p class="note">同じ推定で、推定した両端が敷地の面から $RunwayMaxOutsideM m 以内に収まったのは $fitN / $fitAll 本（滑走路が 1 本だけの国土数値情報の空港）。</p>

  <h2>5 一覧（北から、都道府県の順）</h2>
  <div class="filter">
    <span class="note">表示する状態:</span>
    <label><input type="checkbox" data-f="ok" checked> 問題なし（$($cnt.ok)）</label>
    <label><input type="checkbox" data-f="part" checked> 一部に問題あり（$($cnt.part)）</label>
    <label><input type="checkbox" data-f="out" checked> 入れていない（$($cnt.out)）</label>
  </div>
  <ul class="note">
    <li>区分: 民間 = 国土数値情報の空港（自衛隊・米軍との共用を含む）、軍用 = OurAirports だけの飛行場。</li>
    <li>最長の滑走路: KSJ = 国土数値情報の滑走路延長、OA = OurAirports の runways.csv（閉鎖を除く）。国土数値情報の空港には、比べるために OA の値も小さく載せました。区分は先に書いた値で決めています。</li>
    <li>滑走路の本数: 「描いている本数 / 閉鎖を除く本数」（OurAirports。OurAirports にない空港は国土数値情報の滑走路延長の数）。<span class="est">推定</span> は推定して描いている本数。</li>
    <li>○ 出している、× 出していない、△ 一部（滑走路の一部だけ、または初期値の縮尺でも略号が出ないことがある）。延長線は滑走路と同じなので、滑走路の列で示しました。</li>
  </ul>
  <div class="tw"><table class="ap" id="ap">
    <thead><tr><th>番号・名前（国土数値情報 / OurAirports）</th><th>状態</th><th>ICAO</th><th>IATA</th><th>区分</th><th>最長の滑走路 [m]</th><th>滑走路<br>描く / 本数</th><th>敷地の<br>面</th><th>記号</th><th>略号</th><th>敷地</th><th>滑走路・<br>延長線</th><th>理由</th></tr></thead>
    <tbody>
$($tr.ToString())
    </tbody>
  </table></div>
</main>
<script>
  (function () {
    var boxes = document.querySelectorAll('.filter input');
    function apply() {
      var on = {}; boxes.forEach(function (b) { on[b.dataset.f] = b.checked; });
      var lastGroup = null, groupHas = false;
      document.querySelectorAll('#ap tbody tr').forEach(function (tr) {
        if (tr.classList.contains('group')) { if (lastGroup) lastGroup.hidden = !groupHas; lastGroup = tr; groupHas = false; return; }
        var show = !!on[tr.dataset.st]; tr.hidden = !show; if (show) groupHas = true;
      });
      if (lastGroup) lastGroup.hidden = !groupHas;
    }
    boxes.forEach(function (b) { b.addEventListener('change', apply); });
  })();
</script>
</body>
</html>
"@
$docPath = Join-Path (Get-Location) $AirportDoc
$docDir = Split-Path $docPath -Parent
if ($docDir -and -not (Test-Path $docDir)) { New-Item -ItemType Directory $docDir | Out-Null }
[System.IO.File]::WriteAllText($docPath, ($html -replace "`r`n", "`n"), [System.Text.UTF8Encoding]::new($false))
"飛行場の一覧: $AirportDoc（$($rows.Count) か所。問題なし $($cnt.ok)・一部に問題あり $($cnt.part)・入れていない $($cnt.out)）" | Write-Host
