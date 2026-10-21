<#
.SYNOPSIS
  国土地理院「地球地図日本」（Shapefile）の線・面・点のレイヤを、アプリの assets に置く背景地図のファイル（basemap.bin）に変換する。

.DESCRIPTION
  使い方（PowerShell 7）:
    1. 「地球地図日本」第2.2版の全レイヤの zip を取ってきて、mapdata/ に展開する（mapdata/ は git 管理外）。
         https://www1.gsi.go.jp/geowww/globalmap-gsi/download/data/gm-japan/gm-jpn-all_u_2_2.zip
         → mapdata/gm-jpn-all_u_2_2/coastl_jpn.shp など
       山頂は、国土地理院「日本の山岳標高一覧（1003山）」の CSV を mapdata/1003zan20260331.csv に置く（列: 連番, 索引番号, 山名,
       山名よみ, 標高値(m), 種別, 都道府県, 緯度, 経度。Shift_JIS）
    2. リポジトリの一番上で実行する:
         pwsh tools/gm2basemap.ps1            （変換して app/src/main/assets/basemap.bin を書く。レイヤごとの大きさも出す）
         pwsh tools/gm2basemap.ps1 -List      （入っているレイヤの一覧を出すだけ。書かない）

  取り込むもの（SPEC.md §8。種類の番号はアプリの core/io/BaseMapFile.kt の MapLayer と同じ）:
    線  1 海岸線（coastl）  2 県境（polbndl の use = 26）  6 市区町村境（polbndl の use = 30）
        3 河川（riverl）  4 道路（roadl）  5 鉄道（raill）  7 フェリーの航路（ferryl）
    面  9 湖沼など（inwatera）  … 塗らずに、輪郭を線として入れる
    点  20 空港（airp） … 名前も入れる
        25 山頂（地球地図ではなく、国土地理院「日本の山岳標高一覧（1003山）」の CSV。20261021-D02）… 名前（山名）も入れる。
           種類 = 標高 ÷ 50 m（下の 7 ビット）＋ 目印になる山の印 0x80（下の $PeakLandmarkMinM・$PeakLandmarkMinIsoKm）
    入れないもの: 市街地の面（builtupa。前の 10）、港（portp。21）、駅（rstatp。22）、ダム・堰の点（miscp。23）、都市（builtupp。24）（20261020-D01）、
    ダム・堰の線（miscl。8。20261020-D02）。
    番号は使わずに空けておく（古いファイルを読むアプリは、知らない番号を飛ばす）。駅（rstatp）は、新幹線を見分ける手がかりにだけ使う。
    行政区域の面（polbnda）は、境の線と同じ形なので入れない。
    線・点ごとに「レイヤの中の種類」（0〜255）を持つ。道路は rtt、鉄道は fco（新幹線は 0x10・山形と秋田は 0x20 を足す）、空港は use、海岸線は acc。河川・湖沼などは大きさの段（下の「段」の説明）。
    道路は rtt が 16（高速道路）・14（国道）・15（県道。主要地方道など）の線だけを入れる（データにはこの 3 つしかない）。
    高速道路のうち、端がつながるまとまりの長さが 20 km 未満のものは、種類を 17（その他の自動車専用道路）にする。
    アプリは種類ごと（高速道路・その他の自動車専用道路・国道・県道）に、設定の縮尺で描く。
    河川は、1 級河川を見分ける値がないので、名前のある川（nam の最後の語が G. / K. / KAWA / GAWA）の水系を入れる（下の「段」の説明）。
    名前のない線は、名前のある川どうしの切れ目をつなぐ区間だけを入れる（20261018-D05）。
    鉄道は全部入れる。新幹線を見分ける値はないので、新幹線の駅（rstatp）どうしを結ぶカーブの少ない道から見分ける（20261019-D02。下の Rail）。
  座標系は緯度経度（ITRF94 / JGD2000）。GPS（WGS84）との差は 1/100万の地図の精度より十分小さいので、直さずに使う。
  ライブラリは使わない（PowerShell と、その中の .NET の標準の機能だけ。重い計算は下の C# で行う）。

  縮尺の段に合わせて、点を間引いた線を前もって作る（間引きは Douglas–Peucker 法。距離は東西・南北の m）:
    段 0（詳細用。R1 25 km 未満）: 間引かない
    段 1（R1 25 km 以上用）       : 許す差 70 m（R1 25 km の画面で約 1 px）
    段 2（R1 50 km 以上用）       : 許す差 150 m（R1 50 km の画面で約 1 px）。範囲の対角が 300 m 未満の線、
                                    鉄道・市区町村境は 1 km（約 7 px）未満の線を入れない
    段 3（R1 100 km 以上用）      : 許す差 500 m（R1 100 km の画面で約 2 px）。範囲の対角が 500 m 未満の線、
                                    鉄道・市区町村境は 4 km（約 14 px）未満の線を入れない（点が多く、広域では細かすぎるため）
    道路・河川・湖沼などは、この「短い線を入れない」決まりから外す（道路はインターチェンジなどで短く分かれていて、
    外さないと広域で途切れて見える。河川・湖沼などは下の大きさの段で絞る。20261018-D04）。
    河川・湖沼などは、大きさの段（$RiverClassKm・$WaterClassKm2）で、段 2 では中・大、段 3 では大だけを入れる:
      河川（水系の長さ。名前によらず端がつながる名前のある川と、切れ目をつなぐ名前のない区間。湖・沼の中は数えない）:
        どの段も 5 km 未満は入れない、段 2 は 30 km 未満、段 3 は 50 km 未満を入れない
      湖沼など（面の面積）: どの段も 0.1 km² 未満は入れない、段 2 は 1 km² 未満、段 3 は 10 km² 未満を入れない
    湖沼などは外の輪と 1 km² 以上の穴（湖の島・干拓地）だけを入れ（小さな穴 = 中洲などは入れない）、となりの面と重なる辺も入れない。
    幅のある川: 川の面（hyt = 1）は段 0 だけに入れ、R1 10 km 以上では代わりに川の中心の線（lit = 2）を描く（種類の 0x10。20261018-D05）。
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
.PARAMETER PeakCsv
  山頂の CSV（国土地理院「日本の山岳標高一覧（1003山）」。Shift_JIS）。既定は mapdata/1003zan20260331.csv
.PARAMETER ChunkPoints
  1 本の線の点の数の上限。既定は 64
.PARAMETER List
  入っているレイヤの一覧（形・数・属性）を出すだけにする
#>
param(
  [string]$Src = 'mapdata/gm-jpn-all_u_2_2',
  [string]$Out = 'app/src/main/assets/basemap.bin',
  [int]$ChunkPoints = 64,
  [string]$PeakCsv = 'mapdata/1003zan20260331.csv',
  [switch]$List
)
$ErrorActionPreference = 'Stop'
[System.Text.Encoding]::RegisterProvider([System.Text.CodePagesEncodingProvider]::Instance)

