"""
歩くときの方位の出し方の調べ（20261121-D05-HDGWALK）。アプリのコードは変えない調べ用。

使い方:
    python tools/hdg_walk_study.py <track.csv か、track.csv のあるフォルダ> [...] --out <結果の JSON>

    例: python tools/hdg_walk_study.py sample/walk/session_20261121_132427 --out build/hdg_walk.json

    - 歩きの記録（NavHUD の track.csv）をいくつでも渡せる。記録ごとに計算して、物差しは全部の記録の点を合わせて数える。
    - 結果の JSON には物差しの数字だけを入れる（位置・座標は入れない）。
    - Python 3 の標準のライブラリだけで動く。

中身（アプリのコードと同じ計算）:
    - 位置から求める向き（TrackHeading）: 直前に D m 歩いた所（速さの積分。2 秒を超える間は直線の距離）と今の位置を結ぶ向き。
      20261122-D04 から、アプリの D は その点の水平精度 × 1.0 を「向きを求める距離」〜15 m に収めたもの（kacc = (1.0, 距離, 15)）。
      1 km/h 未満・水平精度が 15 m を超える点では変えない。10 秒を超える欠けをまたがない。結ぶ相手は 90 秒以内、
      まっすぐの距離が 0.6 × D 以上。
    - GPS の向き（HeadingSelector）: 止める速度より遅くなると保持（HLD）、戻す速度を超え方位精度が上限以内の点で戻る。
      方位精度が上限を超えた点は前の値のまま。bearing_acc_deg が 0 か空の点は方位なし（TrackCsv と同じ）。
    - 出し方: GPS / GPS＋位置（GPS_TRACK）/ 位置だけ（TRACK_ONLY）と、調べの案（D を水平精度に合わせる・GPS を精度がとても良いときだけ使う）。

物差し（前の調べ 20261119-D03-WALK・20261121-D04 の 3節と同じ数え方）:
    - 答え 1（ref10）: 前後 10 m ずつ歩いた位置を結んだ向き。位置から作るので、位置から求める向きに少し有利。
    - 答え 2（line60）: まっすぐな所だけ。前後 30 m の位置に直線を当てはめ、直線からのずれが小さい所で、その直線の向きを答えにする。
      位置は使うが、60 m の直線に当てはめるので D m の 2 点を結ぶ向きとは作り方が違う（曲がり角・止まりは入らない）。
    - 誤差（中央・95%）・30° を超える割合・ずれたままの最長（2 km/h 以上の点で 30° を超えたまま続いた秒数）は、答えごと。
    - 止まっている間（止まる前の 12 m の向きとの差の 95%）、曲がり角（答え 1 の 5 m 版との差の 95%・遅れの中央 / 90%）、
      揺れ（1 秒ごとの変わり方の 95%）、GPS と TRK の入れ替わり（2 km/h 以上の 1 分あたりの回数）は、答えによらない。
"""
import argparse
import csv
import json
import math
import os
import struct
import sys
import time

KMH = 3.6
R_EARTH = 6371000.0


# ---------------------------------------------------------------- 角度・統計

def norm360(d):
    return d % 360.0


def ang(a, b):
    """a − b を −180..180 に"""
    return (a - b + 180.0) % 360.0 - 180.0


def cmean(v):
    x = sum(math.cos(math.radians(a)) for a in v)
    y = sum(math.sin(math.radians(a)) for a in v)
    return norm360(math.degrees(math.atan2(y, x)))


def pct(v, p):
    s = sorted(v)
    if not s:
        return None
    k = (len(s) - 1) * p / 100.0
    f = int(k)
    c = min(f + 1, len(s) - 1)
    return s[f] + (s[c] - s[f]) * (k - f)


def bearing(a, b):
    return norm360(math.degrees(math.atan2(b[0] - a[0], b[1] - a[1])))


def fnum(s):
    try:
        x = float(s)
        return x if math.isfinite(x) else None
    except (TypeError, ValueError):
        return None


def f32(x):
    """アプリの Fix と同じ 32 ビットの Float に丸める（速さ・精度・方位。20261122-D01）。
    丸めないと、歩いた距離がちょうど D になる所で、アプリと結ぶ相手が 1 点ずれることがある"""
    return None if x is None else struct.unpack('f', struct.pack('f', x))[0]


# ---------------------------------------------------------------- 読み込みと下ごしらえ

