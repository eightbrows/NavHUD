<#
.SYNOPSIS
  国土地理院「地球地図日本」（Shapefile）の線・面・点のレイヤを、アプリの assets に置く背景地図のファイル（basemap.bin）に変換する。

.DESCRIPTION
  使い方（PowerShell 7）:
    1. 「地球地図日本」第2.2版の全レイヤの zip を取ってきて、mapdata/ に展開する（mapdata/ は git 管理外）。
         https://www1.gsi.go.jp/geowww/globalmap-gsi/download/data/gm-japan/gm-jpn-all_u_2_2.zip
         → mapdata/gm-jpn-all_u_2_2/coastl_jpn.shp など
    2. リポジトリの一番上で実行する:
         pwsh tools/gm2basemap.ps1            （変換して app/src/main/assets/basemap.bin を書く。レイヤごとの大きさも出す）
         pwsh tools/gm2basemap.ps1 -List      （入っているレイヤの一覧を出すだけ。書かない）

  取り込むもの（SPEC.md §8。種類の番号はアプリの core/io/BaseMapFile.kt の MapLayer と同じ）:
    線  1 海岸線（coastl）  2 県境（polbndl の use = 26）  6 市区町村境（polbndl の use = 30）
        3 河川（riverl）  4 道路（roadl）  5 鉄道（raill）  7 フェリーの航路（ferryl）  8 ダム・堰（miscl）
    面  9 湖沼など（inwatera）  10 市街地（builtupa）     … 塗らずに、輪郭を線として入れる
    点  20 空港（airp）  21 港（portp）  22 駅（rstatp）  23 ダム・堰（miscp）  24 都市（builtupp） … 名前も入れる
    行政区域の面（polbnda）は、境の線と同じ形なので入れない。
    線・点ごとに「レイヤの中の種類」（0〜255）を持つ。道路は rtt、鉄道は fco、河川は lit、湖沼は hyt、空港は use、海岸線は acc。
    道路は rtt が 16（高速道路）・14（国道）・15（県道。主要地方道など）の線だけを入れる（データにはこの 3 つしかない）。
    アプリは高速道路と国道を描き、県道は設定の「県道も出す」が ON のときだけ描く。
    河川は、1 級河川を見分ける値がないので、名前のある川（nam の最後の語が G. / K. / KAWA / GAWA）で、lit = 1 の線だけを入れる。
    名前のない線（UNK）と lit = 2（湖・沼・川幅の大きい川など、水の面の中を通る線。面は「湖沼など」のレイヤにある）は入れない
    （名前に作業のメモのような値が付いた線も、すべて lit = 2 なので入らない。20261018-D02 のレポート）。
    鉄道は、新幹線・地下鉄を見分ける値がないので、全部入れる（20261018-D01 のレポート）。
  座標系は緯度経度（ITRF94 / JGD2000）。GPS（WGS84）との差は 1/100万の地図の精度より十分小さいので、直さずに使う。
  ライブラリは使わない（PowerShell と、その中の .NET の標準の機能だけ。重い計算は下の C# で行う）。

  縮尺の段に合わせて、点を間引いた線を前もって作る（間引きは Douglas–Peucker 法。距離は東西・南北の m）:
    段 0（詳細用。R1 25 km 未満）: 間引かない
    段 1（R1 25 km 以上用）       : 許す差 70 m（R1 25 km の画面で約 1 px）
    段 2（R1 50 km 以上用）       : 許す差 150 m（R1 50 km の画面で約 1 px）。範囲の対角が 300 m 未満の線、
                                    道路・鉄道・河川・市区町村境は 1 km（約 7 px）未満の線を入れない
    段 3（R1 100 km 以上用）      : 許す差 500 m（R1 100 km の画面で約 2 px）。範囲の対角が 500 m 未満の線、
                                    道路・鉄道・河川・市区町村境は 4 km（約 14 px）未満の線を入れない（点が多く、広域では細かすぎるため）
    （R1 2.5 km 以上用の段も試したが、元の点の間隔が数百 m あるので、許す差を 1 px（約 7 m）にするとほとんど減らず、入れていない）
  アプリは今の R1（自機から 1 つ目の距離環の距離）以下で、いちばん間引いた段を使う。

  出力の形（リトルエンディアン。読む側は core/io/BaseMapFile.kt。版 2）:
    "NHBM"、版 u8 = 2、単位 i32（座標 = 度 × 単位 の整数。10000 = 1e-4 度 ≒ 11 m。1/100万の地図の精度より十分細かい）
    レイヤの数 u8、レイヤごとに 種類の番号 u8・名前の長さ u8・名前（UTF-8）
    段の数 u8、段ごとに: 使う R1 の下限 i32 [m]、かたまり（段 × レイヤ）の数 u8、かたまりごとに
      種類の番号 u8・線の数 u32・バイト数 u32（アプリは使うレイヤのかたまりだけを読む。ほかは飛ばす）、そのあとに線ごとに
      レイヤの中の種類 u8、点の数（varint）、範囲（最小緯度・最小経度は前の線の最小からの差を zigzag varint、
      緯度・経度の幅 = 最大 − 最小 を varint。かたまりの最初の線の「前の線」は 0）、
      点ごとに 緯度・経度（zigzag varint。1 点目は範囲の最小からの差、2 点目からは前の点からの差）
    点の数 u32、点ごとに 種類の番号 u8・レイヤの中の種類 u8・緯度・経度（zigzag varint。前の点からの差。最初の点は 0 からの差）・名前の長さ（varint）・名前（UTF-8）
    線は、同じ段・同じ種類の中で、近い線が続くように並べてある（見出しの差を小さくするため。読む側は順番に頼らない）。
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

