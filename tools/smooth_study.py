"""
歩行の位置のなめらかさと予測の線の調べ（20261122-SMOOTH）。アプリのコードは変えない調べ用。

使い方:
    python tools/smooth_study.py <歩きの track.csv かフォルダ> [...] [--car <車の track.csv かフォルダ>] --out <結果の JSON>

    例: python tools/smooth_study.py sample/walk/session_20261121_132427 --car sample/session_20260814_075234 --out build/smooth.json

    - 歩きの記録はいくつでも渡せる（20261122-WALK3）。記録ごとに計算して、物差しは全部の記録の点・曲がり角を合わせて数える。
    - 車の記録（--car）は予測の線だけに使う。渡さなければ車は計算しない。

    - 読み込み・答え・物差しは tools/hdg_walk_study.py と同じ（そのファイルを読み込んで使う）。
    - 結果の JSON には物差しの数字だけを入れる（位置・座標は入れない）。
    - Python 3 の標準のライブラリだけで動く。

中身:
    1. 向き: なめらかにした位置から向きを求める（過去の点だけ。ライブで同じ計算ができる）
       - 位置から求める向き（D m）・D = 水平精度 × 1.0（4〜15 m）
       - 位置の平均（直近 N 点）どうしを結ぶ
       - α-β フィルタ（位置だけ）
       - カルマンフィルタ（等速の形。位置だけ / 位置と GPS の速度（速さと方位から）。跳んだ点を割り引く形も）
       - 参考: 前後の点を使う RTS スムーザ（REPLAY だけで使える形）
    2. 軌跡の線のジグザグ（表示だけ）: 間隔 3・5・10 m、なめらか（読み込んだ軌跡は前後の点のガウス、走った跡はカルマンの推定位置）
    3. 予測の線: 今の速さと曲がり方（向きの変わる速さ）が続いたら T 秒後にどこにいるか（CTRV の形）
"""
import argparse
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import hdg_walk_study as S  # noqa: E402

ang, pct, bearing = S.ang, S.pct, S.bearing
KMH = 3.6


# ---------------------------------------------------------------- 1. 向き（過去の点だけ）

def hold_series(P, vec_at):
    """vec_at(i) が (向き or None) を返す。1 km/h 未満の点では向きを変えない（今の TRK と同じ）。欠けで捨てる"""
    out = []
    deg = None
    for i, p in enumerate(P):
        if i and (p['t'] - P[i - 1]['t'] > 10 or p['t'] < P[i - 1]['t']):
            deg = None
        if p['kmh'] >= 1.0:
            d = vec_at(i)
            if d is not None:
                deg = d
        out.append(deg)
    return out


def ma_series(P, N, D, max_acc=15.0):
    """直近 N 点（精度 max_acc 以内・欠けをまたがない）の位置の平均を s_i とし、歩いた距離 D 以上前の s_j と結ぶ向き"""
    sm = [None] * len(P)
    win = []
    for i, p in enumerate(P):
        if i and p['t'] - P[i - 1]['t'] > 10:
            win = []
        if p['h'] is None or p['h'] <= max_acc:
            win.append(i)
            win = win[-N:]
        if win:
            sm[i] = (sum(P[k]['e'] for k in win) / len(win), sum(P[k]['n'] for k in win) / len(win), P[i]['sp'])
    hist = []

    def at(i):
        nonlocal hist
        s = sm[i]
        if s is None:
            return None
        for j in reversed(hist):
            if P[i]['t'] - P[j]['t'] > 90 or P[j]['gseg'] != P[i]['gseg']:
                break
            sj = sm[j]
            if s[2] - sj[2] >= D and math.hypot(s[0] - sj[0], s[1] - sj[1]) >= 0.6 * D:
                return bearing((sj[0], sj[1]), (s[0], s[1]))
        return None
    out = []
    deg = None
    for i, p in enumerate(P):
        if i and (p['t'] - P[i - 1]['t'] > 10):
            deg = None
            hist = []
        if p['kmh'] >= 1.0:
            d = at(i)
            if d is not None:
                deg = d
        if sm[i] is not None:
            hist.append(i)
            hist = [j for j in hist if P[i]['t'] - P[j]['t'] <= 90]
        out.append(deg)
    return out