Add-Type -TypeDefinition @"
using System; using System.IO; using System.Collections.Generic; using System.Text; using System.Linq;

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

  // 線の長さ [km]（部分ごとの点 [x0, y0, x1, y1, ...]。東西は 2 点の中ほどの緯度で縮める）
  public static double Km(double[] xy) {
    double km = 0, r = 6371.0088 * Math.PI / 180.0;
    for (int i = 2; i < xy.Length; i += 2) {
      double k = Math.Cos((xy[i-1] + xy[i+1]) / 2 * Math.PI / 180.0);
      double dx = (xy[i] - xy[i-2]) * k * r, dy = (xy[i+1] - xy[i-1]) * r; km += Math.Sqrt(dx * dx + dy * dy);
    }
    return km;
  }

  // 輪が外の輪か（シェープファイルでは外の輪は時計回り、穴（中洲など）は反時計回り）
  public static bool IsOuter(double[] xy) {
    double s = 0; for (int i = 2; i < xy.Length; i += 2) s += (xy[i] - xy[i-2]) * (xy[i+1] + xy[i-1]);
    return s > 0;   // 経度を右、緯度を上にとると、時計回りで正
  }

  // 面の面積 [km2]（外の輪 − 穴）。東西はレコードの最初の点の緯度で縮める（全部の辺で同じ数にしないと式が成り立たない）
  public static double AreaKm2(List<double[]> parts) {
    if (parts.Count == 0 || parts[0].Length < 2) return 0;
    double yref = parts[0][1], kref = Math.Cos(yref * Math.PI / 180.0), signed = 0;
    foreach (var r in parts) for (int i = 2; i < r.Length; i += 2) signed += (r[i] - r[i-2]) * kref * ((r[i+1] - yref) + (r[i-1] - yref)) / 2;
    double m = 6371.0088 * Math.PI / 180.0; return Math.Abs(signed) * m * m;
  }

  // 同じ名前の線を、端の点が同じものどうしでまとめ、まとまりごとの長さの合計 [km] をレコードごとに返す（use が false のレコードは 0）
  public static double[] ConnectedKm(List<ShpRecord> recs, string[] names, bool[] use) {
    int n = recs.Count; var p = new int[n]; for (int i = 0; i < n; i++) p[i] = i;
    Func<int, int> find = null; find = x => { while (p[x] != x) { p[x] = p[p[x]]; x = p[x]; } return x; };
    var ends = new Dictionary<string, int>();
    for (int i = 0; i < n; i++) {
      if (!use[i]) continue;
      foreach (var r in recs[i].Parts) {
        if (r.Length < 4) continue;
        foreach (var e in new[] { 0, r.Length - 2 }) {
          string k = names[i] + "|" + Math.Round(r[e] * 1e6) + "|" + Math.Round(r[e+1] * 1e6);
          int j; if (ends.TryGetValue(k, out j)) { int a = find(i), b = find(j); if (a != b) p[a] = b; } else ends[k] = i;
        }
      }
    }
    var sum = new Dictionary<int, double>(); var km = new double[n];
    for (int i = 0; i < n; i++) { if (!use[i]) continue; double l = 0; foreach (var r in recs[i].Parts) l += Km(r); km[i] = l; int g = find(i); double v; sum.TryGetValue(g, out v); sum[g] = v + l; }
    var res = new double[n]; for (int i = 0; i < n; i++) if (use[i]) res[i] = sum[find(i)];
    return res;
  }

  // 線の曲がった角度の合計 [度]（点ごとの向きの変わりの絶対値の合計。部分ごと）
  public static double TurnDeg(double[] xy) {
    double r = 6371008.8 * Math.PI / 180, turn = 0; double? prev = null;
    for (int i = 2; i < xy.Length; i += 2) {
      double k = Math.Cos((xy[i-1] + xy[i+1]) / 2 * Math.PI / 180), dx = (xy[i] - xy[i-2]) * k * r, dy = (xy[i+1] - xy[i-1]) * r;
      if (dx == 0 && dy == 0) continue;
      double h = Math.Atan2(dx, dy) * 180 / Math.PI;
      if (prev.HasValue) { double a = h - prev.Value; while (a > 180) a -= 360; while (a < -180) a += 360; turn += Math.Abs(a); }
      prev = h;
    }
    return turn;
  }

  // 端の点がつながるまとまりごとの、長さ [km] と、1 km あたりの曲がった角度 [度/km]（まとまりの角度の合計 ÷ 長さ）。
  // 戻り値: { レコードごとのまとまりの長さ, レコードごとのまとまりの 1 km あたりの角度 }（use が false のレコードは 0）
  public static double[][] ComponentCurvature(List<ShpRecord> recs, bool[] use) {
    int n = recs.Count; var p = new int[n]; for (int i = 0; i < n; i++) p[i] = i;
    Func<int, int> find = null; find = x => { while (p[x] != x) { p[x] = p[p[x]]; x = p[x]; } return x; };
    var ends = new Dictionary<string, int>();
    for (int i = 0; i < n; i++) { if (!use[i]) continue;
      foreach (var r in recs[i].Parts) { if (r.Length < 4) continue;
        foreach (var e in new[] { 0, r.Length - 2 }) { string k = Math.Round(r[e] * 1e6) + "|" + Math.Round(r[e+1] * 1e6); int j;
          if (ends.TryGetValue(k, out j)) { int a = find(i), b = find(j); if (a != b) p[a] = b; } else ends[k] = i; } } }
    var km = new Dictionary<int, double>(); var turn = new Dictionary<int, double>();
    for (int i = 0; i < n; i++) { if (!use[i]) continue; int g = find(i); double l = 0, t = 0; foreach (var r in recs[i].Parts) { l += Km(r); t += TurnDeg(r); }
      double v; km.TryGetValue(g, out v); km[g] = v + l; turn.TryGetValue(g, out v); turn[g] = v + t; }
    var resKm = new double[n]; var resDeg = new double[n];
    for (int i = 0; i < n; i++) if (use[i]) { int g = find(i); resKm[i] = km[g]; resDeg[i] = km[g] > 0 ? turn[g] / km[g] : 0; }
    return new[] { resKm, resDeg };
  }

  // 網の枝のうち、カーブの多いもの。use のレコード（の部分）を線、端の点を点とする。
  // 分かれ目（3 本以上が来る点）v ごとに、v を通らずにつながる線のかたまりに分け、いちばん長いかたまりを網、
  // ほかを v にぶら下がる「枝」とする（枝の中に小さな輪や、さらに枝があってもよい。枝の中の枝も枝）。
  // 枝の長さが minKm 以上 maxKm 未満で、1 km あたりの曲がった角度（枝の角度の合計 ÷ 長さ）が degPerKm 以上なら、枝の全部のレコードに印を付ける。
  // 両端とも網につながる区間（通り道）は、どの v で分けても網の側に入るので、印は付かない
  public static bool[] CurvyBranches(List<ShpRecord> recs, bool[] use, double minKm, double maxKm, double degPerKm) {
    var eRec = new List<int>(); var eA = new List<int>(); var eB = new List<int>(); var eKm = new List<double>(); var eTurn = new List<double>();
    var node = new Dictionary<string, int>(); var inc = new List<List<int>>();
    Func<double, double, int> nid = (x, y) => { string k = Math.Round(x * 1e6) + "|" + Math.Round(y * 1e6); int v;
      if (!node.TryGetValue(k, out v)) { v = node.Count; node[k] = v; inc.Add(new List<int>()); } return v; };
    for (int i = 0; i < recs.Count; i++) { if (!use[i]) continue;
      foreach (var r in recs[i].Parts) { if (r.Length < 4) continue;
        int a = nid(r[0], r[1]), b = nid(r[r.Length - 2], r[r.Length - 1]), e = eRec.Count;
        eRec.Add(i); eA.Add(a); eB.Add(b); eKm.Add(Km(r)); eTurn.Add(TurnDeg(r)); inc[a].Add(e); if (b != a) inc[b].Add(e); } }
    var mark = new bool[recs.Count];
    for (int v = 0; v < inc.Count; v++) {
      int deg = 0; foreach (var e in inc[v]) deg += eA[e] == eB[e] ? 2 : 1;
      if (deg < 3) continue;
      var group = new Dictionary<int, int>(); var groups = new List<List<int>>();
      foreach (var e0 in inc[v]) { if (group.ContainsKey(e0)) continue;
        var g = new List<int>(); var todo = new Queue<int>(); todo.Enqueue(e0); group[e0] = groups.Count;
        while (todo.Count > 0) { int e = todo.Dequeue(); g.Add(e);
          foreach (var n in new[] { eA[e], eB[e] }) { if (n == v) continue; foreach (var f in inc[n]) if (!group.ContainsKey(f)) { group[f] = groups.Count; todo.Enqueue(f); } } }
        groups.Add(g); }
      if (groups.Count < 2) continue;
      var km = groups.Select(g => g.Sum(e => eKm[e])).ToList(); int net = km.IndexOf(km.Max());
      for (int k = 0; k < groups.Count; k++) { if (k == net || km[k] < minKm || km[k] >= maxKm) continue;
        if (groups[k].Sum(e => eTurn[e]) / km[k] < degPerKm) continue;
        foreach (var e in groups[k]) mark[eRec[e]] = true; }
    }
    return mark;
  }

  // 点が面の中か（偶奇の規則。穴も含めて全部の輪を数える）
  static bool Inside(List<double[]> poly, double x, double y) {
    bool inside = false;
    foreach (var r in poly) { int n = r.Length / 2;
      for (int i = 0, j = n - 1; i < n; j = i++) { double xi = r[2*i], yi = r[2*i+1], xj = r[2*j], yj = r[2*j+1];
        if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) inside = !inside; } }
    return inside;
  }

  // 線ごとに、点の半分以上が入っている面の種類（hyt）を返す（どの面にも半分以上入っていなければ 0）
  public static int[] ContainerHyt(List<ShpRecord> lines, List<ShpRecord> polys, int[] hyt) {
    var box = new List<double[]>();
    foreach (var p in polys) { double a = 1e9, b = 1e9, c = -1e9, d = -1e9; foreach (var r in p.Parts) for (int i = 0; i < r.Length; i += 2) { a = Math.Min(a, r[i]); c = Math.Max(c, r[i]); b = Math.Min(b, r[i+1]); d = Math.Max(d, r[i+1]); } box.Add(new[] { a, b, c, d }); }
    var res = new int[lines.Count];
    for (int li = 0; li < lines.Count; li++) {
      var cnt = new Dictionary<int, int>(); int tot = 0;
      foreach (var r in lines[li].Parts) for (int i = 0; i < r.Length; i += 2) {
        tot++; double x = r[i], y = r[i+1];
        for (int k = 0; k < polys.Count; k++) { var bb = box[k]; if (x < bb[0] || x > bb[2] || y < bb[1] || y > bb[3]) continue; if (Inside(polys[k].Parts, x, y)) { int v; cnt.TryGetValue(k, out v); cnt[k] = v + 1; break; } }
      }
      int best = -1, bn = 0; foreach (var kv in cnt) if (kv.Value > bn) { best = kv.Key; bn = kv.Value; }
      res[li] = (best >= 0 && bn * 2 >= tot) ? hyt[best] : 0;
    }
    return res;
  }

  // 水系: 名前のある川（named）を、名前によらず端がつながるものどうしでまとめ、名前のない線（lit = 1・2）で切れている所は、
  // 名前のない線をたどる最短の道（湖・沼の中のつなぎの線は 0 km と数える）が maxBridgeKm 以下なら、その道の線も入れる。
  // 戻り値: レコードごとの、入っている水系の長さ [km]（湖・沼の中のつなぎの線は数えない）。水系に入らないレコードは 0
  // 点から面の輪郭までの距離 [m]（面の中なら 0）
  static double DistToPolyM(List<double[]> poly, double x, double y) {
    if (Inside(poly, x, y)) return 0;
    double k = Math.Cos(y * Math.PI / 180.0), m = 6371008.8 * Math.PI / 180.0, best = double.MaxValue;
    foreach (var r in poly) for (int i = 2; i < r.Length; i += 2) {
      double ax = (r[i-2] - x) * k * m, ay = (r[i-1] - y) * m, bx = (r[i] - x) * k * m, by = (r[i+1] - y) * m;
      double dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy, t = l2 == 0 ? 0 : -(ax * dx + ay * dy) / l2;
      if (t < 0) t = 0; else if (t > 1) t = 1;
      double qx = ax + t * dx, qy = ay + t * dy; best = Math.Min(best, Math.Sqrt(qx * qx + qy * qy));
    }
    return best;
  }

  public static double[] WaterSystems(List<ShpRecord> recs, int[] lit, bool[] named, int[] cont, double maxBridgeKm) {
    return WaterSystems(recs, lit, named, cont, maxBridgeKm, new List<ShpRecord>(), 0);
  }

  // lakes: 湖・沼・貯水池の面。川の端の点がこの面の中か、輪郭から lakeTolM 以内なら、その湖につながっているとみなす
  // （湖の中につなぎの線がないとき（琵琶湖など）、湖に入る川と出る川を、湖を通ってつなぐため。湖を通る道は 0 km）
  public static double[] WaterSystems(List<ShpRecord> recs, int[] lit, bool[] named, int[] cont, double maxBridgeKm, List<ShpRecord> lakes, double lakeTolM) {
    int n = recs.Count;
    var km = new double[n]; var w = new double[n]; var use = new bool[n];
    for (int i = 0; i < n; i++) {
      use[i] = lit[i] == 1 || lit[i] == 2;
      foreach (var r in recs[i].Parts) km[i] += Km(r);
      w[i] = (lit[i] == 2 && (cont[i] == 4 || cont[i] == 13)) ? 0 : km[i];
    }
    // 端の点（部分ごとの最初と最後）→ 点の番号
    var nodeOf = new Dictionary<string, int>(); var ends = new List<int>[n]; var adj = new List<List<int>>();
    var nx = new List<double>(); var ny = new List<double>();
    for (int i = 0; i < n; i++) {
      ends[i] = new List<int>(); if (!use[i]) continue;
      foreach (var r in recs[i].Parts) { if (r.Length < 4) continue;
        foreach (var e in new[] { 0, r.Length - 2 }) {
          string k = Math.Round(r[e] * 1e6) + "|" + Math.Round(r[e+1] * 1e6); int id;
          if (!nodeOf.TryGetValue(k, out id)) { id = adj.Count; nodeOf[k] = id; adj.Add(new List<int>()); nx.Add(r[e]); ny.Add(r[e+1]); }
          if (!ends[i].Contains(id)) { ends[i].Add(id); adj[id].Add(i); }
        } }
    }
    // 湖の点（番号は adj.Count から）と、川の端の点とのつながり（0 km）
    int real = adj.Count; var virt = new List<List<int>>();
    for (int v = 0; v < real + lakes.Count; v++) virt.Add(new List<int>());
    for (int k = 0; k < lakes.Count; k++) {
      double a = 1e9, b = 1e9, c = -1e9, d = -1e9; foreach (var r in lakes[k].Parts) for (int i = 0; i < r.Length; i += 2) { a = Math.Min(a, r[i]); c = Math.Max(c, r[i]); b = Math.Min(b, r[i+1]); d = Math.Max(d, r[i+1]); }
      double pad = lakeTolM / 80000.0 + 1e-6;
      for (int v = 0; v < real; v++) {
        if (nx[v] < a - pad || nx[v] > c + pad || ny[v] < b - pad || ny[v] > d + pad) continue;
        if (DistToPolyM(lakes[k].Parts, nx[v], ny[v]) <= lakeTolM) { virt[v].Add(real + k); virt[real + k].Add(v); }
      }
    }
    // 名前のある川のまとまり（名前によらず端がつながるもの）
    var p = new int[n]; for (int i = 0; i < n; i++) p[i] = i;
    Func<int, int> find = null; find = x => { while (p[x] != x) { p[x] = p[p[x]]; x = p[x]; } return x; };
    for (int v = 0; v < adj.Count; v++) { int first = -1; foreach (int i in adj[v]) { if (!named[i]) continue; if (first < 0) first = i; else { int a = find(i), b = find(first); if (a != b) p[a] = b; } } }
    var nodeComps = new List<HashSet<int>>(); for (int v = 0; v < adj.Count; v++) { var s = new HashSet<int>(); foreach (int i in adj[v]) if (named[i]) s.Add(find(i)); nodeComps.Add(s); }
    // まとまりごとに、名前のない線を最短でたどって、ほかのまとまりへの道を探す
    var bridge = new bool[n];
    var comps = new HashSet<int>(); for (int i = 0; i < n; i++) if (named[i]) comps.Add(find(i));
    var compNodes = new Dictionary<int, List<int>>(); for (int v = 0; v < adj.Count; v++) foreach (int c in nodeComps[v]) { List<int> l; if (!compNodes.TryGetValue(c, out l)) compNodes[c] = l = new List<int>(); l.Add(v); }
    foreach (int c in comps) {
      var dist = new Dictionary<int, double>(); var prevRec = new Dictionary<int, int>(); var prevNode = new Dictionary<int, int>();
      var pq = new SortedSet<Tuple<double, int>>();
      foreach (int v in compNodes[c]) { dist[v] = 0; pq.Add(Tuple.Create(0.0, v)); }
      var reached = new HashSet<int>();
      while (pq.Count > 0) {
        var top = pq.Min; pq.Remove(top); double d = top.Item1; int u = top.Item2;
        if (d > dist[u]) continue;
        bool other = false;
        if (u < real) foreach (int oc in nodeComps[u]) if (oc != c) { other = true; if (!reached.Contains(oc)) { reached.Add(oc);
              int x = u; while (prevNode.ContainsKey(x)) { if (prevRec[x] >= 0) bridge[prevRec[x]] = true; x = prevNode[x]; } } }
        if (other) continue;   // ほかのまとまりに着いたら、その先へは進まない
        Action<int, int, double> relax = (v, i, nd) => {
          if (nd > maxBridgeKm) return;
          double old; if (!dist.TryGetValue(v, out old) || nd < old) { if (dist.ContainsKey(v)) pq.Remove(Tuple.Create(old, v)); dist[v] = nd; prevRec[v] = i; prevNode[v] = u; pq.Add(Tuple.Create(nd, v)); } };
        if (u < real) foreach (int i in adj[u]) {
          if (named[i] || !use[i]) continue;
          foreach (int v in ends[i]) if (v != u) relax(v, i, d + w[i]);
        }
        foreach (int v in virt[u]) relax(v, -1, d);   // 湖を通る（0 km）
      }
    }
    // 水系 = 名前のある川 ＋ つなぐ線。長さは湖・沼の中のつなぎの線を除いて足す
    var q = new int[n]; for (int i = 0; i < n; i++) q[i] = i;
    Func<int, int> find2 = null; find2 = x => { while (q[x] != x) { q[x] = q[q[x]]; x = q[x]; } return x; };
    for (int v = 0; v < adj.Count; v++) { int first = -1; foreach (int i in adj[v]) { if (!named[i] && !bridge[i]) continue; if (first < 0) first = i; else { int a = find2(i), b = find2(first); if (a != b) q[a] = b; } } }
    var sum = new Dictionary<int, double>();
    for (int i = 0; i < n; i++) if (named[i] || bridge[i]) { int g = find2(i); double s; sum.TryGetValue(g, out s); sum[g] = s + w[i]; }
    var res = new double[n]; for (int i = 0; i < n; i++) if (named[i] || bridge[i]) res[i] = sum[find2(i)];
    return res;
  }

  // 面の輪郭のうち、ほかの面と重なる辺（となりの面との境。2 回描くことになり、水の面の中を横切る線にもなる）を除き、
  // 残りを続いた線に分ける。object[] = { int[] lat, int[] lon, int kind }（輪は最初と最後の点が同じ）
  public static List<object[]> DropSharedEdges(List<object[]> rings) {
    Func<int, int, int, int, string> edge = (a, b, c, d) => {
      long p = ((long)a << 32) ^ (uint)b, q = ((long)c << 32) ^ (uint)d; return p < q ? p + "|" + q : q + "|" + p; };
    var count = new Dictionary<string, int>();
    foreach (var r in rings) { var la = (int[])r[0]; var lo = (int[])r[1];
      for (int i = 1; i < la.Length; i++) { string k = edge(la[i-1], lo[i-1], la[i], lo[i]); int v; count.TryGetValue(k, out v); count[k] = v + 1; } }
    var res = new List<object[]>();
    foreach (var r in rings) {
      var la = (int[])r[0]; var lo = (int[])r[1]; int n = la.Length;
      var shared = new bool[n]; bool any = false;   // shared[i]: 点 i-1 から i への辺がほかの面と重なる
      for (int i = 1; i < n; i++) { shared[i] = count[edge(la[i-1], lo[i-1], la[i], lo[i])] > 1; any |= shared[i]; }
      if (!any) { res.Add(r); continue; }
      // 重なる辺のあとから始めて、輪を 1 周たどる（輪の始まりで切れないように）
      int start = 1; while (!shared[start]) start++;
      var curLa = new List<int>(); var curLo = new List<int>();
      for (int s = 0; s < n - 1; s++) {
        int i = (start + s - 1) % (n - 1) + 1;   // 辺 i（点 i-1 → i）。1..n-1 を start から回る
        if (shared[i]) { if (curLa.Count >= 2) res.Add(new object[] { curLa.ToArray(), curLo.ToArray(), r[2] }); curLa = new List<int>(); curLo = new List<int>(); continue; }
        if (curLa.Count == 0) { curLa.Add(la[i-1]); curLo.Add(lo[i-1]); }
        curLa.Add(la[i]); curLo.Add(lo[i]);
      }
      if (curLa.Count >= 2) res.Add(new object[] { curLa.ToArray(), curLo.ToArray(), r[2] });
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

  // 山頂の孤立度（20261021-D02）: 自分より標高の大きい山頂（一覧の中）までの大円距離 [km]。いちばん高い山は正の無限大
  public static double[] Isolation(double[] lat, double[] lon, double[] elev) {
    int n = lat.Length; var r = new double[n]; double k = Math.PI / 180;
    for (int i = 0; i < n; i++) {
      double best = double.PositiveInfinity;
      for (int j = 0; j < n; j++) {
        if (elev[j] <= elev[i]) continue;
        double dl = (lat[j] - lat[i]) * k, dn = (lon[j] - lon[i]) * k;
        double h = Math.Sin(dl / 2) * Math.Sin(dl / 2) + Math.Cos(lat[i] * k) * Math.Cos(lat[j] * k) * Math.Sin(dn / 2) * Math.Sin(dn / 2);
        double d = 2 * 6371.0088 * Math.Asin(Math.Sqrt(h));
        if (d < best) best = d;
      }
      r[i] = best;
    }
    return r;
  }
}

// 新幹線を見分ける（20261019-D02）。地球地図の鉄道には新幹線の区分がないので、新幹線の駅（rstatp）を手がかりにする。
// 1. 鉄道の線の頂点を点、となりの頂点を辺とするグラフを作る。辺の重み = 長さ × (1 + レコードの 1 km あたりの角度 ÷ curvK)
//    （カーブの少ない線を通りやすくする。複線かどうか（fco）は、新幹線でも単線になっている所があるので使わない）。
//    線の行き止まりの点は、gapM 以内のほかの線の頂点とつなぐ（新幹線のトンネルの線などの継ぎ目のずれ）。
// 2. 駅（snapM 以内の頂点から出る）どうしの重み付きの最短の道を、直線で pairKm 以内の組について出し、駅の最小全域木を作る。
// 3. 木を、木の次数が 2 でない駅で「路線のまとまり」に分ける。道を 200 m ごとにならしてから測った 1 km あたりの角度が
//    miniDeg 以上のまとまりは、在来線の線路を走る新幹線（山形・秋田）とする。
// 4. 道が通った辺に印を付ける（1 = 新幹線、2 = 山形・秋田）。戻り値: [レコード][部分][辺] の印
public static class Rail {
  static List<double> X = new List<double>(), Y = new List<double>();
  static List<List<int[]>> Adj;   // { 相手の点, レコード, 部分, 辺（-1 はつなぎ） }
  static List<List<double>> W;
  static double D(double x0, double y0, double x1, double y1) { double r = 6371008.8 * Math.PI / 180, k = Math.Cos((y0 + y1) / 2 * Math.PI / 180), dx = (x1 - x0) * k * r, dy = (y1 - y0) * r; return Math.Sqrt(dx*dx + dy*dy); }
  static double TurnPerKm(List<double> x, List<double> y, double step) {
    if (x.Count < 2) return 0;
    var rx = new List<double> { x[0] }; var ry = new List<double> { y[0] }; double need = step, len = 0;
    for (int i = 1; i < x.Count; i++) { double d = D(x[i-1], y[i-1], x[i], y[i]); len += d; double at = 0;
      while (d - at >= need) { at += need; double f = at / d; rx.Add(x[i-1] + (x[i] - x[i-1]) * f); ry.Add(y[i-1] + (y[i] - y[i-1]) * f); need = step; }
      need -= (d - at); }
    if (need < step) { rx.Add(x[x.Count-1]); ry.Add(y[y.Count-1]); }
    double t = 0; double? prev = null;
    for (int i = 1; i < rx.Count; i++) { double k = Math.Cos((ry[i-1] + ry[i]) / 2 * Math.PI / 180), dx = (rx[i] - rx[i-1]) * k, dy = ry[i] - ry[i-1]; if (dx == 0 && dy == 0) continue;
      double h = Math.Atan2(dx, dy) * 180 / Math.PI; if (prev.HasValue) { double a = h - prev.Value; while (a > 180) a -= 360; while (a < -180) a += 360; t += Math.Abs(a); } prev = h; }
    return len > 0 ? t / (len / 1000) : 0;
  }
  // 調べた結果（レポート用）: 木の辺 { 駅 a, 駅 b, 道の長さ [m], 200 m でならした 1 km あたりの角度 }、まとまり { 駅の並び, 長さ, 角度, 山形・秋田か }
  public static List<double[]> TreeEdges = new List<double[]>();
  public static List<string> Chains = new List<string>();
  public static int[][][] Shinkansen(List<ShpRecord> recs, List<double[]> stations, double curvK, double gapM, double snapM, double pairKm, double miniDeg) {
    X.Clear(); Y.Clear(); TreeEdges.Clear(); Chains.Clear();
    Adj = new List<List<int[]>>(); W = new List<List<double>>();
    var id = new Dictionary<string, int>();
    Func<double, double, int> node = (x, y) => { string k = Math.Round(x * 1e6) + "|" + Math.Round(y * 1e6); int v;
      if (!id.TryGetValue(k, out v)) { v = X.Count; id[k] = v; X.Add(x); Y.Add(y); Adj.Add(new List<int[]>()); W.Add(new List<double>()); } return v; };
    for (int i = 0; i < recs.Count; i++) {
      double turn = 0, len = 0; foreach (var p in recs[i].Parts) { turn += Gm.TurnDeg(p); len += Gm.Km(p); }
      double f = 1 + Math.Min(len > 0 ? turn / len : 0, 300) / curvK;
      for (int pi = 0; pi < recs[i].Parts.Count; pi++) { var p = recs[i].Parts[pi];
        for (int q = 2; q < p.Length; q += 2) { int a = node(p[q-2], p[q-1]), b = node(p[q], p[q+1]); if (a == b) continue; double d = D(p[q-2], p[q-1], p[q], p[q+1]);
          Adj[a].Add(new[] { b, i, pi, q / 2 - 1 }); W[a].Add(d * f); Adj[b].Add(new[] { a, i, pi, q / 2 - 1 }); W[b].Add(d * f); } } }
    // 行き止まりの点を、近くのほかのレコードの頂点とつなぐ
    var grid = new Dictionary<long, List<int>>(); double cell = 0.01; int n0 = X.Count;
    for (int v = 0; v < n0; v++) { long k = (long)Math.Floor(X[v] / cell) * 100000L + (long)Math.Floor(Y[v] / cell); List<int> l; if (!grid.TryGetValue(k, out l)) grid[k] = l = new List<int>(); l.Add(v); }
    for (int v = 0; v < n0; v++) { if (Adj[v].Count != 1) continue; int rec = Adj[v][0][1]; int best = -1; double bd = gapM;
      long cx = (long)Math.Floor(X[v] / cell), cy = (long)Math.Floor(Y[v] / cell);
      for (long dx = -1; dx <= 1; dx++) for (long dy = -1; dy <= 1; dy++) { List<int> l; if (!grid.TryGetValue((cx + dx) * 100000L + cy + dy, out l)) continue;
        foreach (var u in l) { if (u == v) continue; bool same = false; foreach (var e in Adj[u]) if (e[1] == rec) { same = true; break; } if (same) continue;
          double d = D(X[v], Y[v], X[u], Y[u]); if (d < bd) { bd = d; best = u; } } }
      if (best >= 0) { Adj[v].Add(new[] { best, -1, -1, -1 }); W[v].Add(bd + 1); Adj[best].Add(new[] { v, -1, -1, -1 }); W[best].Add(bd + 1); } }
    // 駅の近くの頂点
    int ns = stations.Count; var snap = new List<int>[ns];
    for (int s = 0; s < ns; s++) { snap[s] = new List<int>(); for (int v = 0; v < X.Count; v++) if (Math.Abs(Y[v] - stations[s][1]) < 0.02 && D(stations[s][0], stations[s][1], X[v], Y[v]) < snapM) snap[s].Add(v); }
    // 駅の組ごとの最短の道（Dijkstra）
    var pairs = new List<Tuple<double, int, int>>(); var paths = new Dictionary<long, List<int[]>>();   // 道: { 点, その点に来た辺の { レコード, 部分, 辺 } }
    int nv = X.Count;
    for (int a = 0; a < ns; a++) { if (snap[a].Count == 0) continue;
      var dist = new double[nv]; var prev = new int[nv]; var prevE = new int[nv][]; for (int v = 0; v < nv; v++) { dist[v] = double.MaxValue; prev[v] = -1; }
      var pq = new SortedSet<Tuple<double, int>>();
      foreach (var v in snap[a]) { dist[v] = D(stations[a][0], stations[a][1], X[v], Y[v]); pq.Add(Tuple.Create(dist[v], v)); }
      double maxCost = pairKm * 3000;
      while (pq.Count > 0) { var t = pq.Min; pq.Remove(t); int u = t.Item2; if (t.Item1 > maxCost) break;
        for (int k = 0; k < Adj[u].Count; k++) { int v = Adj[u][k][0]; double nd = dist[u] + W[u][k];
          if (nd < dist[v]) { if (dist[v] != double.MaxValue) pq.Remove(Tuple.Create(dist[v], v)); dist[v] = nd; prev[v] = u; prevE[v] = Adj[u][k]; pq.Add(Tuple.Create(nd, v)); } } }
      for (int b = a + 1; b < ns; b++) { if (snap[b].Count == 0 || D(stations[a][0], stations[a][1], stations[b][0], stations[b][1]) > pairKm * 1000) continue;
        double best = double.MaxValue; int bv = -1; foreach (var v in snap[b]) { if (dist[v] == double.MaxValue) continue; double c = dist[v] + D(stations[b][0], stations[b][1], X[v], Y[v]); if (c < best) { best = c; bv = v; } }
        if (bv < 0) continue;
        var path = new List<int[]>(); int w = bv; while (w >= 0) { path.Add(new[] { w, prevE[w] == null ? -2 : prevE[w][1], prevE[w] == null ? -2 : prevE[w][2], prevE[w] == null ? -2 : prevE[w][3] }); w = prev[w]; }
        pairs.Add(Tuple.Create(best, a, b)); paths[(long)a * 100000 + b] = path; } }
    // 駅の最小全域木
    var par = new int[ns]; for (int s = 0; s < ns; s++) par[s] = s;
    Func<int, int> root = null; root = x => { while (par[x] != x) x = par[x] = par[par[x]]; return x; };
    var tree = new List<Tuple<int, int>>(); var adjT = new List<int>[ns]; for (int s = 0; s < ns; s++) adjT[s] = new List<int>();
    foreach (var e in pairs.OrderBy(t => t.Item1)) { int ra = root(e.Item2), rb = root(e.Item3); if (ra == rb) continue; par[ra] = rb; tree.Add(Tuple.Create(e.Item2, e.Item3)); adjT[e.Item2].Add(tree.Count - 1); adjT[e.Item3].Add(tree.Count - 1); }
    var eLen = new double[tree.Count]; var eDeg = new double[tree.Count];
    for (int t = 0; t < tree.Count; t++) { var path = paths[(long)tree[t].Item1 * 100000 + tree[t].Item2]; var px = new List<double>(); var py = new List<double>(); double len = 0;
      for (int j = 0; j < path.Count; j++) { px.Add(X[path[j][0]]); py.Add(Y[path[j][0]]); if (j > 0) len += D(px[j-1], py[j-1], px[j], py[j]); }
      eLen[t] = len; eDeg[t] = TurnPerKm(px, py, 200); TreeEdges.Add(new double[] { tree[t].Item1, tree[t].Item2, len, eDeg[t] }); }
    // 路線のまとまり（木の次数が 2 でない駅で切る）
    var edgeMini = new bool[tree.Count]; var usedT = new bool[tree.Count];
    for (int s = 0; s < ns; s++) { if (adjT[s].Count == 2 || adjT[s].Count == 0) continue;
      foreach (int e0 in adjT[s]) { if (usedT[e0]) continue; var list = new List<int>(); var seq = new List<int> { s }; int cur = s, e = e0;
        while (true) { usedT[e] = true; list.Add(e); cur = tree[e].Item1 == cur ? tree[e].Item2 : tree[e].Item1; seq.Add(cur);
          if (adjT[cur].Count != 2) break; int nx = -1; foreach (int f in adjT[cur]) if (!usedT[f]) nx = f; if (nx < 0) break; e = nx; }
        double km = list.Sum(t => eLen[t]), deg = km > 0 ? list.Sum(t => eLen[t] * eDeg[t]) / km : 0; bool mini = deg >= miniDeg;
        foreach (int t in list) edgeMini[t] = mini;
        Chains.Add(string.Join(",", seq) + "|" + km.ToString("F0") + "|" + deg.ToString("F1") + "|" + (mini ? 1 : 0)); } }
    // 印
    var flags = new int[recs.Count][][];
    for (int i = 0; i < recs.Count; i++) { flags[i] = new int[recs[i].Parts.Count][]; for (int pi = 0; pi < recs[i].Parts.Count; pi++) flags[i][pi] = new int[Math.Max(0, recs[i].Parts[pi].Length / 2 - 1)]; }
    for (int t = 0; t < tree.Count; t++) { int mark = edgeMini[t] ? 2 : 1; var path = paths[(long)tree[t].Item1 * 100000 + tree[t].Item2];
      foreach (var st in path) { if (st[1] < 0) continue; var fl = flags[st[1]][st[2]]; if (mark == 1 || fl[st[3]] == 0) fl[st[3]] = mark; } }
    return flags;
  }
  // レコードの印を整える: 通った長さがレコードの half 以上なら、レコード全部を多い方の印にする。そうでなければ、minRunKm 以上続く所だけ残す
  public static void Tidy(List<ShpRecord> recs, int[][][] flags, double half, double minRunKm) {
    for (int i = 0; i < recs.Count; i++) {
      double total = 0, on1 = 0, on2 = 0;
      for (int pi = 0; pi < recs[i].Parts.Count; pi++) { var p = recs[i].Parts[pi]; for (int s = 0; s < flags[i][pi].Length; s++) { double d = D(p[2*s], p[2*s+1], p[2*s+2], p[2*s+3]); total += d; if (flags[i][pi][s] == 1) on1 += d; else if (flags[i][pi][s] == 2) on2 += d; } }
      if (on1 + on2 == 0) continue;
      if (on1 + on2 >= half * total) { int m = on1 >= on2 ? 1 : 2; foreach (var fl in flags[i]) for (int s = 0; s < fl.Length; s++) fl[s] = m; continue; }
      for (int pi = 0; pi < recs[i].Parts.Count; pi++) { var p = recs[i].Parts[pi]; var fl = flags[i][pi]; int s0 = 0;
        while (s0 < fl.Length) { int s1 = s0; double run = 0; while (s1 < fl.Length && fl[s1] == fl[s0]) { run += D(p[2*s1], p[2*s1+1], p[2*s1+2], p[2*s1+3]); s1++; }
          if (fl[s0] != 0 && run < minRunKm * 1000) for (int s = s0; s < s1; s++) fl[s] = 0;
          s0 = s1; } } }
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
  @{ Code = 3;  Name = 'river';          File = 'riverl_jpn';   Keep = { param($r) (Num $r 'lit') -in 1, 2 }; Kind = { param($r) 0 } }
  @{ Code = 4;  Name = 'road';           File = 'roadl_jpn';    Keep = { param($r) (Num $r 'rtt') -in 14, 15, 16 }; Kind = { param($r) Num $r 'rtt' } }
  @{ Code = 5;  Name = 'railway';        File = 'raill_jpn';    Keep = { param($r) $true };              Kind = { param($r) Num $r 'fco' } }
  @{ Code = 6;  Name = 'muni_boundary';  File = 'polbndl_jpn';  Keep = { param($r) $r['use'] -eq '30' };   Kind = { param($r) Num $r 'bst' } }
  @{ Code = 7;  Name = 'ferry';          File = 'ferryl_jpn';   Keep = { param($r) $true };              Kind = { param($r) 0 } }
  @{ Code = 9;  Name = 'inland_water';   File = 'inwatera_jpn'; Keep = { param($r) $true };              Kind = { param($r) 0 } }
  @{ Code = 20; Name = 'airport';        File = 'airp_jpn';     Keep = { param($r) $true };              Kind = { param($r) Num $r 'use' } }
)
# 段（使う R1 の下限 [m]・許す差 [m]）
$Levels = @(
  @{ MinR1 = 0;      Tol = 0.0;   MinExtent = 0.0;   DenseMinExtent = 0.0 }
  @{ MinR1 = 25000;  Tol = 70.0;  MinExtent = 0.0;   DenseMinExtent = 0.0 }
  @{ MinR1 = 50000;  Tol = 150.0; MinExtent = 300.0; DenseMinExtent = 1000.0 }
  @{ MinR1 = 100000; Tol = 500.0; MinExtent = 500.0; DenseMinExtent = 4000.0 }
)
$Unit = 10000
# 短い線を捨てる決まり（段 2・3 の MinExtent / DenseMinExtent）から外すレイヤ: 道路（線が途切れて見えるため）・河川・湖沼など（大きさの段で絞るため）
$NoExtentRule = @(3, 4, 9)
# 点の多いレイヤ（段 2・3 で DenseMinExtent を使う）: 鉄道・市区町村境
$DenseLayers = @(5, 6)

# 河川・湖沼などの「レイヤの中の種類」= 大きさの段（下の 4 ビット）＋ 幅のある川の印（0x10）。
# 段 2（R1 50 km 以上）・段 3（100 km 以上）は、大きさの段がこの値以上の線だけを入れる
#   河川: 水系の長さ。水系 = 名前のある川（lit = 1・2）を、名前によらず端がつながるものどうしでまとめ、名前のない区間で
#         切れている所は、その区間（名前のない線をたどる最短の道が $BridgeMaxKm 以下）も入れたもの。長さは湖・沼の中の
#         つなぎの線を除いて足す。1 = 5 km 以上 30 km 未満、2 = 30〜50 km、3 = 50 km 以上（5 km 未満は入れない）
#         描く線: lit = 1 と、川の面（hyt = 1）・貯水池（hyt = 10）・面の外のつなぎの線 lit = 2（0x10 を付ける。アプリは
#         R1 10 km 以上でだけ描く）。湖（hyt = 4）・沼（hyt = 13）の中のつなぎの線は入れない（湖の輪郭がある）
#   湖沼など: 面の面積（外の輪 − 穴）。1 = 0.1 km² 以上 1 km² 未満、2 = 1〜10 km²、3 = 10 km² 以上（0.1 km² 未満は入れない）
#         川の面（hyt = 1）は 0x10 を付け、段 0 だけに入れる（アプリは R1 10 km 未満でだけ描く。それより広域では川の中心の線）
#         穴（内側の輪）は 1 km² 以上（湖の島・干拓地）だけを入れる。小さな穴（中洲など）は入れない
$RiverMinKm = 5; $RiverClassKm = @(30, 50); $BridgeMaxKm = 20; $LakeTolM = 300
$WaterMinKm2 = 0.1; $WaterClassKm2 = @(1, 10); $HoleMinKm2 = 1
$WideRiverFlag = 0x10
$MotorwayMinKm = 20   # 高速道路のまとまりがこれより短いと「その他の自動車専用道路」
$MotorwayCurvyMaxKm = 100; $MotorwayCurvyDegPerKm = 80   # これより短く、1 km あたりの曲がった角度がこれ以上のまとまりも同じ
$BranchMinKm = 2; $BranchMaxKm = 100; $BranchDegPerKm = 100   # 網の枝: 長さがこの範囲で、1 km あたりの曲がった角度がこれ以上の枝も同じ
# 鉄道: 新幹線を見分ける（Rail の説明）。種類の 0x10 = 新幹線、0x20 = 在来線の線路を走る新幹線（山形・秋田。アプリは在来線として描く）
$RailCurvK = 50; $RailGapM = 300; $StationSnapM = 1000; $StationPairKm = 150; $MiniShinkansenDeg = 15; $RailHalf = 0.5; $RailMinRunKm = 2
$ShinkansenFlag = 0x10; $MiniShinkansenFlag = 0x20
# 山頂（20261021-D02）: 種類の下の 7 ビット = 標高 ÷ PeakElevStepM（切り捨て）、0x80 = 目印になる山
#   目印になる山 = 標高が PeakLandmarkMinM 以上、かつ孤立度（一覧の中で自分より高い山頂までの距離）が PeakLandmarkMinIsoKm 以上（仮）
$PeakElevStepM = 50; $PeakLandmarkMinM = 800; $PeakLandmarkMinIsoKm = 15; $PeakLandmarkFlag = 0x80
$MinSizeClass = @(1, 1, 2, 3)   # 段ごとに入れる大きさの段の下限
function SizeClass($v, $edges) { if ($v -ge $edges[1]) { 3 } elseif ($v -ge $edges[0]) { 2 } else { 1 } }

# 読む（レイヤごとに、線の部分（単位の整数）と点）
$data = foreach ($layer in $Layers) {
  $recs = [Gm]::ReadShp((Join-Path $Src "$($layer.File).shp"))
  $rows = [Gm]::ReadDbf((Join-Path $Src "$($layer.File).dbf"))
  if ($recs.Count -ne $rows.Count) { throw "$($layer.File): .shp と .dbf の件数が違う" }
  # 河川: 水系の長さと、線がどの面（湖沼など）の中にあるか
  $riverKm = $null; $riverCont = $null
  if ($layer.Code -eq 3) {
    $wRecs = [Gm]::ReadShp((Join-Path $Src 'inwatera_jpn.shp')); $wRows = [Gm]::ReadDbf((Join-Path $Src 'inwatera_jpn.dbf'))
    $wHyt = [int[]]::new($wRows.Count); for ($k = 0; $k -lt $wRows.Count; $k++) { $wHyt[$k] = Num $wRows[$k] 'hyt' }
    $riverCont = [Gm]::ContainerHyt($recs, $wRecs, $wHyt)
    # 湖・沼・貯水池の面（川の端がこの面の中か輪郭から $LakeTolM 以内なら、湖を通ってつながっているとみなす）
    $lakes = [System.Collections.Generic.List[ShpRecord]]::new(); for ($k = 0; $k -lt $wRecs.Count; $k++) { if ($wHyt[$k] -in 4, 10, 13) { $lakes.Add($wRecs[$k]) } }
    $lit = [int[]]::new($rows.Count); $named = [bool[]]::new($rows.Count)
    for ($k = 0; $k -lt $rows.Count; $k++) { $lit[$k] = Num $rows[$k] 'lit'; $named[$k] = $lit[$k] -in 1, 2 -and (RiverName $rows[$k]) }
    $riverKm = [Gm]::WaterSystems($recs, $lit, $named, $riverCont, $BridgeMaxKm, $lakes, $LakeTolM)
    $script:RiverStats = @{ Km = $riverKm; Cont = $riverCont; Lit = $lit; Named = $named; Rows = $rows }
  }
  # 道路: 高速道路（rtt = 16）の線を、端の点がつながるまとまりに分けた長さ。$MotorwayMinKm 未満のまとまりは
  # 「その他の自動車専用道路」（種類 17）にする（観光の有料道路・有料の橋やトンネル・バイパス・網から切れたかけら。20261018-D06）
  # まとまりが $MotorwayCurvyMaxKm 未満で、1 km あたりの曲がった角度が $MotorwayCurvyDegPerKm 以上のもの
  # （カーブの続く観光の有料道路。大きな網から離れている）も同じにする（20261018-D07）
  # 大きな網につながっていても、網の枝（片方の端だけで網につながる部分）のうち、長さ $BranchMinKm 以上 $BranchMaxKm 未満で、
  # 1 km あたりの曲がった角度が $BranchDegPerKm 以上のもの（網につながるカーブの多い観光の有料道路）も同じにする（20261019-D01）
  # 鉄道: 新幹線の駅（rstatp）どうしを結ぶ道から、新幹線と、在来線の線路を走る新幹線（山形・秋田）を見分ける（20261019-D02。Rail の説明）
  $railFlags = $null
  if ($layer.Code -eq 5) {
    $sRecs = [Gm]::ReadShp((Join-Path $Src 'rstatp_jpn.shp')); $sRows = [Gm]::ReadDbf((Join-Path $Src 'rstatp_jpn.dbf'))
    $stations = [System.Collections.Generic.List[double[]]]::new(); foreach ($s in $sRecs) { $stations.Add([double[]]@($s.X, $s.Y)) }
    $railFlags = [Rail]::Shinkansen($recs, $stations, $RailCurvK, $RailGapM, $StationSnapM, $StationPairKm, $MiniShinkansenDeg)
    [Rail]::Tidy($recs, $railFlags, $RailHalf, $RailMinRunKm)
    $script:RailStats = @{ Flags = $railFlags; Recs = $recs; Rows = $rows; StationRows = $sRows; TreeEdges = @([Rail]::TreeEdges); Chains = @([Rail]::Chains) }
  }
  $motorwayKm = $null; $motorwayDeg = $null; $motorwayBranch = $null
  if ($layer.Code -eq 4) {
    $mUse = [bool[]]::new($rows.Count)
    for ($k = 0; $k -lt $rows.Count; $k++) { $mUse[$k] = (Num $rows[$k] 'rtt') -eq 16 }
    $mc = [Gm]::ComponentCurvature($recs, $mUse); $motorwayKm = $mc[0]; $motorwayDeg = $mc[1]
    # 枝を調べるのは、D06・D07 の決め方で格下げしないまとまりだけ
    $bUse = [bool[]]::new($rows.Count)
    for ($k = 0; $k -lt $rows.Count; $k++) {
      $bUse[$k] = $mUse[$k] -and $motorwayKm[$k] -ge $MotorwayMinKm -and -not ($motorwayKm[$k] -lt $MotorwayCurvyMaxKm -and $motorwayDeg[$k] -ge $MotorwayCurvyDegPerKm)
    }
    $motorwayBranch = [Gm]::CurvyBranches($recs, $bUse, $BranchMinKm, $BranchMaxKm, $BranchDegPerKm)
    $script:MotorwayStats = @{ Km = $motorwayKm; Deg = $motorwayDeg; Branch = $motorwayBranch; Use = $mUse; Recs = $recs; Rows = $rows }
  }
  $lines = [System.Collections.Generic.List[object[]]]::new(); $points = [System.Collections.Generic.List[object]]::new(); $kept = 0
  for ($i = 0; $i -lt $recs.Count; $i++) {
    if (-not (& $layer.Keep $rows[$i])) { continue }
    $kind = & $layer.Kind $rows[$i]; $r = $recs[$i]; $parts = $r.Parts
    if ($layer.Code -eq 4 -and $kind -eq 16 -and ($motorwayKm[$i] -lt $MotorwayMinKm -or
        ($motorwayKm[$i] -lt $MotorwayCurvyMaxKm -and $motorwayDeg[$i] -ge $MotorwayCurvyDegPerKm) -or $motorwayBranch[$i])) { $kind = 17 }
    if ($layer.Code -eq 3) {
      if ($riverKm[$i] -lt $RiverMinKm) { continue }
      if ($lit[$i] -eq 2 -and $riverCont[$i] -in 4, 13) { continue }   # 湖・沼の中のつなぎの線は入れない
      $kind = SizeClass $riverKm[$i] $RiverClassKm
      if ($lit[$i] -eq 2) { $kind = $kind -bor $WideRiverFlag }
    } elseif ($layer.Code -eq 9) {
      $a = [Gm]::AreaKm2($r.Parts)
      if ($a -lt $WaterMinKm2) { continue }
      $kind = SizeClass $a $WaterClassKm2
      if ((Num $rows[$i] 'hyt') -eq 1) { $kind = $kind -bor $WideRiverFlag }
      # 外の輪と、1 km² 以上の穴（湖の島・干拓地）だけ。小さな穴（中洲など）は描かない
      $parts = [System.Collections.Generic.List[double[]]]::new()
      foreach ($xy in $r.Parts) {
        $ring = [System.Collections.Generic.List[double[]]]::new(); $ring.Add($xy)
        if ([Gm]::IsOuter($xy) -or [Gm]::AreaKm2($ring) -ge $HoleMinKm2) { $parts.Add($xy) }
      }
    }
    $kept++
    if ($r.Type -in 1, 11, 21) {
      $name = if ($rows[$i].ContainsKey('nam') -and $rows[$i]['nam'] -ne 'UNK') { $rows[$i]['nam'] } else { '' }
      $points.Add(@{ Kind = $kind; Lat = [int][math]::Round($r.Y * $Unit); Lon = [int][math]::Round($r.X * $Unit); Name = $name })
    } elseif ($layer.Code -eq 5) {
      # 鉄道: 新幹線・山形と秋田の印が変わる所で線を切り、種類に印を足す（種類の下位は fco のまま）
      for ($pi = 0; $pi -lt $parts.Count; $pi++) {
        $xy = $parts[$pi]; $fl = $railFlags[$i][$pi]; $s0 = 0
        while ($s0 -lt $fl.Length) {
          $s1 = $s0; while ($s1 -lt $fl.Length -and $fl[$s1] -eq $fl[$s0]) { $s1++ }
          $sub = [double[]]::new(2 * ($s1 - $s0 + 1)); [Array]::Copy($xy, 2 * $s0, $sub, 0, $sub.Length)
          $k = [int]$kind -bor $(if ($fl[$s0] -eq 1) { $ShinkansenFlag } elseif ($fl[$s0] -eq 2) { $MiniShinkansenFlag } else { 0 })
          $lon = $null; $lat = [Gm]::ToUnits($sub, $Unit, [ref]$lon)
          if ($lat.Length -ge 2) { $lines.Add([object[]]@($lat, $lon, $k)) }
          $s0 = $s1
        }
      }
    } else {
      foreach ($xy in $parts) {
        $lon = $null; $lat = [Gm]::ToUnits($xy, $Unit, [ref]$lon)
        if ($lat.Length -ge 2) { $lines.Add([object[]]@($lat, $lon, [int]$kind)) }
      }
    }
  }
  # 線のレイヤは、端がつながっている線をつなぐ（面の輪郭はそのまま）。湖沼などは、となりの面と重なる辺を除く
  $isArea = $recs.Count -gt 0 -and $recs[0].Type -in 5, 15, 25
  if ($layer.Code -eq 9) { $lines = [Gm]::DropSharedEdges($lines) }
  $merged = if ($lines.Count -gt 0 -and -not $isArea) { [Gm]::Merge($lines) } else { $lines }
  $lineObjs = [System.Collections.Generic.List[object]]::new()
  foreach ($m in $merged) { $lineObjs.Add(@{ Lat = [int[]]$m[0]; Lon = [int[]]$m[1]; Kind = [int]$m[2] }) }
  # 近い線が続くように並べる（線の最初の点の 0.1 度の升目で、南から北へ・西から東へ）。見出しの差が小さくなる
  $sorted = $lineObjs | Sort-Object { [math]::Floor($_.Lat[0] / ($Unit / 10)) }, { $_.Lon[0] }
  @{ Layer = $layer; Lines = $lineObjs; Sorted = @($sorted); Points = $points; Kept = $kept; Parts = $lines.Count }
}

# 山頂（20261021-D02）: 国土地理院「日本の山岳標高一覧（1003山）」の CSV（Shift_JIS）。枝番の山頂（14-1・14-2 など）も 1 つの点にする
$peakLayer = @{ Code = 25; Name = 'peak' }
$pkLines = [System.IO.File]::ReadAllLines((Join-Path (Get-Location) $PeakCsv), [System.Text.Encoding]::GetEncoding(932))
$pkFields = for ($k = 1; $k -lt $pkLines.Count; $k++) {
  if (-not $pkLines[$k]) { continue }
  $f = $pkLines[$k] -split ','
  if ($f.Count -ne 9) { throw "${PeakCsv}: 列の数が 9 でない行がある: $($pkLines[$k])" }
  , $f
}
$pkN = @($pkFields).Count
$pkLat = [double[]]::new($pkN); $pkLon = [double[]]::new($pkN); $pkElev = [double[]]::new($pkN)
for ($k = 0; $k -lt $pkN; $k++) { $pkElev[$k] = [double]$pkFields[$k][4]; $pkLat[$k] = [double]$pkFields[$k][7]; $pkLon[$k] = [double]$pkFields[$k][8] }
$pkIso = [Gm]::Isolation($pkLat, $pkLon, $pkElev)
$pkPoints = [System.Collections.Generic.List[object]]::new()
$pkReport = for ($k = 0; $k -lt $pkN; $k++) {
  $kind = [math]::Min(0x7F, [int][math]::Floor($pkElev[$k] / $PeakElevStepM))
  $mark = $pkElev[$k] -ge $PeakLandmarkMinM -and $pkIso[$k] -ge $PeakLandmarkMinIsoKm
  if ($mark) { $kind = $kind -bor $PeakLandmarkFlag }
  $pkPoints.Add(@{ Kind = $kind; Lat = [int][math]::Round($pkLat[$k] * $Unit); Lon = [int][math]::Round($pkLon[$k] * $Unit); Name = $pkFields[$k][2] })
  [pscustomobject]@{ idx = $pkFields[$k][1]; name = $pkFields[$k][2]; elev = $pkElev[$k]; type = $pkFields[$k][5]; pref = $pkFields[$k][6]; lat = $pkLat[$k]; lon = $pkLon[$k]; isoKm = $pkIso[$k]; landmark = $mark; kind = $kind }
}
$data = @($data) + @(@{ Layer = $peakLayer; Lines = @(); Sorted = @(); Points = $pkPoints; Kept = $pkN; Parts = 0 })
$Layers = @($Layers) + @($peakLayer)
"山頂: $pkN 点（目印になる山 $(@($pkReport | Where-Object landmark).Count)）" | Write-Host

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
      # 河川・湖沼など: 段ごとに小さいものを入れない（大きさの段）
      if ($d.Layer.Code -in 3, 9 -and ($ln.Kind -band 0x0F) -lt $MinSizeClass[$lv]) { continue }
      # 川の面（hyt = 1）は段 0 だけ（R1 10 km 以上は川の中心の線で描くので、段 1 から上には要らない）
      if ($d.Layer.Code -eq 9 -and ($ln.Kind -band $WideRiverFlag) -and $lv -gt 0) { continue }
      if ($level.Tol -gt 0) {
        $idx = [Gm]::Simplify($lat, $lon, $level.Tol, $Unit)
        $lat = [int[]]::new($idx.Length); $lon = [int[]]::new($idx.Length); for ($q = 0; $q -lt $idx.Length; $q++) { $lat[$q] = $ln.Lat[$idx[$q]]; $lon[$q] = $ln.Lon[$idx[$q]] }
        $minExt = if ($d.Layer.Code -in $NoExtentRule) { 0 } elseif ($d.Layer.Code -in $DenseLayers) { $level.DenseMinExtent } else { $level.MinExtent }
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
# 河川のレコードごと（名前・lit・入っている面の種類・水系の長さ。0 = 水系に入らない）（レポート用）
$rs = $script:RiverStats
$riverRows = for ($k = 0; $k -lt $rs.Rows.Count; $k++) {
  [pscustomobject]@{ idx = $k; lit = $rs.Lit[$k]; nam = $rs.Rows[$k]['nam']; named = $rs.Named[$k]; cont = $rs.Cont[$k]; systemKm = [math]::Round($rs.Km[$k], 2) }
}
$riverRows | ConvertTo-Csv -NoTypeInformation | Set-Content -Encoding utf8 (Join-Path ([System.IO.Path]::GetTempPath()) 'gm2basemap_rivers.csv')
# 山頂ごと（索引番号・名前・標高・孤立度・目印になるか・種類）（レポート用）
$pkReport | ConvertTo-Csv -NoTypeInformation | Set-Content -Encoding utf8 (Join-Path ([System.IO.Path]::GetTempPath()) 'gm2basemap_peaks.csv')
"書いた: $Out（$($buf.Length) バイト、圧縮の目安 $([Gm]::Deflated($buf.ToArray())) バイト）"