Add-Type -TypeDefinition @"
using System; using System.IO; using System.Collections.Generic; using System.Text;

public class ShpRecord { public int Type; public List<double[]> Parts = new List<double[]>(); public double X, Y; }

public static class Gm {
  // 点（1）・線（3, 13, 23）・面（5, 15, 25）。部分ごとの点は [x0, y0, x1, y1, ...]（x = 経度、y = 緯度）
  public static List<ShpRecord> ReadShp(string path) {
    var b = File.ReadAllBytes(path); var list = new List<ShpRecord>(); int pos = 100;
    while (pos < b.Length) {
      int len = (b[pos+4] << 24) | (b[pos+5] << 16) | (b[pos+6] << 8) | b[pos+7]; int c = pos + 8;
      var r = new ShpRecord { Type = BitConverter.ToInt32(b, c) };
      if (r.Type == 1 || r.Type == 11 || r.Type == 21) { r.X = BitConverter.ToDouble(b, c + 4); r.Y = BitConverter.ToDouble(b, c + 12); }
      else if (r.Type == 3 || r.Type == 13 || r.Type == 23 || r.Type == 5 || r.Type == 15 || r.Type == 25) {
        int np = BitConverter.ToInt32(b, c + 36), n = BitConverter.ToInt32(b, c + 40); int p0 = c + 44 + 4 * np;
        for (int i = 0; i < np; i++) {
          int a = BitConverter.ToInt32(b, c + 44 + 4 * i), e = i + 1 < np ? BitConverter.ToInt32(b, c + 48 + 4 * i) : n;
          var xy = new double[2 * (e - a)];
          for (int k = a; k < e; k++) { xy[2*(k-a)] = BitConverter.ToDouble(b, p0 + 16 * k); xy[2*(k-a)+1] = BitConverter.ToDouble(b, p0 + 16 * k + 8); }
          r.Parts.Add(xy);
        }
      }
      list.Add(r); pos = c + 2 * len;
    }
    return list;
  }

