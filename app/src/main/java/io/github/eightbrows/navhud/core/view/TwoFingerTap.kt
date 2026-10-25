package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.Tuning

/** タッチの中の 1 本の指（id は指ごとに同じ値。pressed は置いている間 true） */
data class TouchPoint(val id: Long, val x: Float, val y: Float, val pressed: Boolean)

/**
 * 2 本指のタップ（20261024-D09。20261023-D02 の提案）: 1 回のタッチ（最初の指を置いてから全部離すまで）が次の全部を満たしたら、
 * 2 本指のタップとみなす。
 * - 同時に置いた指が、ちょうど 2 本になった（3 本以上になったら違う）
 * - どちらの指も、置いた所から slopPx（端末のタッチのずれの許し方）より動かなかった
 * - 最初の指を置いてから全部の指を離すまでが maxMs 以内
 * - ピンチで段が動かなかった（onPinchStep が呼ばれていない）
 * 1 回のタッチごとに作り直す（または reset する）。
 */
class TwoFingerTap(
    private val slopPx: Float,
    private val maxMs: Long = Tuning.TWO_FINGER_TAP_MAX_MS,
) {
    private var startMs: Long? = null
    private var endMs: Long? = null
    private val origin = HashMap<Long, Pair<Float, Float>>()
    private var maxDown = 0
    private var moved = false
    private var pinched = false

    /** 指の状態が変わるたびに呼ぶ（timeMs はそのときの時刻 [ms]）。全部の指が離れたら、タッチの終わりとする */
    fun onEvent(timeMs: Long, points: List<TouchPoint>) {
        val down = points.filter { it.pressed }
        if (startMs == null) {
            if (down.isEmpty()) return
            startMs = timeMs
        }
        for (p in down) {
            val o = origin.getOrPut(p.id) { p.x to p.y }
            if (kotlin.math.hypot(p.x - o.first, p.y - o.second) > slopPx) moved = true
        }
        maxDown = maxOf(maxDown, down.size)
        if (down.isEmpty()) endMs = timeMs
    }

    /** ピンチで段が動いた */
    fun onPinchStep() {
        pinched = true
    }

    /** タッチが終わっていて、2 本指のタップだったか */
    val isTap: Boolean
        get() {
            val s = startMs ?: return false
            val e = endMs ?: return false
            return maxDown == 2 && origin.size == 2 && !moved && !pinched && e - s <= maxMs
        }

    fun reset() {
        startMs = null
        endMs = null
        origin.clear()
        maxDown = 0
        moved = false
        pinched = false
    }
}