def ab_filter(P, alpha, gate=None):
    """α-β フィルタ（位置だけ。β = α²/(2−α)）。gate = k なら、残差が k × max(水平精度, 3 m) を超える点は α・β を 1/5 にする（跳んだ点を割り引く）。
    戻り値: 点ごとの (東, 北, 速度東, 速度北)"""
    beta = alpha * alpha / (2 - alpha)
    st = []
    x = v = None
    prev_t = None
    for p in P:
        z = (p['e'], p['n'])
        if x is None or p['t'] - prev_t > 10 or p['t'] < prev_t:
            x = list(z)
            v = [0.0, 0.0]
        else:
            dt = p['t'] - prev_t
            x = [x[0] + v[0] * dt, x[1] + v[1] * dt]
            r = [z[0] - x[0], z[1] - x[1]]
            a, b = alpha, beta
            if gate is not None and math.hypot(*r) > gate * max(p['h'] or 3.0, 3.0):
                a, b = alpha / 5, beta / 5
            x = [x[0] + a * r[0], x[1] + a * r[1]]
            v = [v[0] + b * r[0] / dt, v[1] + b * r[1] / dt]
        prev_t = p['t']
        st.append((x[0], x[1], v[0], v[1]))
    return st


def _mm(A, B):
    return [[sum(A[i][k] * B[k][j] for k in range(len(B))) for j in range(len(B[0]))] for i in range(len(A))]


def _tr(A):
    return [list(r) for r in zip(*A)]


def _inv(A):
    """小さな正方行列の逆（ガウスの消去）"""
    n = len(A)
    M = [list(A[i]) + [1.0 if i == j else 0.0 for j in range(n)] for i in range(n)]
    for c in range(n):
        piv = max(range(c, n), key=lambda r: abs(M[r][c]))
        M[c], M[piv] = M[piv], M[c]
        d = M[c][c]
        M[c] = [x / d for x in M[c]]
        for r in range(n):
            if r != c and M[r][c] != 0:
                f = M[r][c]
                M[r] = [x - f * y for x, y in zip(M[r], M[c])]
    return [row[n:] for row in M]


def _FQ(dim, dt, q):
    """等速（dim 2: 位置・速度）・等加速度（dim 3: 位置・速度・加速度）の移り変わりと雑音（白色の加速度 / 白色の加加速度）"""
    if dim == 2:
        F = [[1, dt], [0, 1]]
        Q = [[q * dt ** 3 / 3, q * dt ** 2 / 2], [q * dt ** 2 / 2, q * dt]]
    else:
        F = [[1, dt, dt * dt / 2], [0, 1, dt], [0, 0, 1]]
        Q = [[q * dt ** 5 / 20, q * dt ** 4 / 8, q * dt ** 3 / 6], [q * dt ** 4 / 8, q * dt ** 3 / 3, q * dt ** 2 / 2], [q * dt ** 3 / 6, q * dt ** 2 / 2, q * dt]]
    return F, Q


def _upd(x, Pm, row, z, r):
    """1 つの測り（状態の row 番目 = z、分散 r）で直す"""
    s_ = Pm[row][row] + r
    K = [Pm[i][row] / s_ for i in range(len(x))]
    y = z - x[row]
    x = [x[i] + K[i] * y for i in range(len(x))]
    Pm = [[Pm[i][j] - K[i] * Pm[row][j] for j in range(len(x))] for i in range(len(x))]
    return x, Pm