  public static List<Dictionary<string, string>> ReadDbf(string path) {
    var d = File.ReadAllBytes(path); var enc = Encoding.GetEncoding(932);
    int cnt = BitConverter.ToInt32(d, 4), hl = BitConverter.ToUInt16(d, 8), rl = BitConverter.ToUInt16(d, 10);
    var names = new List<string>(); var lens = new List<int>(); int p = 32;
    while (d[p] != 0x0D) { names.Add(Encoding.ASCII.GetString(d, p, 11).TrimEnd('\0')); lens.Add(d[p+16]); p += 32; }
    var rows = new List<Dictionary<string, string>>();
    for (int r = 0; r < cnt; r++) { var row = new Dictionary<string, string>(); int off = hl + r * rl + 1;
      for (int f = 0; f < names.Count; f++) { row[names[f]] = enc.GetString(d, off, lens[f]).Trim(); off += lens[f]; } rows.Add(row); }
    return rows;
  }

  // 度 → 単位の整数。続けて同じになった点は 1 つにする
  public static int[] ToUnits(double[] xy, int unit, out int[] lon) {
    var la = new List<int>(); var lo = new List<int>();
    for (int k = 0; k < xy.Length; k += 2) {
      int x = (int)Math.Round(xy[k] * unit), y = (int)Math.Round(xy[k+1] * unit);
      if (la.Count > 0 && la[la.Count-1] == y && lo[lo.Count-1] == x) continue;
      la.Add(y); lo.Add(x);
    }
    lon = lo.ToArray(); return la.ToArray();
  }

  // Douglas–Peucker（許す差 tol [m]）。最初と最後の点は残す。距離は線の中ほどの緯度で東西を縮めた平面で測る
  public static int[] Simplify(int[] lat, int[] lon, double tol, int unit) {
    int n = lat.Length; if (n <= 2 || tol <= 0) { var all = new int[n]; for (int i = 0; i < n; i++) all[i] = i; return all; }
    double mPerUnit = 6371000.0 * Math.PI / 180.0 / unit;
    double kx = Math.Cos(lat[n / 2] / (double)unit * Math.PI / 180.0) * mPerUnit, ky = mPerUnit;
    var keep = new bool[n]; keep[0] = keep[n-1] = true;
    var stack = new Stack<int[]>(); stack.Push(new[] { 0, n - 1 });
    while (stack.Count > 0) {
      var s = stack.Pop(); int a = s[0], z = s[1]; if (z - a < 2) continue;
      double ax = lon[a] * kx, ay = lat[a] * ky, zx = lon[z] * kx, zy = lat[z] * ky, dx = zx - ax, dy = zy - ay, l2 = dx * dx + dy * dy;
      double best = -1; int bi = -1;
      for (int i = a + 1; i < z; i++) {
        double px = lon[i] * kx - ax, py = lat[i] * ky - ay, d;
        if (l2 == 0) d = Math.Sqrt(px * px + py * py);
        else { double t = (px * dx + py * dy) / l2; if (t < 0) t = 0; else if (t > 1) t = 1; double qx = px - t * dx, qy = py - t * dy; d = Math.Sqrt(qx * qx + qy * qy); }
        if (d > best) { best = d; bi = i; }
      }
      if (best > tol) { keep[bi] = true; stack.Push(new[] { a, bi }); stack.Push(new[] { bi, z }); }
    }
    var idx = new List<int>(); for (int i = 0; i < n; i++) if (keep[i]) idx.Add(i);
    return idx.ToArray();
  }

  // 範囲の対角 [m]
  public static double Extent(int[] lat, int[] lon, int unit) {
    int a = int.MaxValue, b = int.MinValue, c = int.MaxValue, d = int.MinValue;
    for (int i = 0; i < lat.Length; i++) { a = Math.Min(a, lat[i]); b = Math.Max(b, lat[i]); c = Math.Min(c, lon[i]); d = Math.Max(d, lon[i]); }
    double mPerUnit = 6371000.0 * Math.PI / 180.0 / unit, kx = Math.Cos(lat[0] / (double)unit * Math.PI / 180.0);
    double h = (b - a) * mPerUnit, w = (d - c) * mPerUnit * kx; return Math.Sqrt(h * h + w * w);
  }