def load_track(path):
    if os.path.isdir(path):
        path = os.path.join(path, 'track.csv')
    P = []
    with open(path, encoding='utf-8-sig', newline='') as h:
        for r in csv.DictReader(h):
            # 緯度・経度は Double、速さ・水平精度・方位・方位精度は Float（アプリの Fix と同じ）
            p = dict(t=int(r['epoch_ms']) / 1000.0, lat=fnum(r['latitude']), lon=fnum(r['longitude']),
                     h=f32(fnum(r['horizontal_acc_m'])), v=f32(fnum(r['speed_mps'])), brg0=f32(fnum(r['bearing_deg'])), ba0=f32(fnum(r['bearing_acc_deg'])))
            if p['lat'] is None or p['lon'] is None:
                continue
            ok = p['ba0'] is not None and p['ba0'] > 0
            p['brg'] = p['brg0'] if ok else None
            p['ba'] = p['ba0'] if ok else None
            if p['v'] is None:
                p['v'] = 0.0
            p['kmh'] = p['v'] * KMH
            P.append(p)
    return P


def enu(P):
    lat0 = P[0]['lat']
    c = math.cos(math.radians(lat0))
    for p in P:
        p['e'] = math.radians(p['lon'] - P[0]['lon']) * R_EARTH * c
        p['n'] = math.radians(p['lat'] - lat0) * R_EARTH


def add_ref(P, key, D, W, min_chord):
    """前後の歩きから分かる向き: 速さの積分で前後 D m ずつ離れた 2 点を結ぶ向き（W 秒以内）。止まっている点は None"""
    n = len(P)
    S = [0.0] * n
    for i in range(1, n):
        dt = P[i]['t'] - P[i - 1]['t']
        S[i] = S[i - 1] + (P[i - 1]['v'] + P[i]['v']) / 2 * dt if dt <= 3 else S[i - 1] + 1e6
    for i, p in enumerate(P):
        p[key] = None
        if p['v'] <= 0:
            continue
        a = i
        while a > 0 and S[i] - S[a] < D and p['t'] - P[a - 1]['t'] <= W:
            a -= 1
        b = i
        while b < n - 1 and S[b] - S[i] < D and P[b + 1]['t'] - p['t'] <= W:
            b += 1
        if S[i] - S[a] < D * .8 or S[b] - S[i] < D * .8 or S[b] - S[a] > 1e5:
            continue
        de = P[b]['e'] - P[a]['e']
        dn = P[b]['n'] - P[a]['n']
        if math.hypot(de, dn) < min_chord:
            continue
        p[key] = norm360(math.degrees(math.atan2(de, dn)))


def prep(P):
    """速さの積分の距離（10 秒を超える欠けで切る）"""
    sp = 0.0
    seg = 0
    for i, p in enumerate(P):
        if i:
            q = P[i - 1]
            dt = p['t'] - q['t']
            if dt > 10:
                seg += 1
            else:
                sp += (p['v'] + q['v']) / 2 * dt
        p['sp'] = sp
        p['gseg'] = seg


def add_line_truth(P, half=30.0, max_t=60.0, rms_max=1.5, dev_max=3.5, straight=0.95):
    """
    答え 2（line60）: その点の前後 half m（速さの積分）の位置に直線を当てはめる（主成分の向き）。
    前後とも 0.8 × half 以上あり、直線からのずれが RMS rms_max m 以下・最大 dev_max m 以下で、
    両端のまっすぐの距離が歩いた距離の straight 倍以上のときだけ、直線の向き（進む向き）を答えにする
    """
    n = len(P)
    for i, p in enumerate(P):
        p['line'] = None
        if p['v'] <= 0:
            continue
        a = i
        while a > 0 and P[a - 1]['gseg'] == p['gseg'] and p['sp'] - P[a - 1]['sp'] <= half and p['t'] - P[a - 1]['t'] <= max_t:
            a -= 1
        b = i
        while b < n - 1 and P[b + 1]['gseg'] == p['gseg'] and P[b + 1]['sp'] - p['sp'] <= half and P[b + 1]['t'] - p['t'] <= max_t:
            b += 1
        if p['sp'] - P[a]['sp'] < .8 * half or P[b]['sp'] - p['sp'] < .8 * half:
            continue
        w = P[a:b + 1]
        me = sum(q['e'] for q in w) / len(w)
        mn = sum(q['n'] for q in w) / len(w)
        see = sum((q['e'] - me) ** 2 for q in w)
        snn = sum((q['n'] - mn) ** 2 for q in w)
        sen = sum((q['e'] - me) * (q['n'] - mn) for q in w)
        th = 0.5 * math.atan2(2 * sen, see - snn)
        ue, un = math.cos(th), math.sin(th)
        dev = [abs(-(q['e'] - me) * un + (q['n'] - mn) * ue) for q in w]
        rms = math.sqrt(sum(d * d for d in dev) / len(dev))
        de = P[b]['e'] - P[a]['e']
        dn = P[b]['n'] - P[a]['n']
        if rms > rms_max or max(dev) > dev_max or math.hypot(de, dn) < straight * (P[b]['sp'] - P[a]['sp']):
            continue
        if ue * de + un * dn < 0:
            ue, un = -ue, -un
        p['line'] = norm360(math.degrees(math.atan2(ue, un)))


