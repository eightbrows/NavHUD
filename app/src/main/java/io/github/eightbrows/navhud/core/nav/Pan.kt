package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.geo.EN
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.geo.Screen

/** PAN（ドラッグで地図を平行移動する）の計算。Android に依存しない。 */
object Pan {

    /**
     * PAN を始める: 今の表示で表示枠の中心にある地点を中心にする。
     * @param centerOffset 自機から見た表示枠の中心（東 m・北 m）
     * @param upDeg 画面の上の方位（ARC は今の機首方位、North Up は 0）。PAN 中はこのまま固定
     */
    fun start(fixLat: Double, fixLon: Double, centerOffset: EN, upDeg: Double): PanView {
        val (lat, lon) = Geo.fromEN(fixLat, fixLon, centerOffset)
        return PanView(lat, lon, upDeg)
    }

    /**
     * 指を (dxPx, dyPx) 動かした分、地図を同じ向きに動かす（表示の中心は逆へ動く）。
     * @param pxPerM 今の縮尺での 1 m あたりの画面の長さ
     */
    fun drag(p: PanView, dxPx: Float, dyPx: Float, pxPerM: Double): PanView {
        if (pxPerM <= 0) return p
        val move = Geo.fromScreen(Screen(right = -dxPx / pxPerM, fwd = dyPx / pxPerM), p.upDeg)
        val (lat, lon) = Geo.fromEN(p.lat, p.lon, move)
        return p.copy(lat = lat, lon = lon)
    }
}