  // 端がつながっている線をつなぐ（同じレイヤの中の種類どうし。端の点に 2 本だけが来ているときだけつなぐ。分かれ道はそのまま）
  public static List<object[]> Merge(List<object[]> lines) {   // object[] = { int[] lat, int[] lon, int kind }
    var ends = new Dictionary<long, List<int>>();
    Func<int, int, long> key = (la, lo) => ((long)la << 32) ^ (uint)lo;
    for (int i = 0; i < lines.Count; i++) {
      var la = (int[])lines[i][0]; var lo = (int[])lines[i][1]; int n = la.Length;
      foreach (var k in new[] { key(la[0], lo[0]), key(la[n-1], lo[n-1]) }) { List<int> l; if (!ends.TryGetValue(k, out l)) ends[k] = l = new List<int>(); l.Add(i); }
    }
    var used = new bool[lines.Count]; var res = new List<object[]>();
    for (int i = 0; i < lines.Count; i++) {
      if (used[i]) continue; used[i] = true; int kind = (int)lines[i][2];
      var la = new List<int>((int[])lines[i][0]); var lo = new List<int>((int[])lines[i][1]);
      for (int side = 0; side < 2; side++) {
        while (true) {
          long k = side == 0 ? key(la[la.Count-1], lo[lo.Count-1]) : key(la[0], lo[0]);
          var l = ends[k]; if (l.Count != 2) break;
          int j = l[0] == i || used[l[0]] ? l[1] : l[0]; if (used[j] || (int)lines[j][2] != kind) break;
          var ja = (int[])lines[j][0]; var jo = (int[])lines[j][1]; int jn = ja.Length;
          bool jStartHere = key(ja[0], jo[0]) == k;
          var na = new List<int>(ja); var no = new List<int>(jo);
          if (side == 0) { if (!jStartHere) { na.Reverse(); no.Reverse(); } na.RemoveAt(0); no.RemoveAt(0); la.AddRange(na); lo.AddRange(no); }
          else { if (jStartHere) { na.Reverse(); no.Reverse(); } na.RemoveAt(na.Count-1); no.RemoveAt(no.Count-1); la.InsertRange(0, na); lo.InsertRange(0, no); }
          used[j] = true;
          if (la.Count > 1 && la[0] == la[la.Count-1] && lo[0] == lo[lo.Count-1]) break;   // 輪になった
        }
      }
      res.Add(new object[] { la.ToArray(), lo.ToArray(), kind });
    }
    return res;
  }

  public static void UVarint(Stream s, long v) {
 while (v >= 0x80) { s.WriteByte((byte)((v & 0x7F) | 0x80)); v >>= 7; } s.WriteByte((byte)v); }
  public static void SVarint(Stream s, long v) { UVarint(s, (v << 1) ^ (v >> 63)); }

  // 線 1 本（点は lat[from..to]）を書く。範囲は、最小を前の線の最小からの差、最大を最小からの幅で書く（prev は同じかたまりの前の線の最小。書いたあと更新する）。code は使わない（かたまりの見出しにある）
  public static void WriteLine(Stream s, int code, int kind, int[] lat, int[] lon, int from, int to, int[] prev) {
    int n = to - from; int minLat = int.MaxValue, maxLat = int.MinValue, minLon = int.MaxValue, maxLon = int.MinValue;
    for (int i = from; i < to; i++) { minLat = Math.Min(minLat, lat[i]); maxLat = Math.Max(maxLat, lat[i]); minLon = Math.Min(minLon, lon[i]); maxLon = Math.Max(maxLon, lon[i]); }
    s.WriteByte((byte)kind); UVarint(s, n);
    SVarint(s, minLat - prev[0]); SVarint(s, minLon - prev[1]); UVarint(s, maxLat - minLat); UVarint(s, maxLon - minLon);
    prev[0] = minLat; prev[1] = minLon;
    long pLat = minLat, pLon = minLon;
    for (int i = from; i < to; i++) { SVarint(s, lat[i] - pLat); SVarint(s, lon[i] - pLon); pLat = lat[i]; pLon = lon[i]; }
  }

  public static long Deflated(byte[] b) {
    var ms = new MemoryStream(); using (var z = new System.IO.Compression.DeflateStream(ms, System.IO.Compression.CompressionLevel.Optimal, true)) z.Write(b, 0, b.Length); return ms.Length;
  }
}
"@

