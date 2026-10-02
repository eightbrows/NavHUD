package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.geo.EN

/**
 * 縮尺（ARC では基準の距離環が左右端に接する距離、North Up では縮尺の距離環）の段の扱い。
 * 対地速度は将来「速度に応じた縮尺」を足すためのもので、今は使わない。
 */
object RangeAuto {

    /** 選べる縮尺の全段 [km]。NavSettings の有効リストでこの中から使う段を選ぶ。 */
    val ALL_STEPS_KM = Tuning.RANGE_ALL_STEPS_KM

    /**
     * 距離だけで見た、次の WP が「縮尺 × fitRatio」以内に収まる最小の段 [m]。どの段にも収まらなければ最大の段。
     * 次の WP がなければ null。
     * @param stepsM 有効な段 [m]（昇順）
     * @param groundSpeedMps 対地速度（今は判定に使わない）
     */
    @Suppress("UNUSED_PARAMETER")
    fun desired(stepsM: List<Double>, nextWpDistanceM: Double?, groundSpeedMps: Float?, fitRatio: Double = Tuning.RANGE_DISTANCE_FIT_RATIO): Double? {
        if (nextWpDistanceM == null) return null
        return desired(stepsM) { nextWpDistanceM <= it * fitRatio }
    }

    /** fits（その縮尺で次の WP が表示枠に収まるか）が true になる最小の段。どの段にも収まらなければ最大の段。 */
    fun desired(stepsM: List<Double>, fits: (rangeM: Double) -> Boolean): Double? {
        if (stepsM.isEmpty()) return null
        return stepsM.firstOrNull(fits) ?: stepsM.last()
    }

    /**
     * AUTO の下限・上限 [km] を、使う段に寄せる。下限は元の下限以上でいちばん狭い段、上限は元の上限以下でいちばん広い段。
     * 寄せる段がなければ使う段の端。下限が上限より広くなったら、下限を上限にそろえる。
     */
    fun limitsKm(stepsKm: List<Double>, minKm: Double, maxKm: Double): Pair<Double, Double> {
        val steps = stepsKm.filter { it > 0 }.distinct().sorted().ifEmpty { listOf(1.0) }
        val hi = steps.lastOrNull { it <= maxKm + EPS } ?: steps.first()
        val lo = (steps.firstOrNull { it >= minKm - EPS } ?: steps.last()).coerceAtMost(hi)
        return lo to hi
    }

    private const val EPS = 1e-9
}

/**
 * 地図の表示枠（ボタン列などを除いた領域）の寸法を知っている側（core/view の HudViewport）が実装する。
 * AUTO 縮尺の判定と、PAN の始点・ドラッグ量の換算に使う。
 */
interface MapViewport {
    /**
     * 縮尺 rangeM のとき、自機から見て target（東 m・北 m）にある点が表示枠に余白付きで収まるか。
     * spread: 自機から見た位置をこの倍率だけ遠くにして判定する（狭めるときの余裕。1 / Tuning.AUTO_ZOOM_IN_FIT_RATIO）
     */
    fun fits(rangeM: Double, target: EN, headingDeg: Double?, settings: NavSettings, spread: Double = 1.0): Boolean

    /**
     * 縮尺 rangeM のとき、points（自機から見た東 m・北 m。ルートの順）のうち描画の枠の中に見えている隣り合う2つが、
     * どれも画面上で最小の間隔（Tuning.AUTO_WP_MIN_SEP_DP）以上離れているか。
     */
    fun separated(rangeM: Double, points: List<EN>, headingDeg: Double?, settings: NavSettings): Boolean

    /** 通常の表示（PAN でない）で、表示枠の中心は自機から見てどこか（東 m・北 m）。 */
    fun frameCenterOffset(rangeM: Double, headingDeg: Double?, settings: NavSettings): EN

    /** 縮尺 rangeM での 1 m あたりの画面の長さ [px]。 */
    fun pxPerM(rangeM: Double, settings: NavSettings): Double
}

/** AUTO の判定に使う、段ごとの問い（§6.1）。NavEngine が次の WP と画面（MapViewport）から作る。 */
interface RangeProbe {
    /** 段 rangeM で、次の WP が枠に収まるか。spread: 自機から見た位置をこの倍率だけ遠くにして判定する */
    fun fits(rangeM: Double, spread: Double = 1.0): Boolean

    /** 段 rangeM で、描画の枠の中に見えている隣り合う目標どうしが、画面上で最小の間隔以上離れているか */
    fun separated(rangeM: Double): Boolean = true
}

/**
 * 今の縮尺と AUTO の状態（§6.1）。AUTO は次の WP を収める段を基本に、[下限, 上限] の中で1段ずつ動かす。
 * 1回の判定（Fix・時計の刻み）で動かすのは1段だけ:
 * 1. 次の WP がない: 下限〜上限の段の中央（2つあれば広い方）へすぐ。
 * 2. 今の段が [下限, 上限] の外: すぐ範囲の端の段へ。
 * 3. 見えている隣り合う目標が近すぎる（separated でない）: すぐ1段狭める（下限まで）。
 * 4. 次の WP が今の段で収まらない: 1段広い段が上限以内で separated なら、すぐ1段広げる（広げられなければ矢印で示す）。
 * 5. 1段狭い段で、次の WP を 1 / AUTO_ZOOM_IN_FIT_RATIO 倍遠くに置いても収まり、separated: これが zoomInDelayMs 続いたら1段狭める。
 * ＋ / − を押したら AUTO は OFF になる。
 */