def chord(P, i0, s_from, s_to):
    a = b = None
    for q in P[max(0, i0 - 60):min(len(P), i0 + 60)]:
        if abs(q['t'] - P[i0]['t']) > 40 or q['gseg'] != P[i0]['gseg']:
            continue
        s = q['sp'] - P[i0]['sp']
        if a is None and s >= s_from:
            a = q
        if s <= s_to:
            b = q
    if a is None or b is None:
        return None
    de, dn = b['e'] - a['e'], b['n'] - a['n']
    if b['sp'] - a['sp'] < 8 or math.hypot(de, dn) < 7:
        return None
    return norm360(math.degrees(math.atan2(de, dn)))


def scenes(P):
    """止まった所（速さ 0 が 4 秒以上）と曲がり角（前の調べと同じ見つけ方）"""
    C = dict(stops=[], turns=[])
    i = 0
    while i < len(P):
        if P[i]['v'] != 0:
            i += 1
            continue
        j = i
        while j + 1 < len(P) and P[j + 1]['v'] == 0 and P[j + 1]['t'] - P[j]['t'] <= 3:
            j += 1
        if P[j]['t'] - P[i]['t'] >= 4:
            pre = chord(P, i, -12, -1) if i > 3 else None
            C['stops'].append(dict(i=i, j=j, pre=pre))
        i = j + 1
    flag = []
    for i, p in enumerate(P):
        f = False
        if 2 <= i < len(P) - 2 and p['ref'] is not None and 0 < p['kmh'] < 20:
            a, b = P[i - 2], P[i + 2]
            if a['ref'] is not None and b['ref'] is not None and b['t'] - a['t'] <= 6:
                f = abs(ang(b['ref'], a['ref'])) / (b['t'] - a['t']) >= 5
        flag.append(f)
    i = 0
    while i < len(P):
        if not flag[i]:
            i += 1
            continue
        j = i
        k = i
        while k + 1 < len(P) and k - j <= 3:
            k += 1
            if flag[k]:
                j = k
        a = max(0, i - 3)
        while a > 0 and P[a]['ref'] is None and i - a < 10:
            a -= 1
        b = min(len(P) - 1, j + 3)
        while b < len(P) - 1 and P[b]['ref'] is None and b - j < 12:
            b += 1
        if P[a]['ref'] is not None and P[b]['ref'] is not None:
            turn = ang(P[b]['ref'], P[a]['ref'])
            if abs(turn) >= 30 and P[j]['t'] - P[i]['t'] <= 40:
                C['turns'].append(dict(i=i, j=j, a=a, b=b, turn=turn, final=P[b]['ref']))
        i = max(j, k) + 1
    return C


POOR_H = 8.0      # 精度が悪いとみなす水平精度 [m]
POOR_MIN_S = 3    # これ以上続いたら「精度が悪い所」 [秒]（記録の始めは 1 点でも）
SETTLE_WIN_S = 60  # 落ち着くのを待つ上限 [秒]


def poor_events(P):
    """精度が悪い所（水平精度 POOR_H を超える点が続く所）。記録の始めは長さを問わない。(始めの番号, 終わりの番号) の一覧"""
    ev = []
    i = 0
    while i < len(P):
        if P[i]['h'] is not None and P[i]['h'] > POOR_H:
            j = i
            while j + 1 < len(P) and P[j + 1]['h'] is not None and P[j + 1]['h'] > POOR_H and P[j + 1]['t'] - P[j]['t'] <= 10:
                j += 1
            if i == 0 or P[j]['t'] - P[i]['t'] + 1 >= POOR_MIN_S:
                ev.append((i, j))
            i = j + 1
        else:
            i += 1
    return ev


def setup(path):
    P = load_track(path)
    enu(P)
    add_ref(P, 'ref10', D=10.0, W=40.0, min_chord=14.0)
    add_ref(P, 'ref', D=5.0, W=20.0, min_chord=7.0)
    prep(P)
    add_line_truth(P)
    C = scenes(P)
    C['poor'] = poor_events(P)
    return dict(P=P, C=C, name=os.path.basename(os.path.dirname(path) if path.endswith('.csv') else path.rstrip('/\\')))


# ---------------------------------------------------------------- 出し方（アプリと同じ計算）

class Selector:
    """HeadingSelector（GPS の向き・保持）"""

    def __init__(s, en, ex, mb, ma=15.0):
        s.en, s.ex, s.ma, s.mb = en / KMH, ex / KMH, ma, mb
        s.holding = False
        s.live = None
        s.stable = None

    def usable(s, p):
        return p['brg'] is not None and (p['h'] is None or p['h'] <= s.ma) and (p['ba'] is None or p['ba'] <= s.mb)

    def update(s, p):
        ok = s.usable(p)
        v = p['v']
        if s.holding:
            if ok and v is not None and v > s.ex:
                s.holding = False
        elif v is not None and v < s.en:
            s.holding = True
        s.live = None if s.holding else (p['brg'] if ok else s.live)
        if not s.holding and ok and (v is None or v >= s.ex):
            s.stable = p['brg']


