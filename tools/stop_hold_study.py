"""
止まっている間の向きの調べ（20261122-STOPHOLD）。アプリのコードは変えない調べ用。

使い方:
    python tools/stop_hold_study.py <歩きの track.csv かフォルダ> [...] [--car <車の track.csv かフォルダ>] --out <結果の JSON>

    例: python tools/stop_hold_study.py sample/walk/session_20261121_132427 sample/walk/session_20261122_121430 \
            sample/walk/session_20261122_132237 --car sample/session_20260814_075234 --out build/stophold.json

    - 読み込み・答え・物差しは tools/hdg_walk_study.py と同じ（そのファイルを読み込んで使う）。
    - 結果の JSON には物差しの数字と、止まりの前後で向きが変わった点の値（記録の始めからの秒・速さ・水平精度・動いた距離）だけを入れる
      （緯度・経度は入れない）。Python 3 の標準のライブラリだけで動く。

中身:
    - 位置から求める向き（今の歩行: D = 水平精度 × 1.0 を 4〜15 m に収めたもの）を、「向きを変えてよい点」の決まり（gate）だけ変えて比べる。
      どの決まりも過去の点だけを使う（ライブで同じ計算ができる）。
        now        今の形: 端末の速さが 1 km/h 以上
        run N X    端末の速さが N 点続けて X km/h を超える
        away D     速さが 1 km/h 未満になった点（止まりに入った位置）から D m 以上離れるまで変えない（k なら D = max(D, 水平精度 × k)）
        disp T L   T 秒前の位置から L m 以上離れている（位置の差から出した速さ。止まっている間の揺れは積み重ならない）
        steps N    直近 N 秒（経過時間）に歩数（steps.csv の detector）がある（20261124-D01。steps.csv がない記録は今の歩行と同じ）
    - 止まり（hdg_walk_study.scenes。端末の速さ 0 が 4 秒以上）の前後の遅い所も含めた「止まりの区間」を作り、
      区間の中で向きが変わった点、区間の中で回った角度の最大、歩き出しの遅れを数える。
"""
import argparse
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import hdg_walk_study as S  # noqa: E402

ang, pct, bearing = S.ang, S.pct, S.bearing
WALK_KMH = 3.0      # 歩いているとみなす速さ [km/h]（止まりの区間の端: これ以上の点が 3 点続く所）
SETTLE_WIN_S = 60


# ---------------------------------------------------------------- 向きを変えてよい点の決まり（過去の点だけ）

class Now:
    def __init__(s, kmh=1.0):
        s.kmh = kmh

    def reset(s):
        pass

    def allow(s, P, i):
        return P[i]['kmh'] >= s.kmh


class Run:
    """端末の速さが N 点続けて X km/h を超える"""

    def __init__(s, N, X):
        s.N, s.X = N, X
        s.run = 0

    def reset(s):
        s.run = 0

    def allow(s, P, i):
        s.run = s.run + 1 if P[i]['kmh'] > s.X else 0
        return s.run >= s.N and P[i]['kmh'] >= 1.0


class Away:
    """速さが 1 km/h 未満になった点の位置から D m（k なら max(D, 水平精度 × k)）離れるまで変えない"""

    def __init__(s, D, k=None):
        s.D, s.k = D, k
        s.anchor = None

    def reset(s):
        s.anchor = None

    def allow(s, P, i):
        p = P[i]
        if p['kmh'] < 1.0:
            if s.anchor is None:
                s.anchor = (p['e'], p['n'])
            return False
        if s.anchor is not None:
            d = s.D if s.k is None else max(s.D, s.k * (p['h'] or 0.0))
            if math.hypot(p['e'] - s.anchor[0], p['n'] - s.anchor[1]) >= d:
                s.anchor = None
            else:
                return False
        return True


