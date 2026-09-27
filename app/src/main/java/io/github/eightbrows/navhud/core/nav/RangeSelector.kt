package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.geo.EN

/**
 * 縮尺（ARC では基準の距離環が左右端に接する距離、North Up では最外周の距離環）の選び方。
 * 画面の大きさが分かっていれば「次の WP が表示枠に収まるか」（RangeFit）で、分からなければ距離で選ぶ。
 * 対地速度は将来「速度に応じた縮尺」を足すためのもので、今は使わない。
 */
object RangeAuto {

    /** 選べる縮尺の全段 [km]。NavSettings の有効リストでこの中から使う段を選ぶ。 */
    val ALL_STEPS_KM = listOf(0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0)

    /**
     * AUTO で選びたい縮尺 [m]。次の WP が「縮尺 × fitRatio」以内に収まる最小の段。どの段にも収まらなければ最大の段。
     * 次の WP がなければ null（今の縮尺のまま）。
     * @param stepsM 有効な段 [m]（昇順）
     * @param groundSpeedMps 対地速度（今は判定に使わない）
     */
    @Suppress("UNUSED_PARAMETER")
    fun desired(stepsM: List<Double>, nextWpDistanceM: Double?, groundSpeedMps: Float?, fitRatio: Double = 0.9): Double? {
        if (nextWpDistanceM == null) return null
        return desired(stepsM) { nextWpDistanceM <= it * fitRatio }
    }

    /**
     * AUTO で選びたい縮尺 [m]。fits（その縮尺で次の WP が表示枠に収まるか）が true になる最小の段。
     * どの段にも収まらなければ最大の段。
     */
    fun desired(stepsM: List<Double>, fits: (rangeM: Double) -> Boolean): Double? {
        if (stepsM.isEmpty()) return null
        return stepsM.firstOrNull(fits) ?: stepsM.last()
    }
}

/**
 * 地図の表示枠（ボタン列などを除いた領域）の寸法を知っている側（core/view の HudViewport）が実装する。
 * AUTO 縮尺の判定と、PAN の始点・ドラッグ量の換算に使う。
 */
interface MapViewport {
    /** 縮尺 rangeM のとき、自機から見て target（東 m・北 m）にある点が表示枠に余白付きで収まるか。 */
    fun fits(rangeM: Double, target: EN, headingDeg: Double?, settings: NavSettings): Boolean

    /** 通常の表示（PAN でない）で、表示枠の中心は自機から見てどこか（東 m・北 m）。 */
    fun frameCenterOffset(rangeM: Double, headingDeg: Double?, settings: NavSettings): EN

    /** 縮尺 rangeM での 1 m あたりの画面の長さ [px]。 */
    fun pxPerM(rangeM: Double, settings: NavSettings): Double
}

/**
 * 今の縮尺と AUTO の状態。AUTO では広げる方向はすぐ、狭める方向は zoomInDelayMs 続いてから切り替える。
 * ＋ / − を押したら AUTO は OFF になる。
 */
class RangeSelector(
    stepsKm: List<Double>,
    initialKm: Double,
    auto: Boolean,
    var fitRatio: Double = 0.9,
    var zoomInDelayMs: Long = 5_000,
) {
    private var steps: List<Double> = normalize(stepsKm)

    var rangeM: Double = snap(initialKm * 1000)
        private set

    var auto: Boolean = auto
        private set

    /** 狭めたい状態が始まった時刻（AUTO の狭める方向の待ち）。 */
    private var zoomInSince: Long? = null

    /** 有効な段を変える（設定の変更）。今の縮尺は近い段に合わせる。 */
    fun setSteps(stepsKm: List<Double>) {
        steps = normalize(stepsKm)
        rangeM = snap(rangeM)
    }

    /** ＋: 1段狭く（拡大）。AUTO は OFF（keepAuto なら AUTO はそのまま。PAN 中）。 */
    fun zoomIn(keepAuto: Boolean = false) {
        if (!keepAuto) auto = false
        zoomInSince = null
        rangeM = steps.lastOrNull { it < rangeM } ?: rangeM
    }

    /** −: 1段広く（縮小）。AUTO は OFF（keepAuto なら AUTO はそのまま。PAN 中）。 */
    fun zoomOut(keepAuto: Boolean = false) {
        if (!keepAuto) auto = false
        zoomInSince = null
        rangeM = steps.firstOrNull { it > rangeM } ?: rangeM
    }

    /** 次の update では、狭める方向も待たずに AUTO の段にする（PAN から現在地へ戻ったとき）。 */
    fun decideNow() {
        decideNow = true
    }

    private var decideNow = false

    /** AUTO の狭める方向の待ちをやり直す（PAN 中など、判定を止めている間）。 */
    fun restartWait() {
        zoomInSince = null
    }

    fun setAuto(on: Boolean) {
        auto = on
        zoomInSince = null
    }

    /**
     * AUTO の判定を進めて、今の縮尺 [m] を返す。
     * @param nowMs 現在時刻（狭める方向の待ち時間に使う）
     */
    fun update(nextWpDistanceM: Double?, groundSpeedMps: Float?, nowMs: Long): Double =
        update(nextWpDistanceM?.let { d -> { r: Double -> d <= r * fitRatio } }, nowMs)

    /**
     * AUTO の判定を進めて、今の縮尺 [m] を返す。
     * @param fits その縮尺で次の WP が収まるか。次の WP がなければ null（今の縮尺のまま）
     */
    fun update(fits: ((rangeM: Double) -> Boolean)?, nowMs: Long): Double {
        if (!auto) return rangeM
        val want = fits?.let { RangeAuto.desired(steps, it) }
        if (decideNow && want != null) {
            decideNow = false
            rangeM = want
            zoomInSince = null
            return rangeM
        }
        when {
            want == null || want == rangeM -> zoomInSince = null
            // 広げる方向はすぐ
            want > rangeM -> {
                rangeM = want
                zoomInSince = null
            }
            // 狭める方向は、条件が続いてから
            else -> {
                val since = zoomInSince
                if (since == null || nowMs < since) {
                    zoomInSince = nowMs
                } else if (nowMs - since >= zoomInDelayMs) {
                    rangeM = want
                    zoomInSince = null
                }
            }
        }
        return rangeM
    }

    private fun snap(m: Double): Double = steps.minByOrNull { kotlin.math.abs(it - m) } ?: m

    private companion object {
        fun normalize(stepsKm: List<Double>): List<Double> =
            stepsKm.filter { it > 0 }.distinct().sorted().map { it * 1000 }.ifEmpty { listOf(1_000.0) }
    }
}