def track_series(P, D=8.0, kacc=None, gate_kmh=1.0, max_acc=15.0, gap_s=10.0, age_s=90.0, minr=0.6, good_run=None):
    """
    位置から求める向き（TrackHeading）。点ごとの向き（まだなければ None）。
    kacc = (k, lo, hi) なら、その点の距離 D = 水平精度 × k を lo〜hi に収めたもの（調べの案）
    good_run = (hmax, N) なら（20261122-D03 の調べの案）、水平精度 hmax 以内の点が N 点続いてから点を使う。
    hmax を超える点が来たら、それまでの点を捨てる（向きはそのまま保つ）
    """
    out = []
    hist = []
    S = 0.0
    deg = None
    prev = None
    run = 0
    for p in P:
        if prev is not None:
            dt = p['t'] - prev['t']
            if dt > gap_s or dt < 0:
                hist = []
                deg = None
                S = 0.0
            elif p['v'] is not None and prev['v'] is not None and dt <= 2.0:
                S += (p['v'] + prev['v']) / 2 * dt
            else:
                S += math.hypot(p['e'] - prev['e'], p['n'] - prev['n'])
        prev = p
        good = p['h'] is None or p['h'] <= max_acc
        if good_run is not None:
            hmax, n_run = good_run
            if p['h'] is not None and p['h'] > hmax:
                run = 0
                hist = []
            else:
                run += 1
            good = good and run >= n_run
        Dp = D
        if kacc is not None:
            k, lo, hi = kacc
            Dp = min(hi, max(lo, k * (p['h'] or 0.0)))
        if good and p['kmh'] >= gate_kmh:
            for q in reversed(hist):
                if p['t'] - q[0] > age_s:
                    break
                if S - q[3] >= Dp and math.hypot(p['e'] - q[1], p['n'] - q[2]) >= minr * Dp:
                    deg = bearing((q[1], q[2]), (p['e'], p['n']))
                    break
        if good:
            hist.append((p['t'], p['e'], p['n'], S))
            while hist and p['t'] - hist[0][0] > age_s:
                hist.pop(0)
        out.append(deg)
    return out


def gps_only(P, en, ex, mb):
    s = Selector(en, ex, mb)
    out = []
    for p in P:
        s.update(p)
        if s.live is not None:
            out.append((s.live, 'G'))
        elif s.stable is not None:
            out.append((s.stable, 'H'))
        else:
            out.append((None, 'N'))
    return out


def gps_track(P, T, en, ex, mb):
    """GPS＋位置: GPS の向きがその点で使えるときは GPS、使えないとき（保持・精度が上限を超えた点）は位置から求める向き"""
    s = Selector(en, ex, mb)
    out = []
    for p, t in zip(P, T):
        s.update(p)
        if (not s.holding) and s.usable(p):
            out.append((s.live, 'G'))
        elif t is not None:
            out.append((t, 'T'))
        elif s.live is not None:
            out.append((s.live, 'G'))
        elif s.stable is not None:
            out.append((s.stable, 'H'))
        else:
            out.append((None, 'N'))
    return out


def track_only(P, T, en=1, ex=3, mb=30.0):
    """位置だけ: いつも位置から求める向き。まだなければ GPS＋位置で向きがまだないときと同じ（GPS の値・保持の値）"""
    s = Selector(en, ex, mb)
    out = []
    for p, t in zip(P, T):
        s.update(p)
        if t is not None:
            out.append((t, 'T'))
        elif s.live is not None:
            out.append((s.live, 'G'))
        elif s.stable is not None:
            out.append((s.stable, 'H'))
        else:
            out.append((None, 'N'))
    return out


def good_gps(P, T, X, N, gate_kmh=1.0, max_acc=15.0):
    """
    調べの案: 方位精度が X° 以内の GPS の向きが N 点続いたら（1.5 秒より長い間はとぎれ）GPS、そうでなければ位置から求める向き。
    位置から求める向きがまだなければ、GPS＋位置（1 / 3 km/h・30°）で向きがまだないときと同じ
    """
    s = Selector(1, 3, 30.0)
    out = []
    run = 0
    prev_t = None
    for p, t in zip(P, T):
        s.update(p)
        good = p['brg'] is not None and p['ba'] <= X and (p['h'] is None or p['h'] <= max_acc) and p['kmh'] >= gate_kmh
        if prev_t is not None and p['t'] - prev_t > 1.5:
            run = 0
        run = run + 1 if good else 0
        prev_t = p['t']
        if run >= N:
            out.append((p['brg'], 'G'))
        elif t is not None:
            out.append((t, 'T'))
        elif s.live is not None:
            out.append((s.live, 'G'))
        elif s.stable is not None:
            out.append((s.stable, 'H'))
        else:
            out.append((None, 'N'))
    return out