class Disp:
    """T 秒前（それより前で一番近い点）の位置から L m 以上離れている"""

    def __init__(s, T, L):
        s.T, s.L = T, L
        s.hist = []

    def reset(s):
        s.hist = []

    def allow(s, P, i):
        p = P[i]
        s.hist.append(i)
        while len(s.hist) > 1 and p['t'] - P[s.hist[1]]['t'] >= s.T:
            s.hist.pop(0)
        q = P[s.hist[0]]
        if p['t'] - q['t'] < s.T:
            return False
        return p['kmh'] >= 1.0 and math.hypot(p['e'] - q['e'], p['n'] - q['n']) >= s.L


class Both:
    def __init__(s, *g):
        s.g = g

    def reset(s):
        for x in s.g:
            x.reset()

    def allow(s, P, i):
        r = [x.allow(P, i) for x in s.g]   # どれも状態を進める
        return all(r)


def track_series_gate(P, gate, kacc=(1.0, 4, 15), D=None, max_acc=15.0, gap_s=10.0, age_s=90.0, minr=0.6):
    """hdg_walk_study.track_series と同じ計算で、向きを変えてよい点の決まり（速さ 1 km/h 以上）だけを gate にしたもの"""
    out = []
    hist = []
    Spath = 0.0
    deg = None
    prev = None
    gate.reset()
    for i, p in enumerate(P):
        if prev is not None:
            dt = p['t'] - prev['t']
            if dt > gap_s or dt < 0:
                hist = []
                deg = None
                Spath = 0.0
                gate.reset()
            elif p['v'] is not None and prev['v'] is not None and dt <= 2.0:
                Spath += (p['v'] + prev['v']) / 2 * dt
            else:
                Spath += math.hypot(p['e'] - prev['e'], p['n'] - prev['n'])
        prev = p
        good = p['h'] is None or p['h'] <= max_acc
        ok = gate.allow(P, i)
        if D is not None:
            Dp = D
        else:
            k, lo, hi = kacc
            Dp = min(hi, max(lo, k * (p['h'] or 0.0)))
        if good and ok:
            for q in reversed(hist):
                if p['t'] - q[0] > age_s:
                    break
                if Spath - q[3] >= Dp and math.hypot(p['e'] - q[1], p['n'] - q[2]) >= minr * Dp:
                    deg = bearing((q[1], q[2]), (p['e'], p['n']))
                    break
        if good:
            hist.append((p['t'], p['e'], p['n'], Spath))
            while hist and p['t'] - hist[0][0] > age_s:
                hist.pop(0)
        out.append(deg)
    return out


# ---------------------------------------------------------------- 止まりの区間

def episodes(rec):
    """止まり（scenes の stops）ごとに、前後の遅い所を含めた区間 [a, b)。a: さかのぼって WALK_KMH 以上が 3 点続く所の次、
    b: 先へ進んで WALK_KMH 以上が 3 点続く最初の点（歩き出し）。重なる区間はまとめる。欠け（10 秒）はまたがない"""
    P = rec['P']
    n = len(P)

    def walking(k):
        return all(0 <= q < n and P[q]['kmh'] >= WALK_KMH for q in (k, k + 1, k + 2))

    out = []
    for s in rec['C']['stops']:
        a = s['i']
        while a > 0 and not walking(a - 3) and P[a]['t'] - P[a - 1]['t'] <= 10 and s['i'] - a < 60:
            a -= 1
        b = s['j'] + 1
        while b < n and not walking(b) and P[b]['t'] - P[b - 1]['t'] <= 10:
            b += 1
        if out and a <= out[-1][1]:
            out[-1] = (out[-1][0], max(b, out[-1][1]), out[-1][2] + [s])
        else:
            out.append((a, b, [s]))
    return out