$ShapeTypes = @{ 0 = 'なし'; 1 = '点'; 3 = '線'; 5 = '面'; 8 = '複数の点'; 11 = '点(Z)'; 13 = '線(Z)'; 15 = '面(Z)'; 21 = '点(M)'; 23 = '線(M)'; 25 = '面(M)' }

if ($List) {
  foreach ($shp in Get-ChildItem $Src -Filter *.shp | Sort-Object Name) {
    $type = [BitConverter]::ToInt32([System.IO.File]::ReadAllBytes($shp.FullName), 32)
    $rows = [Gm]::ReadDbf([System.IO.Path]::ChangeExtension($shp.FullName, '.dbf'))
    "{0}  形={1}  件数={2}" -f $shp.Name, $ShapeTypes[$type], $rows.Count
    foreach ($k in $rows[0].Keys) {
      $vals = $rows | Group-Object { $_[$k] } | Sort-Object Count -Descending
      $shown = if ($vals.Count -le 8) { ($vals | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ' ' } else { "（$($vals.Count) 種類）" }
      "    {0}: {1}" -f $k, $shown
    }
  }
  return
}

function Num($row, $key) { $v = 0; if ([int]::TryParse($row[$key], [ref]$v) -and $v -ge 0 -and $v -le 255) { $v } else { 0 } }
# 河川の名前が「〜G.」「〜K.」「〜KAWA」「〜GAWA」で終わるか（名前のない線は UNK）
function RiverName($row) { $n = "$($row['nam'])".Trim(); $n -ne 'UNK' -and (($n -split '\s+')[-1] -cin 'G.', 'K.', 'KAWA', 'GAWA') }

# 取り込むレイヤ（種類の番号・名前・ファイル・取る行・レイヤの中の種類）
$Layers = @(
  @{ Code = 1;  Name = 'coastline';      File = 'coastl_jpn';   Keep = { param($r) $true };              Kind = { param($r) Num $r 'acc' } }
  @{ Code = 2;  Name = 'pref_boundary';  File = 'polbndl_jpn';  Keep = { param($r) $r['use'] -eq '26' };   Kind = { param($r) Num $r 'bst' } }
  @{ Code = 3;  Name = 'river';          File = 'riverl_jpn';   Keep = { param($r) (Num $r 'lit') -eq 1 -and (RiverName $r) }; Kind = { param($r) Num $r 'lit' } }
  @{ Code = 4;  Name = 'road';           File = 'roadl_jpn';    Keep = { param($r) (Num $r 'rtt') -in 14, 15, 16 }; Kind = { param($r) Num $r 'rtt' } }
  @{ Code = 5;  Name = 'railway';        File = 'raill_jpn';    Keep = { param($r) $true };              Kind = { param($r) Num $r 'fco' } }
  @{ Code = 6;  Name = 'muni_boundary';  File = 'polbndl_jpn';  Keep = { param($r) $r['use'] -eq '30' };   Kind = { param($r) Num $r 'bst' } }
  @{ Code = 7;  Name = 'ferry';          File = 'ferryl_jpn';   Keep = { param($r) $true };              Kind = { param($r) 0 } }
  @{ Code = 8;  Name = 'dam_line';       File = 'miscl_jpn';    Keep = { param($r) $true };              Kind = { param($r) 0 } }
  @{ Code = 9;  Name = 'inland_water';   File = 'inwatera_jpn'; Keep = { param($r) $true };              Kind = { param($r) Num $r 'hyt' } }
  @{ Code = 10; Name = 'built_up_area';  File = 'builtupa_jpn'; Keep = { param($r) $true };              Kind = { param($r) 0 } }
  @{ Code = 20; Name = 'airport';        File = 'airp_jpn';     Keep = { param($r) $true };              Kind = { param($r) Num $r 'use' } }
  @{ Code = 21; Name = 'port';           File = 'portp_jpn';    Keep = { param($r) $true };              Kind = { param($r) 0 } }
  @{ Code = 22; Name = 'station';        File = 'rstatp_jpn';   Keep = { param($r) $true };              Kind = { param($r) 0 } }
  @{ Code = 23; Name = 'dam_point';      File = 'miscp_jpn';    Keep = { param($r) $true };              Kind = { param($r) 0 } }
  @{ Code = 24; Name = 'city';           File = 'builtupp_jpn'; Keep = { param($r) $true };              Kind = { param($r) 0 } }
)
# 段（使う R1 の下限 [m]・許す差 [m]）
$Levels = @(
  @{ MinR1 = 0;      Tol = 0.0;   MinExtent = 0.0;   DenseMinExtent = 0.0 }
  @{ MinR1 = 25000;  Tol = 70.0;  MinExtent = 0.0;   DenseMinExtent = 0.0 }
  @{ MinR1 = 50000;  Tol = 150.0; MinExtent = 300.0; DenseMinExtent = 1000.0 }
  @{ MinR1 = 100000; Tol = 500.0; MinExtent = 500.0; DenseMinExtent = 4000.0 }
)
$Unit = 10000

# 読む（レイヤごとに、線の部分（単位の整数）と点）
$data = foreach ($layer in $Layers) {
  $recs = [Gm]::ReadShp((Join-Path $Src "$($layer.File).shp"))
  $rows = [Gm]::ReadDbf((Join-Path $Src "$($layer.File).dbf"))
  if ($recs.Count -ne $rows.Count) { throw "$($layer.File): .shp と .dbf の件数が違う" }
  $lines = [System.Collections.Generic.List[object[]]]::new(); $points = [System.Collections.Generic.List[object]]::new(); $kept = 0
  for ($i = 0; $i -lt $recs.Count; $i++) {
    if (-not (& $layer.Keep $rows[$i])) { continue }
    $kept++; $kind = & $layer.Kind $rows[$i]; $r = $recs[$i]
    if ($r.Type -in 1, 11, 21) {
      $name = if ($rows[$i].ContainsKey('nam') -and $rows[$i]['nam'] -ne 'UNK') { $rows[$i]['nam'] } else { '' }
      $points.Add(@{ Kind = $kind; Lat = [int][math]::Round($r.Y * $Unit); Lon = [int][math]::Round($r.X * $Unit); Name = $name })
    } else {
      foreach ($xy in $r.Parts) {
        $lon = $null; $lat = [Gm]::ToUnits($xy, $Unit, [ref]$lon)
        if ($lat.Length -ge 2) { $lines.Add([object[]]@($lat, $lon, [int]$kind)) }
      }
    }
  }
  # 線のレイヤは、端がつながっている線をつなぐ（面の輪郭はそのまま）
  $isArea = $recs.Count -gt 0 -and $recs[0].Type -in 5, 15, 25
  $merged = if ($lines.Count -gt 0 -and -not $isArea) { [Gm]::Merge($lines) } else { $lines }
  $lineObjs = [System.Collections.Generic.List[object]]::new()
  foreach ($m in $merged) { $lineObjs.Add(@{ Lat = [int[]]$m[0]; Lon = [int[]]$m[1]; Kind = [int]$m[2] }) }
  # 近い線が続くように並べる（線の最初の点の 0.1 度の升目で、南から北へ・西から東へ）。見出しの差が小さくなる
  $sorted = $lineObjs | Sort-Object { [math]::Floor($_.Lat[0] / ($Unit / 10)) }, { $_.Lon[0] }
  @{ Layer = $layer; Lines = $lineObjs; Sorted = @($sorted); Points = $points; Kept = $kept; Parts = $lines.Count }
}

$buf = [System.IO.MemoryStream]::new()
function Write-Bytes([byte[]]$bytes) { $buf.Write($bytes, 0, $bytes.Length) }
Write-Bytes ([System.Text.Encoding]::ASCII.GetBytes('NHBM'))
$buf.WriteByte(2)
Write-Bytes ([BitConverter]::GetBytes([int]$Unit))
$buf.WriteByte([byte]$Layers.Count)
foreach ($layer in $Layers) { $nb = [System.Text.Encoding]::UTF8.GetBytes($layer.Name); $buf.WriteByte([byte]$layer.Code); $buf.WriteByte([byte]$nb.Length); Write-Bytes $nb }

# 段ごとの線（レイヤの順）。レイヤごと・段ごとの点の数とバイト数を数える
$stats = @{}
$kindStats = @{}
$buf.WriteByte([byte]$Levels.Count)
for ($lv = 0; $lv -lt $Levels.Count; $lv++) {
  $level = $Levels[$lv]
  $tmp = [System.IO.MemoryStream]::new(); $count = 0; $segCount = 0
  foreach ($d in $data) {
    # かたまり（段 × レイヤ）: 見出し（種類の番号 u8・線の数 u32・バイト数 u32）のあとに線。前の線の最小はかたまりごとに 0 から
    $segBuf = [System.IO.MemoryStream]::new(); $prev = [int[]]@(0, 0)
    $start = $tmp.Length; $pts = 0; $n0 = $count
    foreach ($ln in $d.Sorted) {
      $lat = $ln.Lat; $lon = $ln.Lon
      if ($level.Tol -gt 0) {
        $idx = [Gm]::Simplify($lat, $lon, $level.Tol, $Unit)
        $lat = [int[]]::new($idx.Length); $lon = [int[]]::new($idx.Length); for ($q = 0; $q -lt $idx.Length; $q++) { $lat[$q] = $ln.Lat[$idx[$q]]; $lon[$q] = $ln.Lon[$idx[$q]] }
        $minExt = if ($d.Layer.Code -in 3, 4, 5, 6) { $level.DenseMinExtent } else { $level.MinExtent }
        if ($lat.Length -lt 2 -or ($minExt -gt 0 -and [Gm]::Extent($lat, $lon, $Unit) -lt $minExt)) { continue }
      }
      $pts += $lat.Length
      $before = $segBuf.Length
      for ($s = 0; $s -lt $lat.Length - 1; $s += $ChunkPoints - 1) {
        $e = [math]::Min($s + $ChunkPoints, $lat.Length)
        [Gm]::WriteLine($segBuf, $d.Layer.Code, $ln.Kind, $lat, $lon, $s, $e, $prev); $count++
      }
      # レイヤの中の種類ごとの線の数・点の数・バイト数（レポート用）
      $kk = "$($d.Layer.Name),$($ln.Kind)"
      if (-not $kindStats.ContainsKey($kk)) { $kindStats[$kk] = @{ Lines = @(0, 0, 0, 0); Points = @(0, 0, 0, 0); Bytes = @(0, 0, 0, 0) } }
      $kindStats[$kk].Lines[$lv]++; $kindStats[$kk].Points[$lv] += $lat.Length; $kindStats[$kk].Bytes[$lv] += $segBuf.Length - $before
    }
    if ($count -gt $n0) {
      $tmp.WriteByte([byte]$d.Layer.Code)
      $tmp.Write([BitConverter]::GetBytes([uint32]($count - $n0)), 0, 4)
      $tmp.Write([BitConverter]::GetBytes([uint32]$segBuf.Length), 0, 4)
      $segBuf.WriteTo($tmp); $segCount++
    }
    $key = "$($d.Layer.Name)"
    if (-not $stats.ContainsKey($key)) { $stats[$key] = @{ Bytes = @(0, 0, 0, 0); Points = @(0, 0, 0, 0); Lines = @(0, 0, 0, 0); Seg = [System.IO.MemoryStream]::new() } }
    $stats[$key].Bytes[$lv] = $tmp.Length - $start; $stats[$key].Points[$lv] = $pts; $stats[$key].Lines[$lv] = $count - $n0
    if ($tmp.Length -gt $start) { $stats[$key].Seg.Write($tmp.GetBuffer(), [int]$start, [int]($tmp.Length - $start)) }
  }
  Write-Bytes ([BitConverter]::GetBytes([int]$level.MinR1))
  $buf.WriteByte([byte]$segCount)
  Write-Bytes $tmp.ToArray()
}

# 点
$allPoints = foreach ($d in $data) { foreach ($p in $d.Points) { @{ Code = $d.Layer.Code; Name = $d.Layer.Name; P = $p } } }
Write-Bytes ([BitConverter]::GetBytes([uint32]@($allPoints).Count))
$pp = [int[]]@(0, 0)
foreach ($ap in $allPoints) {
  $start = $buf.Length
  $buf.WriteByte([byte]$ap.Code); $buf.WriteByte([byte]$ap.P.Kind)
  [Gm]::SVarint($buf, $ap.P.Lat - $pp[0]); [Gm]::SVarint($buf, $ap.P.Lon - $pp[1]); $pp[0] = $ap.P.Lat; $pp[1] = $ap.P.Lon
  $nb = [System.Text.Encoding]::UTF8.GetBytes($ap.P.Name); [Gm]::UVarint($buf, $nb.Length); Write-Bytes $nb
  $key = $ap.Name
  if (-not $stats.ContainsKey($key)) { $stats[$key] = @{ Bytes = @(0, 0, 0, 0); Points = @(0, 0, 0, 0); Lines = @(0, 0, 0, 0); Seg = [System.IO.MemoryStream]::new(); PBytes = 0; PCount = 0 } }
  $stats[$key].PBytes += $buf.Length - $start; $stats[$key].PCount++
  $stats[$key].Seg.Write($buf.GetBuffer(), [int]$start, [int]($buf.Length - $start))
}

$dir = Split-Path $Out -Parent
if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory $dir | Out-Null }
[System.IO.File]::WriteAllBytes((Join-Path (Get-Location) $Out), $buf.ToArray())