# ---------------------------------------------------------------- 物差し

def err_block(recs, H, key, vmin=2.0, sel=None):
    """答え key との誤差: 歩いている点（vmin km/h 以上・答えがある）。ずれたままの最長は 30° を超えたまま続いた点の数（1 秒ごと）"""
    e = []
    best = 0
    over30 = 0
    for r, h in zip(recs, H):
        P = r['P']
        run = 0
        for i, p in enumerate(P):
            if p['kmh'] < vmin or p[key] is None or (sel is not None and not sel(p)):
                run = 0
                continue
            x = h[i][0]
            if x is None:
                run = 0
                continue
            d = abs(ang(x, p[key]))
            e.append(d)
            if d > 30:
                over30 += 1
                run += 1
                best = max(best, run)
            else:
                run = 0
    return dict(N=len(e), med=pct(e, 50), p95=pct(e, 95), over30=over30 / max(1, len(e)), stuck=best)


def jitter(recs, H, vmin=2.0):
    """揺れ: 歩いている点（答え 1 の 5 m 版がある）で、1 秒前との差"""
    dh = []
    for r, h in zip(recs, H):
        P = r['P']
        for i, p in enumerate(P):
            if not i or p['kmh'] < vmin or p['ref'] is None or h[i][0] is None or h[i - 1][0] is None:
                continue
            if p['t'] - P[i - 1]['t'] <= 1.01 and P[i - 1]['kmh'] >= vmin:
                dh.append(abs(ang(h[i][0], h[i - 1][0])))
    return pct(dh, 95)


def stops_turns(recs, H):
    e = []
    te = []
    delays = []
    per_turn = []
    cens = 0
    nturn = 0
    for r, h in zip(recs, H):
        P = r['P']
        C = r['C']
        for s in C['stops']:
            v = [h[k][0] for k in range(s['i'], s['j'] + 1) if h[k][0] is not None]
            if len(v) < 2 or s['pre'] is None:
                continue
            e.append(abs(ang(cmean(v), s['pre'])))
        for ev in C['turns']:
            nturn += 1
            for k in range(ev['i'], ev['j'] + 1):
                if P[k]['ref'] is not None and h[k][0] is not None:
                    te.append(abs(ang(h[k][0], P[k]['ref'])))
            f = ev['final']
            tr = next((P[k]['t'] for k in range(ev['i'], ev['b'] + 1) if P[k]['ref'] is not None and abs(ang(P[k]['ref'], f)) <= 10), P[ev['b']]['t'])
            th = None
            k = ev['i']
            end = min(len(P) - 1, ev['b'] + 25)
            while k <= end:
                if h[k][0] is not None and abs(ang(h[k][0], f)) <= 10 and all(h[q][0] is not None and abs(ang(h[q][0], f)) <= 15 for q in range(k, min(end, k + 3) + 1)):
                    th = P[k]['t']
                    break
                k += 1
            if th is None:
                cens += 1
            else:
                delays.append(th - tr)
            ev_e = [abs(ang(h[k][0], P[k]['ref'])) for k in range(ev['i'], ev['j'] + 1) if P[k]['ref'] is not None and h[k][0] is not None]
            # 曲がり角ごと（20261122-HDGCORNER）: 遅れ [秒]（入らなければ None）と、その角の中の誤差の最大
            per_turn.append(dict(delay=None if th is None else th - tr, emax=max(ev_e) if ev_e else None))
    return dict(stop95=pct(e, 95), nstop=len(e), turn95=pct(te, 95), nturn=nturn, d50=pct(delays, 50), d90=pct(delays, 90), cens=cens, per_turn=per_turn)


def switches(recs, H):
    n = 0
    walk = 0
    trk = 0
    for r, h in zip(recs, H):
        P = r['P']
        for i in range(len(P)):
            if P[i]['kmh'] < 2:
                continue
            walk += 1
            trk += h[i][1] == 'T'
            if i and h[i][1] != h[i - 1][1] and h[i][1] in 'GT' and h[i - 1][1] in 'GT':
                n += 1
    return n / max(1e-9, walk / 60), trk / max(1, walk)


