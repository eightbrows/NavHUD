package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Fix

/**
 * 位置から求める向き（画面の付記 TRK。20261121-D01。調べ 20261119-D03-WALK の「P 8 m」）。Android に依存しない。
 *
 * 点ごとに、直前に D m を歩いた所の点と今の点を結ぶ向きにする。D は、その点の水平精度に合わせて伸ばす（20261122-D04。distanceFor）:
 * D = max(distanceM, min(Tuning.TRACK_HEADING_ACC_MAX_M（15 m）, 水平精度 × Tuning.TRACK_HEADING_ACC_FACTOR（1.0）))。distanceM は移動のプリセットの
 * 「向きを求める距離」（自動車 8 m・歩行 4 m）で、精度が良いときはこの距離、悪いときは長くなる。歩いた距離は速さの積分（RateTracker.segmentM と同じ。
 * 速さがない区間は直線の距離）。次の点では向きを変えない（前の向きのまま）:
 * - 向きを変えてよい点でない（TurnGate。決まりはそこだけ）: 速さが Tuning.TRACK_HEADING_MIN_KMH（1 km/h）未満・速さがない、または
 *   Tuning.TRACK_HEADING_MOVE_SEC（5 秒）前の位置から、Tuning.TRACK_HEADING_MOVE_KMH（3 km/h）× 5 秒 = 4.2 m 離れていない
 *   （20261124-D01。止まっている間・止まりの前後のゆっくり動く所で、位置の揺れで向きが回らないように。調べ 20261122-STOPHOLD）
 * - 水平精度が maxAccM（設定「使う位置精度の上限」。初期値 15 m）を超える（その点は結ぶ相手にもしない）
 * - 結ぶ相手が見つからない（歩いた距離が足りない・Tuning.TRACK_HEADING_MAX_AGE_SEC より古い・2 点の直線の距離が
 *   歩いた距離の Tuning.TRACK_HEADING_MIN_CHORD_RATIO 倍より短い）
 * 点の間が gapMs（設定「測位が途切れたとみなす時間」）を超えたら（測位の欠け）、それまでの点と向きを捨てる（欠けをまたいで結ばない）。
 * 使うのは方位の出し方が「GPS ＋ 位置」のときだけ（HeadingSelector.select）。
 */
