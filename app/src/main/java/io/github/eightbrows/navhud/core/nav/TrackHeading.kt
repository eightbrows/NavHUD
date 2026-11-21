package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Fix

/**
 * 位置から求める向き（画面の付記 TRK。20261121-D01。調べ 20261119-D03-WALK の「P 8 m」）。Android に依存しない。
 *
 * 点ごとに、直前に distanceM（8 m）を歩いた所の点と今の点を結ぶ向きにする。歩いた距離は速さの積分（RateTracker.segmentM と同じ。
 * 速さがない区間は直線の距離）。次の点では向きを変えない（前の向きのまま）:
 * - 速さが Tuning.TRACK_HEADING_MIN_KMH（1 km/h）未満・速さがない（止まっている間は変えない）
 * - 水平精度が maxAccM（設定「使う位置精度の上限」。初期値 15 m）を超える（その点は結ぶ相手にもしない）
 * - 結ぶ相手が見つからない（歩いた距離が足りない・Tuning.TRACK_HEADING_MAX_AGE_SEC より古い・2 点の直線の距離が
 *   歩いた距離の Tuning.TRACK_HEADING_MIN_CHORD_RATIO 倍より短い）
 * 点の間が gapMs（設定「測位が途切れたとみなす時間」）を超えたら（測位の欠け）、それまでの点と向きを捨てる（欠けをまたいで結ばない）。
 * 使うのは方位の出し方が「GPS ＋ 位置」のときだけ（HeadingSelector.select）。
 */
class TrackHeading(
    private val distanceM: Double = Tuning.TRACK_HEADING_DISTANCE_M,
    private val minSpeedMps: Double = Tuning.TRACK_HEADING_MIN_KMH / 3.6,
    private val maxAgeMs: Long = Tuning.TRACK_HEADING_MAX_AGE_SEC * 1000L,
    private val minChordRatio: Double = Tuning.TRACK_HEADING_MIN_CHORD_RATIO,
) {
    private class Pt(val timeMs: Long, val lat: Double, val lon: Double, val pathM: Double)

    /** 結ぶ相手にできる点（水平精度が上限以内の点。古い順） */
    private val history = ArrayDeque<Pt>()
    private var prev: Fix? = null
    private var pathM = 0.0

    /** 今の向き [°]（真北から時計回り）。まだなければ null */
    var deg: Float? = null
        private set

    /**
     * Fix を 1 つ入れる。
     * @param maxAccM 使う位置精度の上限 [m]
     * @param gapMs 点の間がこれを超えたら測位の欠けとみなす [ms]
     */
    fun update(fix: Fix, maxAccM: Float, gapMs: Long) {
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
        val speed = fix.speedMps
        if (good && speed != null && speed >= minSpeedMps) {
            for (q in history.asReversed()) {
                if (fix.timeMs - q.timeMs > maxAgeMs) break
                if (pathM - q.pathM >= distanceM && Geo.distanceM(q.lat, q.lon, fix.lat, fix.lon) >= minChordRatio * distanceM) {
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

    fun reset() {
        history.clear()
        prev = null
        pathM = 0.0
        deg = null
    }
}