def settle_after_poor(recs, H):
    """
    精度が悪い所のあと（20261122-D03）: 悪い所の終わりから、HDG が答え 1（ref10）の 10° 以内に入り、そのあと 3 秒 15° 以内にとどまるまでの
    時間（曲がり角の遅れと同じ考え）。SETTLE_WIN_S のうちに落ち着かなければ None。悪い所の始めから終わりの 30 秒後までの、
    1 秒ごとの HDG の変わり方（揺れ）の 95% と、終わりから 30 秒の誤差（答え 1）の 95% も
    """
    per = []
    dh = []
    err = []
    for r, h in zip(recs, H):
        P = r['P']
        for (i, j) in r['C']['poor']:
            t_end = P[j]['t']
            settle = None
            k = j + 1
            while k < len(P) and P[k]['t'] - t_end <= SETTLE_WIN_S:
                ok = lambda q, lim: h[q][0] is not None and P[q]['ref10'] is not None and abs(ang(h[q][0], P[q]['ref10'])) <= lim
                if ok(k, 10):
                    tail = [q for q in range(k, len(P)) if P[q]['t'] - P[k]['t'] <= 3]
                    if all(P[q]['ref10'] is None or (h[q][0] is not None and abs(ang(h[q][0], P[q]['ref10'])) <= 15) for q in tail):
                        settle = P[k]['t'] - t_end
                        break
                k += 1
            ev_dh = []
            for q in range(max(1, i), len(P)):
                if P[q]['t'] - t_end > 30:
                    break
                if h[q][0] is not None and h[q - 1][0] is not None and P[q]['t'] - P[q - 1]['t'] <= 1.01:
                    ev_dh.append(abs(ang(h[q][0], h[q - 1][0])))
                if P[q]['t'] > t_end and P[q]['kmh'] >= 2 and P[q]['ref10'] is not None and h[q][0] is not None:
                    err.append(abs(ang(h[q][0], P[q]['ref10'])))
            dh += ev_dh
            per.append(dict(t=P[i]['t'] - P[0]['t'], len=P[j]['t'] - P[i]['t'] + 1, hmax=max(p['h'] for p in P[i:j + 1]), settle=settle, dh95=pct(ev_dh, 95)))
    s_ok = [p['settle'] for p in per if p['settle'] is not None]
    return dict(events=per, settle50=pct(s_ok, 50), settle_max=max(s_ok) if s_ok else None, unsettled=sum(p['settle'] is None for p in per),
                dh95=pct(dh, 95), err95=pct(err, 95))


def metrics(recs, H):
    sw, trk = switches(recs, H)
    m = dict(a1=err_block(recs, H, 'ref10'), a2=err_block(recs, H, 'line'), dh95=jitter(recs, H), sw=sw, trk=trk)
    m.update(stops_turns(recs, H))
    m['poor'] = settle_after_poor(recs, H)
    # 前半・後半（記録ごとに時刻のまん中で分ける）
    for half in (0, 1):
        sel_recs = []
        for r in recs:
            P = r['P']
            mid = (P[0]['t'] + P[-1]['t']) / 2
            sel_recs.append(mid)
        for key, name in (('ref10', 'a1'), ('line', 'a2')):
            e = []
            for r, h, mid in zip(recs, H, sel_recs):
                for i, p in enumerate(r['P']):
                    if (p['t'] < mid) != (half == 0) or p['kmh'] < 2 or p[key] is None or h[i][0] is None:
                        continue
                    e.append(abs(ang(h[i][0], p[key])))
            m[f'{name}_h{half}'] = dict(N=len(e), med=pct(e, 50), p95=pct(e, 95), over30=sum(x > 30 for x in e) / max(1, len(e)))
    return m


# ---------------------------------------------------------------- 2. GPS の方位と位置から求める向き（同じ点で）

BA_BINS = [(0, 5), (5, 10), (10, 20), (20, 30), (30, 999)]
V_BINS = [(0, 3), (3, 5), (5, 99)]


def gps_vs_track(recs, Ds=(5, 8, 10)):
    """GPS の方位が出ている点（水平精度 15 m 以内・1 km/h 以上）で、GPS の方位そのものと、同じ点の位置から求める向きの誤差"""
    T = {D: [track_series(r['P'], D=D) for r in recs] for D in Ds}
    out = []
    for key in ('ref10', 'line'):
        for vb in [None] + V_BINS:
            for bb in BA_BINS:
                eg = []
                et = {D: [] for D in Ds}
                for ri, r in enumerate(recs):
                    for i, p in enumerate(r['P']):
                        if p['brg'] is None or p[key] is None or p['kmh'] < 1 or (p['h'] is not None and p['h'] > 15):
                            continue
                        if not (bb[0] <= p['ba'] < bb[1]) and not (bb[0] == 30 and p['ba'] >= 30):
                            continue
                        if vb is not None and not (vb[0] <= p['kmh'] < vb[1]):
                            continue
                        if any(T[D][ri][i] is None for D in Ds):
                            continue
                        eg.append(abs(ang(p['brg'], p[key])))
                        for D in Ds:
                            et[D].append(abs(ang(T[D][ri][i], p[key])))
                row = dict(key=key, v=vb, ba=bb, N=len(eg), gps_med=pct(eg, 50), gps_p95=pct(eg, 95))
                for D in Ds:
                    row[f'p{D}_med'] = pct(et[D], 50)
                    row[f'p{D}_p95'] = pct(et[D], 95)
                # 同じ点で GPS の方が小さい割合（D = 8 m と）
                row['gps_better8'] = (sum(1 for a, b in zip(eg, et[8]) if a < b) / len(eg)) if eg else None
                out.append(row)
    return out


