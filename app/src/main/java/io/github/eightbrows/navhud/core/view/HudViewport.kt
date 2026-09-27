package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.geo.EN
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.RangeFit

/**
 * HUD の描画領域と帯。AUTO 縮尺の判定に使う: 縮尺 rangeM で次の WP を画面に投影し、
 * 表示枠（帯を除き edgeInset + fitMargin だけ内側）に入れば「収まる」。ARC / North Up とも同じ判定。
 */
data class HudViewport(
    val rect: HudRect,
    val metrics: HudMetrics = HudMetrics(),
    val reserved: HudInsets = HudInsets(),
) : RangeFit {

    override fun fits(rangeM: Double, target: EN, headingDeg: Double?, settings: NavSettings): Boolean {
        val proj = HudSceneBuilder.projection(settings, rect, rangeM, headingDeg, metrics)
        val frame = HudSceneBuilder.arrowFrame(rect, reserved, metrics).inset(metrics.fitMargin)
        if (frame.width <= 0f || frame.height <= 0f) return false
        return frame.contains(proj.toScreen(target))
    }
}