def episode_metrics(rec, H, eps):
    """区間ごと: 区間の直前の向きから回った角度の最大、区間の中で向きが変わった点、歩き出しの遅れ"""
    P = rec['P']
    n = len(P)
    res = []
    for a, b, stops in eps:
        h0 = H[a - 1][0] if a > 0 else None
        rot = None
        if h0 is not None:
            v = [abs(ang(H[k][0], h0)) for k in range(a, min(b, n)) if H[k][0] is not None]
            rot = max(v) if v else 0.0
        changes = []
        for k in range(max(a, 1), min(b, n)):
            if H[k][0] is not None and H[k - 1][0] is not None and H[k][0] != H[k - 1][0]:
                q = P[k - 1]
                p = P[k]
                dt = p['t'] - q['t']
                changes.append(dict(t=round(p['t'] - P[0]['t']), kmh=round(p['kmh'], 1),
                                    kmh_pos=round(math.hypot(p['e'] - q['e'], p['n'] - q['n']) / dt * 3.6, 1) if dt > 0 else None,
                                    h=round(p['h'], 1) if p['h'] is not None else None,
                                    moved=round(math.hypot(p['e'] - P[a]['e'], p['n'] - P[a]['n']), 1),
                                    dh=round(ang(H[k][0], H[k - 1][0]), 1)))
        # 歩き出しの遅れ: b から、向きが答え 1 の 10° 以内に入り、そのあと 3 秒 15° 以内にとどまるまで
        settle = None
        if b < n:
            k = b
            while k < n and P[k]['t'] - P[b]['t'] <= SETTLE_WIN_S:
                okk = H[k][0] is not None and P[k]['ref10'] is not None and abs(ang(H[k][0], P[k]['ref10'])) <= 10
                if okk:
                    tail = [q for q in range(k, n) if P[q]['t'] - P[k]['t'] <= 3]
                    if all(P[q]['ref10'] is None or (H[q][0] is not None and abs(ang(H[q][0], P[q]['ref10'])) <= 15) for q in tail):
                        settle = P[k]['t'] - P[b]['t']
                        break
                k += 1
        res.append(dict(t=round(P[a]['t'] - P[0]['t']), len=round(P[min(b, n - 1)]['t'] - P[a]['t']), stop_s=sum(round(P[s['j']]['t'] - P[s['i']]['t']) for s in stops),
                        rot=rot, changes=changes, settle=settle))
    return res


def summarize(eps_all):
    """記録の始めの区間（前の向きがない）は、回った角度・歩き出しの遅れに入れない"""
    eps_all = [e for e in eps_all if e['t'] > 0]
    rots = [e['rot'] for e in eps_all if e['rot'] is not None]
    st = [e['settle'] for e in eps_all if e['settle'] is not None]
    return dict(n=len(eps_all), rot50=pct(rots, 50), rot95=pct(rots, 95), rot_max=max(rots) if rots else None, rot_over30=sum(r > 30 for r in rots),
                changes=sum(len(e['changes']) for e in eps_all),
                settle50=pct(st, 50), settle_max=max(st) if st else None, unsettled=sum(e['settle'] is None for e in eps_all))


def gates():
    g = [('今の形（1 km/h 以上）', 'now', {}, lambda: Now())]
    for N in (2, 3, 4, 5):
        for X in (1, 2, 3):
            g.append((f'速さが {N} 点続けて {X} km/h を超える', 'run', dict(N=N, X=X), lambda N=N, X=X: Run(N, X)))
    for D in (3, 4, 5, 6, 8, 10):
        g.append((f'止まりに入った位置から {D} m', 'away', dict(D=D), lambda D=D: Away(D)))
    for D in (3, 4, 5):
        for k in (1.0, 1.5):
            g.append((f'止まりに入った位置から max({D} m, 水平精度 × {k})', 'away_acc', dict(D=D, k=k), lambda D=D, k=k: Away(D, k)))
    for T in (2, 3, 5):
        for X in (1.5, 2.0, 3.0):
            L = X * T / 3.6
            g.append((f'{T} 秒前の位置から {L:.2f} m（{X} km/h）', 'disp', dict(T=T, X=X), lambda T=T, L=L: Disp(T, L)))
    for N, X in ((2, 2), (3, 2)):
        for D in (3, 5):
            g.append((f'速さが {N} 点続けて {X} km/h ＋ 止まりから {D} m', 'run_away', dict(N=N, X=X, D=D), lambda N=N, X=X, D=D: Both(Run(N, X), Away(D))))
    # 歩数を使う形（20261124-D01。steps.csv がある記録だけ。ない記録は今の歩行（5 秒前の位置から 4.2 m）と同じ）
    for N in (1, 2, 3, 4):
        g.append((f'直近 {N} 秒に歩数（detector）がある', 'steps', dict(N=N), lambda N=N: Steps(N, 'only')))
        g.append((f'直近 {N} 秒に歩数 ＋ 5 秒前の位置から 4.2 m', 'steps_and_move', dict(N=N), lambda N=N: Steps(N, 'and')))
        g.append((f'直近 {N} 秒に歩数 または 5 秒前の位置から 4.2 m', 'steps_or_move', dict(N=N), lambda N=N: Steps(N, 'or')))
    return g