# レイヤごとの数と大きさ（圧縮は apk の中の大きさの目安。レイヤごとに別に圧縮した値）
$table = foreach ($d in $data) {
  $st = $stats[$d.Layer.Name]
  [pscustomobject]@{
    code = $d.Layer.Code; layer = $d.Layer.Name; records = $d.Kept; merged = $d.Lines.Count
    pts0 = $st.Points[0]; pts1 = $st.Points[1]; pts2 = $st.Points[2]; pts3 = $st.Points[3]
    lines0 = $st.Lines[0]; lines1 = $st.Lines[1]; lines2 = $st.Lines[2]; lines3 = $st.Lines[3]
    bytes0 = $st.Bytes[0]; bytes1 = $st.Bytes[1]; bytes2 = $st.Bytes[2]; bytes3 = $st.Bytes[3]
    points = if ($st.ContainsKey('PCount')) { $st.PCount } else { 0 }; pbytes = if ($st.ContainsKey('PBytes')) { $st.PBytes } else { 0 }
    deflated = [Gm]::Deflated($st.Seg.ToArray())
  }
}
$table | Format-Table code, layer, records, merged, pts0, pts1, pts2, pts3, bytes0, bytes1, bytes2, bytes3, points, pbytes, deflated -AutoSize | Out-String -Width 300 | Write-Host
$table | ConvertTo-Csv -NoTypeInformation | Set-Content -Encoding utf8 (Join-Path ([System.IO.Path]::GetTempPath()) 'gm2basemap_stats.csv')
# レイヤの中の種類ごと（線のレイヤ。つないだあとの線の数、点の数・バイト数は段 0 / 1 / 2 / 3）
$kinds = foreach ($k in $kindStats.Keys | Sort-Object) {
  $ks = $kindStats[$k]; $layerName, $kind = $k -split ','
  [pscustomobject]@{ layer = $layerName; kind = [int]$kind; lines0 = $ks.Lines[0]; pts0 = $ks.Points[0]; pts1 = $ks.Points[1]; pts2 = $ks.Points[2]; pts3 = $ks.Points[3]
    bytes0 = $ks.Bytes[0]; bytes1 = $ks.Bytes[1]; bytes2 = $ks.Bytes[2]; bytes3 = $ks.Bytes[3] }
}
$kinds | ConvertTo-Csv -NoTypeInformation | Set-Content -Encoding utf8 (Join-Path ([System.IO.Path]::GetTempPath()) 'gm2basemap_kinds.csv')
"書いた: $Out（$($buf.Length) バイト、圧縮の目安 $([Gm]::Deflated($buf.ToArray())) バイト）"