class TrackHeading(
    private val minSpeedMps: Double = Tuning.TRACK_HEADING_MIN_KMH / 3.6,
    private val maxAgeMs: Long = Tuning.TRACK_HEADING_MAX_AGE_SEC * 1000L,
    private val minChordRatio: Double = Tuning.TRACK_HEADING_MIN_CHORD_RATIO,
    /** 向きを変えてよい点の「この秒数前の位置から」（20261124-D01。0 なら決まりを使わない。テストで前の形と比べるため） */
    moveSec: Int = Tuning.TRACK_HEADING_MOVE_SEC,
) {
    private class Pt(val timeMs: Long, val lat: Double, val lon: Double, val pathM: Double)

    /** 結ぶ相手にできる点（水平精度が上限以内の点。古い順） */
    private val history = ArrayDeque<Pt>()

    /** 向きを変えてよい点か（20261124-D01） */
    private val gate = TurnGate(minSpeedMps, moveSec * 1000L, Tuning.TRACK_HEADING_MOVE_KMH / 3.6 * moveSec)
    private var prev: Fix? = null
    private var pathM = 0.0

    /** 今の向き [°]（真北から時計回り）。まだなければ null */
    var deg: Float? = null
        private set

    /**
     * Fix を 1 つ入れる。
     * @param maxAccM 使う位置精度の上限 [m]
     * @param gapMs 点の間がこれを超えたら測位の欠けとみなす [ms]
     * @param distanceM 結ぶ相手までに歩いた距離の下限 [m]（移動のプリセットの「向きを求める距離」。精度が悪いと distanceFor で伸ばす）
     */
    fun update(fix: Fix, maxAccM: Float, gapMs: Long, distanceM: Double = Tuning.TRACK_HEADING_DISTANCE_M) {
        val p = prev
        if (p != null) {
            val dt = fix.timeMs - p.timeMs
            if (dt < 0 || dt > gapMs) {
                reset()
            } else {
                pathM += RateTracker.segmentM(p, fix)
            }
        }
        prev = fix
        val good = fix.horizAccM == null || fix.horizAccM <= maxAccM
        // 向きを変えてよいか（決まりは TurnGate だけ。点ごとに必ず呼ぶ: 直近の位置を覚えるため）
        val mayTurn = gate.allow(fix)
        if (good && mayTurn) {
            val d = distanceFor(fix.horizAccM, distanceM)
            for (q in history.asReversed()) {
                if (fix.timeMs - q.timeMs > maxAgeMs) break
                if (pathM - q.pathM >= d && Geo.distanceM(q.lat, q.lon, fix.lat, fix.lon) >= minChordRatio * d) {
                    deg = Geo.bearingDeg(q.lat, q.lon, fix.lat, fix.lon).toFloat()
                    break
                }
            }
        }
        if (good) {
            history.addLast(Pt(fix.timeMs, fix.lat, fix.lon, pathM))
            while (fix.timeMs - history.first().timeMs > maxAgeMs) history.removeFirst()
        }
    }

    companion object {
        /**
         * その点で結ぶ相手までに歩いた距離 [m]（20261122-D04）: 水平精度 × 倍率を、設定の距離より短くしない・上限（15 m）より長くしない。
         * 設定の距離が上限より長いときは設定の距離。水平精度がない点は設定の距離
         */
        fun distanceFor(horizAccM: Float?, settingM: Double): Double {
            val byAcc = (horizAccM?.toDouble() ?: 0.0) * Tuning.TRACK_HEADING_ACC_FACTOR
            return maxOf(settingM, minOf(Tuning.TRACK_HEADING_ACC_MAX_M, byAcc))
        }
    }

    fun reset() {
        gate.reset()
        history.clear()
        prev = null
        pathM = 0.0
        deg = null
    }
}

/**
 * 位置から求める向きを変えてよい点か（20261124-D01）。決まりはここだけに置く（あとで歩数を足すため）。Android に依存しない。
 * - 端末の速さが minSpeedMps（1 km/h）以上
 * - moveSec（Tuning.TRACK_HEADING_MOVE_SEC。5 秒）前の位置（それより前でいちばん新しい点。水平精度によらず）から、
 *   Tuning.TRACK_HEADING_MOVE_KMH（3 km/h）× moveSec = 4.2 m 以上離れている。まだ moveSec 秒ぶんの点がなければ変えない
 *   （止まっている間の位置の揺れは、行ったり来たりで積み重ならない。調べ 20261122-STOPHOLD の「5 秒前の位置から 4.2 m」）
 * allow は点ごとに必ず 1 回呼ぶ（直近の位置を覚える）。測位の欠け・時刻の戻りでは reset（TrackHeading.reset）。
 */
class TurnGate(
    private val minSpeedMps: Double = Tuning.TRACK_HEADING_MIN_KMH / 3.6,
    private val moveMs: Long = Tuning.TRACK_HEADING_MOVE_SEC * 1000L,
    private val moveM: Double = Tuning.TRACK_HEADING_MOVE_KMH / 3.6 * Tuning.TRACK_HEADING_MOVE_SEC,
) {
    private class Pos(val timeMs: Long, val lat: Double, val lon: Double)

    /** 直近の点（古い順）。先頭は moveMs 以上前の点のうちいちばん新しい点（まだなければ最初の点） */
    private val recent = ArrayDeque<Pos>()

    fun allow(fix: Fix): Boolean {
        recent.addLast(Pos(fix.timeMs, fix.lat, fix.lon))
        while (recent.size > 1 && fix.timeMs - recent[1].timeMs >= moveMs) recent.removeFirst()
        val q = recent.first()
        val moved = fix.timeMs - q.timeMs >= moveMs && Geo.distanceM(q.lat, q.lon, fix.lat, fix.lon) >= moveM
        val speed = fix.speedMps
        return speed != null && speed >= minSpeedMps && moved
    }

    fun reset() = recent.clear()
}