def kf_filter(P, q, use_vel=False, gate=None, keep=False, dim=2, ba_max=30.0):
    """カルマンフィルタ（軸ごとに独立。dim 2 = 等速の形 [位置, 速度]、dim 3 = 等加速度の形 [位置, 速度, 加速度]。q は雑音の強さ）。
    位置の測り: 分散 max(水平精度, 2 m)²。use_vel なら、GPS の方位精度が 30° 以内で速さ 0.5 m/s 以上の点で、
    速さと方位から作った速度も測りとして入れる（分散 max(0.3, 速さ × 方位精度[rad])²）。
    gate = k なら、マハラノビスの距離² が k² を超える位置の測りは分散を (d²/k²) 倍にする（跳んだ点を割り引く）。
    keep なら RTS スムーザ用に (dt, 予測の x, 予測の P, 推定の x, 推定の P) を残す。
    戻り値: 点ごとの (東, 北, 速度東, 速度北, 加速度東, 加速度北) と残したもの"""
    out = []
    saved = []
    X = None
    for i, p in enumerate(P):
        rp = max(p['h'] or 5.0, 2.0) ** 2
        if X is None or p['t'] - P[i - 1]['t'] > 10 or p['t'] < P[i - 1]['t']:
            X = []
            for z in (p['e'], p['n']):
                x = [z, 0.0] + ([0.0] if dim == 3 else [])
                Pm = [[rp, 0, 0], [0, 4.0, 0], [0, 0, 1.0]] if dim == 3 else [[rp, 0], [0, 4.0]]
                X.append((x, Pm))
            out.append((p['e'], p['n'], 0.0, 0.0, 0.0, 0.0))
            saved.append((0, None, None, [list(a[0]) for a in X], [a[1] for a in X]) if keep else None)
            continue
        dt = p['t'] - P[i - 1]['t']
        F, Q = _FQ(dim, dt, q)
        Ft = _tr(F)
        pred = []
        for ax in range(2):
            x, Pm = X[ax]
            x = [sum(F[r][c] * x[c] for c in range(dim)) for r in range(dim)]
            Pm = [[a + b for a, b in zip(ra, rb)] for ra, rb in zip(_mm(_mm(F, Pm), Ft), Q)]
            X[ax] = (x, Pm)
            pred.append((list(x), Pm))
        if gate is not None:
            d2 = sum((z - X[ax][0][0]) ** 2 / (X[ax][1][0][0] + rp) for ax, z in enumerate((p['e'], p['n'])))
            if d2 > gate * gate:
                rp *= d2 / (gate * gate)
        for ax, z in enumerate((p['e'], p['n'])):
            X[ax] = _upd(X[ax][0], X[ax][1], 0, z, rp)
        if use_vel and p['brg'] is not None and p['ba'] is not None and p['ba'] <= ba_max and p['v'] is not None and p['v'] >= 0.5:
            rv = max(0.3, p['v'] * math.radians(p['ba'])) ** 2
            vz = (p['v'] * math.sin(math.radians(p['brg'])), p['v'] * math.cos(math.radians(p['brg'])))
            for ax in range(2):
                X[ax] = _upd(X[ax][0], X[ax][1], 1, vz[ax], rv)
        (xe, Pe), (xn, Pn) = X
        out.append((xe[0], xn[0], xe[1], xn[1], xe[2] if dim == 3 else 0.0, xn[2] if dim == 3 else 0.0))
        saved.append((dt, [a[0] for a in pred], [a[1] for a in pred], [list(xe), list(xn)], [Pe, Pn]) if keep else None)
    return out, saved


def rts_series(P, q):
    """参考: 前後の点を使う RTS スムーザ（位置だけの等速のカルマンフィルタを後ろから戻す）。REPLAY だけで使える形"""
    est, saved = kf_filter(P, q, keep=True)
    n = len(P)
    out = list(est)
    sm = None
    for i in range(n - 1, -1, -1):
        dt, _, _, xf, Pf = saved[i]
        if i == n - 1 or saved[i + 1][1] is None:
            sm = xf
        else:
            dtn, xp, Pp, _, _ = saved[i + 1]
            F, _ = _FQ(2, dtn, q)
            new = []
            for ax in range(2):
                C = _mm(_mm(Pf[ax], _tr(F)), _inv(Pp[ax]))
                d = [sm[ax][k] - xp[ax][k] for k in range(2)]
                new.append([xf[ax][r] + sum(C[r][k] * d[k] for k in range(2)) for r in range(2)])
            sm = new
        out[i] = (sm[0][0], sm[1][0], sm[0][1], sm[1][1], 0.0, 0.0)
    return out


def vel_heading(P, est, vmin=0.3):
    """推定した速度の向き（速さが vmin m/s 未満なら変えない）"""
    def at(i):
        ve, vn = est[i][2], est[i][3]
        return bearing((0, 0), (ve, vn)) if math.hypot(ve, vn) >= vmin else None
    return hold_series(P, at)


