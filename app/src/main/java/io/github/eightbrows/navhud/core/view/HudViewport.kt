package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.geo.EN
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.geo.Screen
import io.github.eightbrows.navhud.core.nav.MapViewport
import io.github.eightbrows.navhud.core.nav.NavSettings

/**
 * HUD の描画の枠と、地図の上に重ねた部品（上部バー・数値、右の操作列、WP ボタン列から下）。
 * AUTO 縮尺の判定: 縮尺 rangeM で次の WP を画面に投影し、矢印と AUTO の判定の枠（重ねた部品を除き edgeInset + fitMargin だけ内側）に
 * 入れば「収まる」。見えている隣り合う目標どうしが画面上で近すぎないか（separated）も見る。ARC / North Up とも同じ判定。
 * PAN の始点とドラッグ量の換算にも使う。
 */
data class HudViewport(
    val rect: HudRect,
    val metrics: HudMetrics = HudMetrics(),
    val reserved: HudInsets = HudInsets(),
) : MapViewport {

    private val avoid: HudRect get() = HudSceneBuilder.avoidFrame(rect, reserved)

    private fun projection(rangeM: Double, headingDeg: Double?, settings: NavSettings) =
        HudSceneBuilder.projection(settings, rect, avoid, rangeM, headingDeg, metrics)

    override fun fits(rangeM: Double, target: EN, headingDeg: Double?, settings: NavSettings, spread: Double): Boolean {
        // 矢印と AUTO の枠（TargetFrame）: 上は数値の下端、左・下は描画の枠、右は操作列の高さの範囲だけ操作列の左端
        val inner = HudSceneBuilder.targetFrame(rect, reserved).inset(metrics.edgeInset + metrics.fitMargin)
        if (inner.outer.width <= 0f || inner.outer.height <= 0f) return false
        // spread: 自機から見た位置をこの倍率だけ遠くにする（投影の原点は自機なので、画面上の自機からの位置も同じ倍率）
        return inner.contains(projection(rangeM, headingDeg, settings).toScreen(EN(target.e * spread, target.n * spread)))
    }

    override fun separated(rangeM: Double, points: List<EN>, headingDeg: Double?, settings: NavSettings): Boolean {
        // 描画の枠の中に見えている隣り合う2つの、画面上の距離
        val proj = projection(rangeM, headingDeg, settings)
        val screen = points.map { proj.toScreen(it) }
        return screen.zipWithNext().none { (a, b) ->
            rect.contains(a) && rect.contains(b) && HudGeometry.dist(a, b) < metrics.wpMinSep
        }
    }

    /** 通常の表示で、PAN の中心に置く点（横は画面の中央、縦は回避枠の中央）が自機から見てどこか。 */
    override fun frameCenterOffset(rangeM: Double, headingDeg: Double?, settings: NavSettings): EN {
        val proj = projection(rangeM, headingDeg, settings)
        val dx = (rect.centerX - proj.origin.x) / proj.pxPerM
        val dy = (avoid.centerY - proj.origin.y) / proj.pxPerM
        return Geo.fromScreen(Screen(right = dx, fwd = -dy), proj.upDeg)
    }

    override fun pxPerM(rangeM: Double, settings: NavSettings): Double = projection(rangeM, null, settings).pxPerM
}
