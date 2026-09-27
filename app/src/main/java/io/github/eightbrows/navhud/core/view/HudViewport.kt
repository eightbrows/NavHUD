package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.geo.EN
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.geo.Screen
import io.github.eightbrows.navhud.core.nav.MapViewport
import io.github.eightbrows.navhud.core.nav.NavSettings

/**
 * HUD の描画領域と、地図に重ねる帯（右の操作列、下の WP ボタン列）。
 * AUTO 縮尺の判定: 縮尺 rangeM で次の WP を画面に投影し、表示枠（帯を除き edgeInset + fitMargin だけ内側）に
 * 入れば「収まる」。ARC / North Up とも同じ判定。PAN の始点とドラッグ量の換算にも使う。
 */
data class HudViewport(
    val rect: HudRect,
    val metrics: HudMetrics = HudMetrics(),
    val reserved: HudInsets = HudInsets(),
) : MapViewport {

    private val frame: HudRect get() = HudSceneBuilder.mapFrame(rect, reserved)

    override fun fits(rangeM: Double, target: EN, headingDeg: Double?, settings: NavSettings): Boolean {
        val proj = HudSceneBuilder.projection(settings, frame, rangeM, headingDeg, metrics)
        val inner = frame.inset(metrics.edgeInset + metrics.fitMargin)
        if (inner.width <= 0f || inner.height <= 0f) return false
        return inner.contains(proj.toScreen(target))
    }

    override fun frameCenterOffset(rangeM: Double, headingDeg: Double?, settings: NavSettings): EN {
        val proj = HudSceneBuilder.projection(settings, frame, rangeM, headingDeg, metrics)
        val f = frame
        val dx = (f.centerX - proj.origin.x) / proj.pxPerM
        val dy = (f.centerY - proj.origin.y) / proj.pxPerM
        return Geo.fromScreen(Screen(right = dx, fwd = -dy), proj.upDeg)
    }

    override fun pxPerM(rangeM: Double, settings: NavSettings): Double =
        HudSceneBuilder.projection(settings, frame, rangeM, null, metrics).pxPerM
}