def heading_groups(P):
    """比べる組: (家族, 名前, 値, 向きの列)"""
    g = []
    for D in (3, 4, 5, 6, 8, 10, 12):
        g.append(('位置から求める向き', f'D {D} m', D, S.track_series(P, D=D)))
    g.append(('水平精度に合わせる', 'D = 水平精度 × 1.0（4〜15 m）', 1.0, S.track_series(P, kacc=(1.0, 4, 15))))
    for N in (3, 5, 8):
        for D in (2, 4, 6):
            g.append((f'位置の平均 N {N}', f'直近 {N} 点の平均どうし・D {D} m', D, ma_series(P, N, D)))
    for a in (0.6, 0.4, 0.25, 0.15, 0.1):
        g.append(('α-β', f'α-β α {a}', a, vel_heading(P, ab_filter(P, a))))
        g.append(('α-β・跳びを割り引く', f'α-β α {a}・跳びを割り引く', a, vel_heading(P, ab_filter(P, a, gate=3.0))))
    for q in (20.0, 10.0, 5.0, 3.0, 2.0, 1.0, 0.5, 0.2, 0.1, 0.05):
        g.append(('カルマン・位置だけ', f'カルマン 位置だけ q {q}', q, vel_heading(P, kf_filter(P, q)[0])))
        g.append(('カルマン・位置だけ・跳びを割り引く', f'カルマン 位置だけ q {q}・跳びを割り引く', q, vel_heading(P, kf_filter(P, q, gate=3.0)[0])))
        g.append(('カルマン・位置と GPS の速度・跳びを割り引く', f'カルマン 位置と速度 q {q}・跳びを割り引く', q, vel_heading(P, kf_filter(P, q, use_vel=True, gate=3.0)[0])))
        if q == 0.5:
            for bm in (10.0, 20.0):
                g.append(('カルマン・位置と GPS の速度（方位精度の上限を変える）', f'カルマン 位置と速度（{bm:.0f}° 以内） q {q}・跳びを割り引く', q, vel_heading(P, kf_filter(P, q, use_vel=True, gate=3.0, ba_max=bm)[0])))
        g.append(('カルマン・位置と GPS の速度（方位精度 15° 以内）・跳びを割り引く', f'カルマン 位置と速度（15° 以内） q {q}・跳びを割り引く', q, vel_heading(P, kf_filter(P, q, use_vel=True, gate=3.0, ba_max=15.0)[0])))
    g.append(('参考（前後の点）', 'RTS スムーザ q 0.5（REPLAY だけ）', 0.5, vel_heading(P, rts_series(P, 0.5))))
    return g


# ---------------------------------------------------------------- 2. 軌跡の線のジグザグ

def thin_xy(pts, step):
    if not pts:
        return []
    out = [pts[0]]
    for p in pts[1:]:
        if math.hypot(p[0] - out[-1][0], p[1] - out[-1][1]) >= step:
            out.append(p)
    if out[-1] is not pts[-1]:
        out.append(pts[-1])
    return out


def gauss_xy(P, sigma):
    """読み込んだ軌跡: 前後の点（歩いた距離で ±3σ）をガウスの重みで平均した位置（欠けをまたがない・精度 15 m を超える点は使わない）"""
    out = []
    n = len(P)
    for i, p in enumerate(P):
        we = wn = ws = 0.0
        j = i
        while j >= 0 and P[j]['gseg'] == p['gseg'] and p['sp'] - P[j]['sp'] <= 3 * sigma:
            j -= 1
        k = i
        while k < n and P[k]['gseg'] == p['gseg'] and P[k]['sp'] - p['sp'] <= 3 * sigma:
            k += 1
        for m in range(j + 1, k):
            q = P[m]
            if q['h'] is not None and q['h'] > 15:
                continue
            w = math.exp(-0.5 * ((q['sp'] - p['sp']) / sigma) ** 2)
            we += w * q['e']
            wn += w * q['n']
            ws += w
        out.append((we / ws, wn / ws, i) if ws > 0 else (p['e'], p['n'], i))
    return out


def line_groups(P):
    raw = [(p['e'], p['n'], i) for i, p in enumerate(P)]
    g = [('読み込んだ軌跡', f'間隔 {s} m', thin_xy(raw, s)) for s in (3, 5, 10)]
    for sg in (2, 3, 4, 6):
        g.append(('読み込んだ軌跡', f'間隔 3 m・なめらか（前後 σ {sg} m）', thin_xy(gauss_xy(P, sg), 3)))
    g += [('走った跡', f'間隔 {s} m', thin_xy(raw, s)) for s in (1.5, 2.5, 5)]
    for q in (1.0, 0.3, 0.1):
        est = kf_filter(P, q, gate=3.0)[0]
        g.append(('走った跡', f'間隔 1.5 m・なめらか（カルマン q {q}）', thin_xy([(e[0], e[1], i) for i, e in enumerate(est)], 1.5)))
    for sg in (2, 3):
        g.append(('走った跡', f'間隔 1.5 m・なめらか（前後 σ {sg} m。{3 * sg} m 遅れて決まる）', thin_xy(gauss_xy(P, sg), 1.5)))
    return g


def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    L2 = dx * dx + dy * dy
    t = 0.0 if L2 == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / L2))
    return math.hypot(px - ax - t * dx, py - ay - t * dy)


