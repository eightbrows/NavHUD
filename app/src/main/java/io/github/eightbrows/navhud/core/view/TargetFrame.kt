package io.github.eightbrows.navhud.core.view

/**
 * 画面外の矢印と AUTO 縮尺の判定に使う枠（§6.1）。長方形 outer から、右の操作列の部分 notch を欠いた形。
 * - 上: 数値の下端。左・下: 描画の枠の左端・下端。
 * - 右: 操作列のある高さの範囲（notch）だけは操作列の左端、それより上・下では描画の枠の右端。
 */
data class TargetFrame(val outer: HudRect, val notch: HudRect?) {

    /** 縁から d だけ内側の枠（outer は内側へ、notch は外側へ d 広げる）。 */
    fun inset(d: Float) = TargetFrame(
        outer.inset(d),
        notch?.let { HudRect(it.left - d, it.top - d, it.right + d, it.bottom + d) },
    )

    fun contains(p: P): Boolean = outer.contains(p) && !inNotch(p)

    private fun inNotch(p: P): Boolean = notch?.let { p.x > it.left && p.y > it.top && p.y < it.bottom } ?: false

    /**
     * origin から angleDeg 方向に伸ばした線が、この枠の縁に当たる点。origin は枠の内側にあること。
     * notch に先に当たるなら、その縁の点。
     */
    fun rayHit(origin: P, angleDeg: Double): P {
        val hit = HudGeometry.rayToRect(origin, angleDeg, outer)
        val n = notch ?: return hit
        val d = HudGeometry.dir(angleDeg)
        val tOuter = HudGeometry.dist(origin, hit)
        // notch（右へ開いた矩形）への入り口: 左の縁 x = n.left、上の縁 y = n.top、下の縁 y = n.bottom
        val candidates = mutableListOf<Float>()
        if (d.x > EPS && origin.x < n.left) {
            val t = (n.left - origin.x) / d.x
            val y = origin.y + d.y * t
            if (y >= n.top && y <= n.bottom) candidates += t
        }
        if (d.y > EPS && origin.y < n.top) {
            val t = (n.top - origin.y) / d.y
            if (origin.x + d.x * t >= n.left) candidates += t
        }
        if (d.y < -EPS && origin.y > n.bottom) {
            val t = (n.bottom - origin.y) / d.y
            if (origin.x + d.x * t >= n.left) candidates += t
        }
        val t = candidates.filter { it >= 0f && it < tOuter }.minOrNull() ?: return hit
        return origin + d * t
    }

    private companion object {
        const val EPS = 1e-6f
    }
}