class RangeSelector(
    stepsKm: List<Double>,
    initialKm: Double,
    auto: Boolean,
    /** 既定は NavSettings の既定値（autoRangeZoomInDelaySec）から取る（二重に定義しない） */
    var zoomInDelayMs: Long = NavSettings().autoRangeZoomInDelaySec * 1000L,
    autoMinKm: Double = Tuning.AUTO_MIN_RANGE_KM,
    autoMaxKm: Double = Tuning.AUTO_MAX_RANGE_KM,
) {
    private var stepsKm: List<Double> = stepsKm
    private var steps: List<Double> = normalize(stepsKm)
    private var minKm = autoMinKm
    private var maxKm = autoMaxKm

    var rangeM: Double = snap(initialKm * 1000)
        private set

    var auto: Boolean = auto
        private set

    /** 狭めたい状態が始まった時刻（AUTO の狭める方向の待ち）。 */
    private var zoomInSince: Long? = null

    private var decideNow = false

    /** 有効な段を変える（設定の変更）。今の縮尺は近い段に合わせる。 */
    fun setSteps(stepsKm: List<Double>) {
        this.stepsKm = stepsKm
        steps = normalize(stepsKm)
        rangeM = snap(rangeM)
    }

    /** AUTO の下限・上限 [km]（段）を変える。使う段に寄せて使う（RangeAuto.limitsKm）。 */
    fun setAutoLimits(minKm: Double, maxKm: Double) {
        this.minKm = minKm
        this.maxKm = maxKm
    }

    /** AUTO で使う段（下限〜上限）[m]。 */
    val autoSteps: List<Double>
        get() {
            val (lo, hi) = RangeAuto.limitsKm(stepsKm, minKm, maxKm)
            return steps.filter { it >= lo * 1000 - EPS && it <= hi * 1000 + EPS }.ifEmpty { listOf(steps.first()) }
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

    /** 次の update では、1段ずつではなく、待たずに目標の段にする（PAN から現在地へ戻ったとき、シークのあと）。 */
    fun decideNow() {
        decideNow = true
    }

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
     * @param probe 次の WP と画面から作った問い。次の WP がなければ null（下限〜上限の中央の段にする）
     * @param nowMs 現在時刻（狭める方向の待ち時間に使う。REPLAY はトラックの時刻）
     */
    fun update(probe: RangeProbe?, nowMs: Long): Double {
        if (!auto) return rangeM
        val w = autoSteps
        val lo = w.first()
        val hi = w.last()
        // 1. 次の WP がない: 中央の段（2つあれば広い方）へすぐ
        if (probe == null) return set(w[w.size / 2])
        // PAN から戻ったとき・シークのあと: 目標の段へすぐ
        if (decideNow) {
            decideNow = false
            return set(target(w, probe))
        }
        // 2. 範囲の外: すぐ範囲の端へ
        if (rangeM < lo - EPS) return set(lo)
        if (rangeM > hi + EPS) return set(hi)
        val narrower = w.lastOrNull { it < rangeM - EPS }
        val wider = w.firstOrNull { it > rangeM + EPS }
        // 3. 見えている隣り合う目標が近すぎる: すぐ1段狭める
        if (!probe.separated(rangeM) && narrower != null) return set(narrower)
        // 4. 次の WP が収まらない: すぐ1段広げる（上限まで、WP を区別できる範囲で）
        if (!probe.fits(rangeM)) {
            return if (wider != null && probe.separated(wider)) set(wider) else set(rangeM)
        }
        // 5. 1段狭い段で余裕をもって収まる状態が続いたら、1段狭める
        if (narrower != null && probe.fits(narrower, 1.0 / Tuning.AUTO_ZOOM_IN_FIT_RATIO) && probe.separated(narrower)) {
            val since = zoomInSince
            if (since == null || nowMs < since) {
                zoomInSince = nowMs
            } else if (nowMs - since >= zoomInDelayMs) {
                return set(narrower)
            }
        } else {
            zoomInSince = null
        }
        return rangeM
    }

    /**
     * 目標の段: 下限〜上限のうち次の WP が収まるいちばん狭い段（どれにも収まらなければ上限）。
     * そこで WP を区別できなければ、区別できる段まで狭める（下限まで）。
     */
    private fun target(w: List<Double>, probe: RangeProbe): Double {
        var i = w.indexOfFirst { probe.fits(it) }.let { if (it < 0) w.lastIndex else it }
        while (i > 0 && !probe.separated(w[i])) i--
        return w[i]
    }

    private fun set(m: Double): Double {
        rangeM = m
        zoomInSince = null
        return rangeM
    }

    private fun snap(m: Double): Double = steps.minByOrNull { kotlin.math.abs(it - m) } ?: m

    private companion object {
        const val EPS = 1e-6

        fun normalize(stepsKm: List<Double>): List<Double> =
            stepsKm.filter { it > 0 }.distinct().sorted().map { it * 1000 }.ifEmpty { listOf(1_000.0) }
    }
}