def line_metrics(items):
    """items = [(P, C, pts)]（記録ごと）。
    ジグザグ: まっすぐな所（答え 2 がある点の続き）での、線の向きの変わり方の合計 [° / 100 m]。
    角: 曲がり角の真ん中の記録の位置から、線までのいちばん近い距離（なめらかにすると角が内側に削れる）。点の数も"""
    turn_sum = 0.0
    length = 0.0
    corner = []
    npts = 0
    for P, C, pts in items:
        t_, l_, c_ = _line_raw(P, C, pts)
        turn_sum += t_
        length += l_
        corner += c_
        npts += len(pts)
    return dict(zig=turn_sum / max(length, 1e-9) * 100, corner50=pct(corner, 50), corner_max=max(corner) if corner else None, corners=corner, points=npts)


def _line_raw(P, C, pts):
    turn_sum = 0.0
    length = 0.0
    straight = [p['line'] is not None for p in P]
    for a, b, c in zip(pts, pts[1:], pts[2:]):
        if not (straight[a[2]] and straight[b[2]] and straight[c[2]]):
            continue
        h1 = bearing((a[0], a[1]), (b[0], b[1]))
        h2 = bearing((b[0], b[1]), (c[0], c[1]))
        turn_sum += abs(ang(h2, h1))
        length += math.hypot(c[0] - b[0], c[1] - b[1])
    corner = []
    for ev in C['turns']:
        m = (ev['i'] + ev['j']) // 2
        ex, ey = P[m]['e'], P[m]['n']
        best = min(seg_dist(ex, ey, a[0], a[1], b[0], b[1]) for a, b in zip(pts, pts[1:]))
        corner.append(best)
    return turn_sum, length, corner


# ---------------------------------------------------------------- 3. 予測の線

def unwrap(degs):
    out = []
    for d in degs:
        if d is None:
            out.append(None)
            continue
        prev = next((x for x in reversed(out) if x is not None), None)
        if prev is None:
            out.append(d)
        else:
            out.append(prev + ang(d, prev % 360))
    return out


def yaw_rate(P, H, kind, W):
    """曲がり方 [°/s]。kind = 'zero'（まっすぐ）・'reg'（直近 W 秒の向きの回帰の傾き）・'ema'（1 秒ごとの変わり方の、時定数 W 秒の一次遅れ）"""
    n = len(P)
    if kind == 'zero':
        return [0.0] * n
    if kind == 'ca':
        # 等加速度のカルマン（位置と GPS の速度・跳びを割り引く。q = W）の速度と加速度から、向きの変わる速さ dθ/dt = (vn·ae − ve·an) / v²
        est = kf_filter(P, W, use_vel=True, gate=3.0, dim=3)[0]
        out = []
        for x in est:
            ve, vn, ae, an = x[2], x[3], x[4], x[5]
            v2 = ve * ve + vn * vn
            out.append(math.degrees((vn * ae - ve * an) / v2) if v2 >= 0.25 else 0.0)
        return out
    u = unwrap([h[0] for h in H])
    out = []
    ema = 0.0
    for i, p in enumerate(P):
        if kind == 'reg':
            xs, ys = [], []
            j = i
            while j >= 0 and p['t'] - P[j]['t'] <= W and P[j]['gseg'] == p['gseg']:
                if u[j] is not None and P[j]['kmh'] >= 1.0:
                    xs.append(P[j]['t'] - p['t'])
                    ys.append(u[j])
                j -= 1
            if len(xs) >= 3:
                mx, my = sum(xs) / len(xs), sum(ys) / len(ys)
                sxx = sum((x - mx) ** 2 for x in xs)
                out.append(sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / sxx if sxx > 0 else 0.0)
            else:
                out.append(0.0)
        else:
            if i and u[i] is not None and u[i - 1] is not None and 0 < p['t'] - P[i - 1]['t'] <= 2 and p['kmh'] >= 1.0:
                dt = p['t'] - P[i - 1]['t']
                r = (u[i] - u[i - 1]) / dt
                ema += (1 - math.exp(-dt / W)) * (r - ema)
            elif i and (p['t'] - P[i - 1]['t'] > 10):
                ema = 0.0
            out.append(ema)
    return out