# ---------------------------------------------------------------- 3. 出し方と値を探す

def search(recs, quick=False):
    res = []
    cacheT = {}

    def T_of(D=None, kacc=None):
        k = (D, kacc)
        if k not in cacheT:
            cacheT[k] = [track_series(r['P'], D=D if D is not None else 8.0, kacc=kacc) for r in recs]
        return cacheT[k]

    def add(kind, params, H):
        m = metrics(recs, H)
        res.append(dict(kind=kind, params=params, m=m))

    # GPS だけ（自動車の初期値 4 / 6・20° と、歩き向け 1 / 3・30°）
    for en, ex, mb in ((4, 6, 20.0), (1, 3, 30.0)):
        add('GPS', dict(en=en, ex=ex, mb=mb), [gps_only(r['P'], en, ex, mb) for r in recs])
    # 位置だけ: 精度の良い点（水平精度 8 m 以内）が N 点続いてから使う（20261122-D03）。D 3・4 m
    for D in (3, 4):
        for N in range(1, 6):
            T = [track_series(r['P'], D=D, good_run=(POOR_H, N)) for r in recs]
            add('TRACK_ONLY_GOOD', dict(D=D, H=POOR_H, N=N), [track_only(r['P'], t) for r, t in zip(recs, T)])
    # 位置だけ: D = 3〜20 m
    for D in range(3, 21):
        T = T_of(D)
        add('TRACK_ONLY', dict(D=D), [track_only(r['P'], t) for r, t in zip(recs, T)])
    # 位置だけ: D = 水平精度 × k（lo〜hi）
    for k in (0.5, 1.0, 1.5, 2.0):
        for lo in (3, 4, 5, 8):
            for hi in (10, 15, 20):
                if hi <= lo:
                    continue
                T = T_of(None, (k, lo, hi))
                add('TRACK_ONLY_ACC', dict(k=k, lo=lo, hi=hi), [track_only(r['P'], t) for r, t in zip(recs, T)])
    # GPS＋位置: D・止める / 戻す速度・方位精度の上限（20261122-HDGCORNER で D 3・4・6 m を足した）
    for D in (3, 4, 5, 6, 8, 10, 12):
        T = T_of(D)
        for en, ex in ((1, 2), (1, 3), (2, 3), (2, 4), (3, 5), (4, 6)):
            for mb in (20.0, 30.0, 45.0):
                add('GPS_TRACK', dict(D=D, en=en, ex=ex, mb=mb), [gps_track(r['P'], t, en, ex, mb) for r, t in zip(recs, T)])
    # GPS を精度がとても良いときだけ: X°・N 点・D
    for D in (5, 8, 10, 12):
        T = T_of(D)
        for X in (5.0, 10.0, 15.0, 20.0):
            for N in (1, 2, 3, 4, 5):
                add('GOOD_GPS', dict(D=D, X=X, N=N), [good_gps(r['P'], t, X, N) for r, t in zip(recs, T)])
    return res


def summary(recs):
    out = []
    for r in recs:
        P = r['P']
        walk = sum(1 for p in P if p['kmh'] >= 2)
        out.append(dict(
            name=r['name'], points=len(P), minutes=(P[-1]['t'] - P[0]['t']) / 60, walk_min=walk / 60,
            path_m=P[-1]['sp'], stops=len(r['C']['stops']), turns=len(r['C']['turns']),
            ref10=sum(1 for p in P if p['kmh'] >= 2 and p['ref10'] is not None),
            line=sum(1 for p in P if p['kmh'] >= 2 and p['line'] is not None),
            brg=sum(1 for p in P if p['brg'] is not None),
            ba_bins=[sum(1 for p in P if p['brg'] is not None and lo <= p['ba'] < hi) for lo, hi in BA_BINS],
            h50=pct([p['h'] for p in P if p['h'] is not None], 50), h95=pct([p['h'] for p in P if p['h'] is not None], 95),
            kmh50=pct([p['kmh'] for p in P if p['kmh'] >= 2], 50),
            # 曲がり角（20261122-HDGCORNER）: 記録の始めからの秒・曲がった角度・その間のいちばん速い速さ（位置は入れない）
            turn_list=[dict(t=P[t['i']]['t'] - P[0]['t'], angle=t['turn'], dur=P[t['j']]['t'] - P[t['i']]['t'],
                            kmh=max(p['kmh'] for p in P[t['i']:t['j'] + 1])) for t in r['C']['turns']],
        ))
    # 答え 2 の確かめ: GPS の方位精度 5° 以内の点で、答え 2 と GPS の方位の差
    d = [abs(ang(p['brg'], p['line'])) for r in recs for p in r['P'] if p['line'] is not None and p['brg'] is not None and p['ba'] < 5 and p['kmh'] >= 2]
    d1 = [abs(ang(p['ref10'], p['line'])) for r in recs for p in r['P'] if p['line'] is not None and p['ref10'] is not None and p['kmh'] >= 2]
    return dict(records=out, line_vs_gps5=dict(N=len(d), med=pct(d, 50), p95=pct(d, 95)), line_vs_ref10=dict(N=len(d1), med=pct(d1, 50), p95=pct(d1, 95)))