class Walk:
    """
    歩いているかの判定（20261125-D01）。その点までに届いた歩（recv ≤ その点）と、その点の GPS の速さだけを使う（リアルタイム）。
    kind: 'or' = 過去 N 秒に K 歩以上 または 速さ ≥ v（GpsLogger のおすすめは N 2・K 2・v 0.3）、'and' = 両方、'steps' = 歩だけ、
    'gps' = 速さだけ、'now' = 今の形（端末の速さ 1 km/h 以上・5 秒前の位置から 4.2 m）。
    TurnGate に入れるとき: mode 'replace'（この判定だけ）・'add'（今の形と両方）。歩数のない・使えない記録は今の形と同じ
    """

    def __init__(s, kind, N=2, K=2, v=0.3, mode='replace'):
        s.kind, s.N, s.K, s.v, s.mode = kind, N, K, v, mode
        s.now = Disp(S.MOVE[0], S.MOVE[1])

    def reset(s):
        s.now.reset()

    def walking(s, P, i):
        p = P[i]
        st = p['det_n'][s.N] >= s.K
        gp = (p['v'] or 0.0) >= s.v
        return {'or': st or gp, 'and': st and gp, 'steps': st, 'gps': gp}[s.kind]

    def allow(s, P, i):
        now = s.now.allow(P, i)
        if s.kind == 'now' or not P[i].get('has_steps'):
            return now
        w = s.walking(P, i)
        return w and now if s.mode == 'add' else w and P[i]['kmh'] >= 1.0