def path(e0, n0, th, v, w, Tl, shape, step=0.5):
    """予測の線: 向き th [°]・速さ v [m/s]・曲がり方 w [°/s] から Tl 秒先までの点（step 秒ごと、自機を含む）。
    shape: ('ctrv',) = 曲がり方がそのまま続く / ('cap', A) = 線の先までで曲がる角度を A° までに抑える /
           ('decay', tau) = 曲がり方が時定数 tau 秒で弱まる（曲がりはそのうち終わる）"""
    if shape[0] == 'cap':
        lim = shape[1] / Tl
        w = max(-lim, min(lim, w))
    pts = [(e0, n0)]
    e, n = e0, n0
    t = 0.0
    while t < Tl - 1e-9:
        tm = t + step / 2
        if shape[0] == 'decay':
            hd = th + w * shape[1] * (1 - math.exp(-tm / shape[1]))
        else:
            hd = th + w * tm
        r = math.radians(hd)
        e += v * step * math.sin(r)
        n += v * step * math.cos(r)
        t += step
        pts.append((e, n))
    return pts


def speed_series(P, sec=2):
    """GPS の速さの、直前 sec 秒の平均 [m/s]"""
    out = []
    for i, p in enumerate(P):
        vs = [P[j]['v'] for j in range(i, -1, -1) if p['t'] - P[j]['t'] < sec and P[j]['v'] is not None]
        out.append(sum(vs) / len(vs) if vs else 0.0)
    return out


def pred_paths(rec, H, W, Tl, shape, wmax, pos=None, vmin=0.5):
    """点ごとの予測の線（描かない点は None）"""
    P = rec['P']
    spd = rec.setdefault('_spd', speed_series(P))
    out = []
    for i, p in enumerate(P):
        h = H[i][0]
        if h is None or spd[i] < vmin or p['kmh'] < 1.0:
            out.append(None)
            continue
        e0, n0 = (pos[i][0], pos[i][1]) if pos else (p['e'], p['n'])
        out.append(path(e0, n0, h, spd[i], max(-wmax, min(wmax, W[i])), Tl, shape))
    return out


def _pred_raw(rec, paths, Ts, step=0.5):
    """予測の線の物差し（1 本の記録の生の値。まとめるのは pred_metrics）。
    当たり: T 秒後の予測の位置と、記録で T 秒後にいた位置の差。まっすぐ（答え 1 の向きの変わり < 15°）・曲がり（>= 30°）・
    止まり（いまの速さ < 1 km/h。線は出さないので、止まった所にいるとした差）。
    揺れ: まっすぐな所（答え 2 がある）で、線の先が 1 秒前から左右に動いた距離。
    曲がり角: 曲がり始めから、線の先の向き（自機から見て）が曲がる前の道の向きより曲がる側に 20° 以上向き、2 秒とどまるまでの秒数（向く）と、
    曲がったあとの道の向きの ±20° に入るまで（合う）。行き過ぎ: 曲がり角の間で、線の先の向きが曲がったあとの道の向きを越えた角度の最大"""
    P = rec['P']
    n = len(P)
    by_t = {round(p['t']): i for i, p in enumerate(P)}
    key = 'ref10' if rec['kind'] == 'walk' else 'ref'
    res = {}
    for T in Ts:
        k_t = int(round(T / step))
        err = dict(straight=[], curve=[], stop=[], turning=[])
        jit = []
        for i, p in enumerate(P):
            j = by_t.get(round(p['t'] + T))
            if j is None or P[j]['gseg'] != p['gseg']:
                continue
            if paths[i] is None:
                if p['kmh'] < 1.0:
                    err['stop'].append(math.hypot(P[j]['e'] - p['e'], P[j]['n'] - p['n']))
                continue
            pe = paths[i][k_t]
            d = math.hypot(P[j]['e'] - pe[0], P[j]['n'] - pe[1])
            a0, a1 = p[key], P[j][key]
            if a0 is not None and a1 is not None:
                turn = abs(ang(a1, a0))
                if turn < 15:
                    err['straight'].append(d)
                elif turn >= 30:
                    err['curve'].append(d)
                # 曲がっている最中（直前 3 秒で答えの向きが 10° 以上変わり、T 秒後までに同じ側へさらに 15° 以上曲がる）
                b3 = by_t.get(round(p['t'] - 3))
                if b3 is not None and P[b3][key] is not None:
                    past = ang(a0, P[b3][key])
                    if abs(past) >= 10 and ang(a1, a0) * (1 if past > 0 else -1) >= 15:
                        err['turning'].append(d)
            if i and paths[i - 1] is not None and p['line'] is not None and P[i - 1]['line'] is not None and p['t'] - P[i - 1]['t'] <= 1.01:
                q = paths[i - 1][k_t]
                h = bearing(paths[i][0], paths[i][1]) if paths[i][1] != paths[i][0] else 0.0
                r = math.radians(h)
                dx, dy = pe[0] - q[0], pe[1] - q[1]
                jit.append(abs(dx * math.cos(r) - dy * math.sin(r)))
        res[str(T)] = dict(err=err, jit=jit)
    # 曲がり角（線の先 = いちばん長い所）
    toward, match, over = [], [], []

    def endb(q):
        if paths[q] is None:
            return None
        return bearing(paths[q][0], paths[q][-1])
    for ev in rec['C']['turns']:
        sgn = 1 if ev['turn'] > 0 else -1
        pre = P[ev['a']][key] if P[ev['a']][key] is not None else P[ev['a']]['ref']
        fin = ev['final']

        def first(cond):
            k = ev['i']
            while k < n and P[k]['t'] - P[ev['i']]['t'] <= 40:
                if cond(k) and all(cond(q) for q in range(k, min(n, k + 6)) if 0 <= P[q]["t"] - P[k]["t"] <= 2):
                    return P[k]['t'] - P[ev['i']]['t']
                k += 1
            return None
        toward.append(first(lambda q: endb(q) is not None and sgn * ang(endb(q), pre) >= 20))
        match.append(first(lambda q: endb(q) is not None and abs(ang(endb(q), fin)) <= 20))
        ov = [sgn * ang(endb(q), fin) for q in range(ev['i'], min(n, ev['b'] + 10)) if endb(q) is not None]
        over.append(max(0.0, max(ov)) if ov else None)
    res['turn'] = (toward, match, over)
    return res