# 見比べる組（20261121-D05-HDGWALK のまとめの表と同じ組）
CANDIDATES = [
    # 歩行の初期値: 20261122-D04 から「位置だけ・D = 水平精度 × 1.0（4〜15 m）」、20261122-D01〜D03 は「位置だけ・4 m」、
    # 20261121-D06 は「位置だけ・8 m」、その前は「GPS＋位置・1 / 3 km/h・30°・8 m」。3 m は 20261122-WALK3 で詰めないと決めたが、比べに残す
    ('今の歩行の初期値（位置だけ・D = 水平精度 × 1.0（4〜15 m））', 'TRACK_ONLY_ACC', dict(k=1.0, lo=4, hi=15)),
    ('20261122-D01〜D03 の歩行の初期値（位置だけ・4 m）', 'TRACK_ONLY', dict(D=4)),
    ('位置だけ・3 m', 'TRACK_ONLY', dict(D=3)),
    ('20261121-D06 の歩行の初期値（位置だけ・8 m）', 'TRACK_ONLY', dict(D=8)),
    ('20261121-D05 までの歩行の初期値（GPS＋位置・1 / 3 km/h・30°・8 m）', 'GPS_TRACK', dict(D=8, en=1, ex=3, mb=30.0)),
    ('位置だけ・10 m', 'TRACK_ONLY', dict(D=10)),
    ('位置だけ・D = 水平精度 × 1.5（3〜10 m）', 'TRACK_ONLY_ACC', dict(k=1.5, lo=3, hi=10)),
    ('GPS＋位置・4 / 6 km/h・30°・10 m', 'GPS_TRACK', dict(D=10, en=4, ex=6, mb=30.0)),
    ('GPS は精度 5° 以内が 3 点続いたら・10 m', 'GOOD_GPS', dict(D=10, X=5.0, N=3)),
    ('GPS は精度 5° 以内が 2 点続いたら・12 m', 'GOOD_GPS', dict(D=12, X=5.0, N=2)),
    ('GPS だけ（自動車の初期値 4 / 6 km/h・20°）', 'GPS', dict(en=4, ex=6, mb=20.0)),
]


def find(res, kind, params):
    return next(x for x in res if x['kind'] == kind and x['params'] == params)


def print_candidates(res, out=sys.stdout):
    f = lambda v, d=1: '—' if v is None else f'{v:.{d}f}'
    print('出し方 | 答え1 中央/95%・30°超・最長 | 答え2 中央/95%・30°超・最長 | 止まり95% | 曲がり95%・遅れ中央/90% | 揺れ95% | 入れ替わり/分 | 前半/後半 95%（答え1・答え2）', file=out)
    for name, kind, params in CANDIDATES:
        m = find(res, kind, params)['m']
        a, b = m['a1'], m['a2']
        print(f"{name} | {f(a['med'])}/{f(a['p95'])}・{a['over30'] * 100:.1f}%・{a['stuck']}秒 | {f(b['med'])}/{f(b['p95'])}・{b['over30'] * 100:.1f}%・{b['stuck']}秒"
              f" | {f(m['stop95'])} | {f(m['turn95'], 0)}・{f(m['d50'], 0)}/{f(m['d90'], 0)}秒 | {f(m['dh95'])} | {f(m['sw'])}"
              f" | {f(m['a1_h0']['p95'])}/{f(m['a1_h1']['p95'])}・{f(m['a2_h0']['p95'])}/{f(m['a2_h1']['p95'])}", file=out)


def main():
    ap = argparse.ArgumentParser(description='歩くときの方位の出し方の調べ（20261121-D05-HDGWALK）')
    ap.add_argument('paths', nargs='+', help='track.csv か、track.csv のあるフォルダ')
    ap.add_argument('--out', required=True, help='結果の JSON')
    a = ap.parse_args()
    t0 = time.time()
    recs = [setup(p) for p in a.paths]
    out = dict(summary=summary(recs), gps_vs_track=gps_vs_track(recs), search=search(recs))
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    with open(a.out, 'w', encoding='utf-8') as h:
        json.dump(out, h, ensure_ascii=False)
    print_candidates(out['search'])
    print(f'{len(recs)} 本・{sum(len(r["P"]) for r in recs)} 点・{len(out["search"])} 通り（{time.time() - t0:.0f} 秒）→ {a.out}', file=sys.stderr)


if __name__ == '__main__':
    main()