def truth(rec):
    """
    答え（あとから記録全体を見て決める。20261125-D01）: detector の歩の時刻を、間が 1.5 秒以内のものでまとめた塊のうち、
    10 歩以上で、塊の中の GPS の速さの中央が 0.5 m/s 以上のものを「歩いた」とし、塊の最初の歩から最後の歩 + 0.5 秒までを歩いている、
    ほかを止まっているとする（止まっている間の揺れの短い塊（2〜11 歩・速さ 0）は止まり）。点ごとの True / False と、歩いた塊の一覧
    """
    P = rec['P']
    det = rec['steps']['det']
    bouts = []
    cur = []
    for e in det:
        if cur and e - cur[-1] > 1_500_000_000:
            bouts.append(cur)
            cur = []
        cur.append(e)
    if cur:
        bouts.append(cur)
    walk = []
    for b in bouts:
        v = sorted(p['v'] or 0.0 for p in P if b[0] <= p['el'] <= b[-1])
        if len(b) >= 10 and v and v[len(v) // 2] >= 0.5:
            walk.append((b[0], b[-1] + 500_000_000, len(b)))
    lab = [any(a <= p['el'] <= z for a, z, _ in walk) for p in P]
    return lab, walk


def walk_metrics(rec, lab, walk, det):
    """判定の物差し: 正解の割合・歩きを止まりと誤る割合・止まりを歩きと誤る割合・歩き出しを見つけるまでの秒数（5 秒以上止まったあとの歩き出し）"""
    P = rec['P']
    pred = []
    det.reset()
    for i, p in enumerate(P):
        if i and P[i]['t'] - P[i - 1]['t'] > 10:
            det.reset()
        now = det.now.allow(P, i)   # 今の形（5 秒前の位置）の状態は、どの判定でも点ごとに進める
        pred.append(now if det.kind == 'now' else det.walking(P, i))
    n = len(P)
    ok = sum(1 for a, b in zip(lab, pred) if a == b)
    nw = sum(lab)
    ns = n - nw
    w2s = sum(1 for a, b in zip(lab, pred) if a and not b)
    s2w = sum(1 for a, b in zip(lab, pred) if not a and b)
    delays = []
    for a, z, _ in walk:
        before = [p for p in P if a - 5_000_000_000 <= p['el'] < a]
        if len(before) < 4 or any(lb for p, lb in zip(P, lab) if a - 5_000_000_000 <= p['el'] < a):
            continue
        hit = next((p for p, pr in zip(P, pred) if p['el'] >= a and pr), None)
        if hit is not None and hit['el'] <= z:
            delays.append((hit['el'] - a) / 1e9)
        else:
            delays.append(None)
    return dict(n=n, walk=nw, stop=ns, ok=ok, w2s=w2s, s2w=s2w, delays=delays)


def walk_detectors():
    d = [('GpsLogger のおすすめ（過去 2 秒に 2 歩以上 または 速さ ≥ 0.3 m/s）', 'or', dict(N=2, K=2, v=0.3), lambda: Walk('or', 2, 2, 0.3)),
         ('同じ条件で「かつ」', 'and', dict(N=2, K=2, v=0.3), lambda: Walk('and', 2, 2, 0.3)),
         ('GPS の速さだけ（≥ 0.3 m/s）', 'gps', dict(v=0.3), lambda: Walk('gps', v=0.3)),
         ('今の形（速さ 1 km/h 以上・5 秒前の位置から 4.2 m）', 'now', {}, lambda: Walk('now'))]
    for N in (1, 2, 3, 4):
        for K in (1, 2, 3):
            d.append((f'歩数だけ（過去 {N} 秒に {K} 歩以上）', 'steps', dict(N=N, K=K), lambda N=N, K=K: Walk('steps', N, K)))
    return d


def walk_gates():
    g = [('今の形（速さ 1 km/h 以上・5 秒前の位置から 4.2 m）', 'now', {}, lambda: Walk('now'))]
    for mode, mname in (('replace', '置き換え'), ('add', '今の形に足す')):
        g.append((f'GpsLogger のおすすめ（2 秒 2 歩 または 0.3 m/s）・{mname}', 'or', dict(mode=mode), lambda mode=mode: Walk('or', 2, 2, 0.3, mode)))
        g.append((f'「かつ」（2 秒 2 歩 かつ 0.3 m/s）・{mname}', 'and', dict(mode=mode), lambda mode=mode: Walk('and', 2, 2, 0.3, mode)))
        for N, K in ((2, 2), (3, 2), (3, 3), (4, 3)):
            g.append((f'歩数だけ（{N} 秒に {K} 歩）・{mname}', 'steps', dict(N=N, K=K, mode=mode), lambda N=N, K=K, mode=mode: Walk('steps', N, K, mode=mode)))
    return g


class Steps:
    """
    歩数を使う形（20261124-D01）: 直近 N 秒（経過時間。その点の経過時間以前）に detector があるか。mode = only（歩数と速さ 1 km/h 以上）・
    and（＋ 5 秒前の位置から 4.2 m）・or（どちらか）。steps.csv がない・使えない記録では、今の歩行（5 秒前の位置から 4.2 m）と同じ
    """

    def __init__(s, N, mode):
        s.N, s.mode = N, mode
        s.fb = Disp(S.MOVE[0], S.MOVE[1])

    def reset(s):
        s.fb.reset()

    def allow(s, P, i):
        fb = s.fb.allow(P, i)
        p = P[i]
        if not p.get('has_steps'):
            return fb
        st = p['det_age'] is not None and p['det_age'] <= s.N and p['kmh'] >= 1.0
        if s.mode == 'only':
            return st
        if s.mode == 'and':
            return st and fb
        return st or fb


def main():
    ap = argparse.ArgumentParser(description='止まっている間の向きの調べ（20261122-STOPHOLD）')
    ap.add_argument('walk', nargs='+')
    ap.add_argument('--car')
    ap.add_argument('--out', required=True)
    a = ap.parse_args()
    walks = [S.setup(w) for w in a.walk]
    eps = [episodes(r) for r in walks]
    out = dict(records=[r['name'] for r in walks], episodes=[[dict(a=e[0], b=e[1], stops=len(e[2])) for e in x] for x in eps], walk=[])
    for name, kind, params, mk in gates():
        Ts = [track_series_gate(r['P'], mk()) for r in walks]
        Hs = [S.track_only(r['P'], T) for r, T in zip(walks, Ts)]
        row = dict(name=name, kind=kind, params=params, per=[], all=None)
        epm = [episode_metrics(r, H, e) for r, H, e in zip(walks, Hs, eps)]
        for r, H, em in zip(walks, Hs, epm):
            m = S.metrics([r], [H])
            row['per'].append(dict(m=m, ep=summarize(em), eps=em if kind == 'now' else [dict(rot=e['rot'], settle=e['settle'], n=len(e['changes'])) for e in em]))
        m = S.metrics(walks, Hs)
        row['all'] = dict(m=m, ep=summarize([e for em in epm for e in em]))
        out['walk'].append(row)
        print(name, file=sys.stderr)
    # 歩いている / 止まっているの判定と、それを TurnGate に入れた向き（20261125-D01。歩数を使える記録だけ。判定は歩数のある記録すべて）
    with_steps = [r for r in walks if r['steps']['status'] == 'ok']
    if with_steps:
        truths = [truth(r) for r in with_steps]
        out['walkdet_records'] = [dict(name=r['name'], walk_bouts=len(t[1]), walk_pts=sum(t[0]), pts=len(r['P'])) for r, t in zip(with_steps, truths)]
        out['walkdet'] = []
        for name, kind, params, mk in walk_detectors():
            per = [walk_metrics(r, t[0], t[1], mk()) for r, t in zip(with_steps, truths)]
            out['walkdet'].append(dict(name=name, kind=kind, params=params, per=per))
        out['walkgate'] = []
        for name, kind, params, mk in walk_gates():
            Ts = [track_series_gate(r['P'], mk()) for r in walks]
            Hs = [S.track_only(r['P'], T) for r, T in zip(walks, Ts)]
            epm = [episode_metrics(r, H, e) for r, H, e in zip(walks, Hs, eps)]
            row = dict(name=name, kind=kind, params=params, per=[], all=None, series=[[h[0] for h in H] for H in Hs])
            for r, H, em in zip(walks, Hs, epm):
                row['per'].append(dict(m=S.metrics([r], [H]), ep=summarize(em)))
            sel = [k for k, r in enumerate(walks) if r['steps']['status'] == 'ok']
            row['all_steps'] = dict(m=S.metrics([walks[k] for k in sel], [Hs[k] for k in sel]), ep=summarize([e for k in sel for e in epm[k]])) if sel else None
            row['all'] = dict(m=S.metrics(walks, Hs), ep=summarize([e for em in epm for e in em]))
            out['walkgate'].append(row)
            print('walkgate', name, file=sys.stderr)
    if a.car:
        car = S.setup(a.car)
        out['car'] = []
        for name, kind, params, mk in gates():
            # 自動車の位置だけ（D = max(8, 水平精度 × 1.0)・15 m まで）と、GPS＋位置（4 / 6 km/h・20°）
            T = track_series_gate(car['P'], mk(), kacc=(1.0, 8, 15))
            for method, H in (('TRACK_ONLY', S.track_only(car['P'], T, 4, 6, 20.0)), ('GPS_TRACK', S.gps_track(car['P'], T, 4, 6, 20.0))):
                out['car'].append(dict(name=name, kind=kind, params=params, method=method, m=S.metrics([car], [H])))
            print('car', name, file=sys.stderr)
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    json.dump(out, open(a.out, 'w', encoding='utf-8'), ensure_ascii=False)
    print('ok', len(out['walk']), file=sys.stderr)


if __name__ == '__main__':
    main()