def pred_metrics(items, Ts):
    """items = [(rec, paths)]（記録ごと）。点・曲がり角を合わせて数える"""
    raws = [_pred_raw(rec, paths, Ts) for rec, paths in items]
    res = {}
    for T in Ts:
        err = {k: [x for r in raws for x in r[str(T)]['err'][k]] for k in ('straight', 'curve', 'stop', 'turning')}
        jit = [x for r in raws for x in r[str(T)]['jit']]
        res[str(T)] = dict(straight50=pct(err['straight'], 50), straight95=pct(err['straight'], 95), nstraight=len(err['straight']),
                           curve50=pct(err['curve'], 50), curve95=pct(err['curve'], 95), ncurve=len(err['curve']),
                           stop50=pct(err['stop'], 50), stop95=pct(err['stop'], 95), turning50=pct(err['turning'], 50), turning95=pct(err['turning'], 95), nturning=len(err['turning']),
                           jit50=pct(jit, 50), jit95=pct(jit, 95))
    toward = [x for r in raws for x in r['turn'][0]]
    match = [x for r in raws for x in r['turn'][1]]
    over = [x for r in raws for x in r['turn'][2]]
    ok = lambda v: [x for x in v if x is not None]
    res['turn'] = dict(toward50=pct(ok(toward), 50), toward90=pct(ok(toward), 90), toward_none=sum(x is None for x in toward),
                       match50=pct(ok(match), 50), match90=pct(ok(match), 90), match_none=sum(x is None for x in match),
                       over50=pct(ok(over), 50), over90=pct(ok(over), 90), nturn=len(toward), toward=toward, match=match, over=over)
    return res


PRED_SHAPES = [('ctrv',), ('cap', 90.0), ('cap', 45.0), ('decay', 2.0), ('decay', 4.0)]
PRED_YAWS = [('zero', 0), ('reg', 3), ('reg', 5), ('ema', 2), ('ema', 4), ('ca', 0.2)]


def avg_heading(P, H, sec=5, min_m=2.0, min_res=0.3):
    """平均した方位（20261122-D03 の AvgHeading と同じ）: 直近 sec 秒の HDG を、前の点からの動いた距離（速さの台形）で重みを付けて
    ベクトルで平均。動いた距離が min_m 未満・ベクトルの長さが動いた距離の min_res 未満なら前の向き。欠け（10 秒）で捨てる"""
    out = []
    win = []
    deg = None
    for i, p in enumerate(P):
        gap = i and (p['t'] - P[i - 1]['t'] > 10 or p['t'] < P[i - 1]['t'])
        if gap:
            win = []
            deg = None
        w = (p['v'] + P[i - 1]['v']) / 2 * (p['t'] - P[i - 1]['t']) if i and not gap else 0.0
        if H[i][0] is not None:
            win.append((p['t'], H[i][0], w))
        win = [x for x in win if p['t'] - x[0] < sec]
        tot = sum(x[2] for x in win)
        if tot >= min_m:
            se = sum(x[2] * math.sin(math.radians(x[1])) for x in win)
            ce = sum(x[2] * math.cos(math.radians(x[1])) for x in win)
            if math.hypot(se, ce) >= min_res * tot:
                deg = math.degrees(math.atan2(se, ce)) % 360
        out.append((deg, 'A'))
    return out


def pred_heads(rec):
    """予測の線の向きと位置: 今の出し方（記録の位置）と、カルマン（位置と速度）の推定"""
    P = rec['P']
    if rec['kind'] == 'walk':
        est = kf_filter(P, 0.5, use_vel=True, gate=3.0, ba_max=15.0)[0]
        cur = S.track_only(P, S.track_series(P, D=4))
        return [('今の歩行（位置だけ 4 m）', cur, None),
                ('平均した方位（5 秒）', avg_heading(P, cur), None),
                ('カルマン（位置と速度 15°・q 0.5）', S.track_only(P, vel_heading(P, est)), est)]
    est = kf_filter(P, 2.0, use_vel=True, gate=3.0, ba_max=20.0)[0]
    return [('今の自動車（GPS・4 / 6 km/h）', S.gps_only(P, 4, 6, 20.0), None),
            ('カルマン（位置と速度 20°・q 2）', S.track_only(P, vel_heading(P, est, vmin=1.0)), est)]


PRED_CFG = dict(walk=dict(Ts=(5, 10), Tl=10, wmax=30.0), car=dict(Ts=(10, 20, 30), Tl=30, wmax=15.0))


def main():
    ap = argparse.ArgumentParser(description='歩行の位置のなめらかさと予測の線の調べ（20261122-SMOOTH・20261122-WALK3）')
    ap.add_argument('walk', nargs='+', help='歩きの track.csv か、track.csv のあるフォルダ（いくつでも）')
    ap.add_argument('--car', help='車の track.csv か、track.csv のあるフォルダ（予測の線だけ）')
    ap.add_argument('--out', required=True)
    a = ap.parse_args()
    walks = []
    for w in a.walk:
        r = S.setup(w)
        r['kind'] = 'walk'
        walks.append(r)
    out = dict(records=[r['name'] for r in walks])
    # 1. 向き（記録ごとに同じ順で組を作り、まとめて数える）
    groups = [heading_groups(r['P']) for r in walks]
    rows = []
    for k, (fam, name, val, _) in enumerate(groups[0]):
        Hs = [S.track_only(r['P'], g[k][3]) for r, g in zip(walks, groups)]
        m = S.metrics(walks, Hs)
        dl = [t['delay'] for t in m['per_turn'] if t['delay'] is not None]
        rows.append(dict(fam=fam, name=name, val=val, m=m, delay_mean=sum(dl) / len(dl) if dl else None))
    out['heading'] = rows
    # 2. 軌跡の線
    lg = [line_groups(r['P']) for r in walks]
    out['lines'] = [dict(kind=kind, name=nm, **line_metrics([(r['P'], r['C'], g[k][2]) for r, g in zip(walks, lg)])) for k, (kind, nm, _) in enumerate(lg[0])]
    # 3. 予測の線
    recsets = [walks]
    if a.car:
        car = S.setup(a.car)
        car['kind'] = 'car'
        recsets.append([car])
    pred = []
    for recs in recsets:
        cfg = PRED_CFG[recs[0]['kind']]
        heads = [pred_heads(r) for r in recs]
        for hk, (hname, _, _) in enumerate(heads[0]):
            for wk, Wsec in (PRED_YAWS if hname != '平均した方位（5 秒）' else PRED_YAWS[:1]):
                Ws = [yaw_rate(r['P'], h[hk][1], wk, Wsec) for r, h in zip(recs, heads)]
                for shape in (PRED_SHAPES if wk != 'zero' else PRED_SHAPES[:1]):
                    items = [(r, pred_paths(r, h[hk][1], W, cfg['Tl'], shape, cfg['wmax'], h[hk][2])) for r, h, W in zip(recs, heads, Ws)]
                    pred.append(dict(rec=recs[0]['kind'], head=hname, yaw=wk, W=Wsec, shape=list(shape), m=pred_metrics(items, cfg['Ts'])))
                    print(recs[0]['kind'], hname, wk, Wsec, shape, file=sys.stderr)
    out['pred'] = pred
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    json.dump(out, open(a.out, 'w', encoding='utf-8'), ensure_ascii=False)
    print('ok', len(walks), len(rows), len(out['lines']), len(pred), file=sys.stderr)


if __name__ == '__main__':
    main()
