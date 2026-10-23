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

    /**
     * ダブルタップで詳細にしたとき（20261023-D02）: PAN の中心から (dxPx, dyPx) の所（タップした所）にある地点が、
     * 縮尺を変えたあとも同じ画面の位置に残るよう、PAN の中心をタップした所の方へ寄せる。
     * 地点は中心から (dx, dy) / before [m] の所にあり、変えたあとは (dx, dy) / after [m] の所に見えるので、差だけ中心を動かす。
     * 画面の上の方位（upDeg）はそのまま（ARC も North Up も、画面の上で見た向きで計算する）
     * @param before 変える前の 1 m あたりの画面の長さ [px]
     * @param after 変えたあとの 1 m あたりの画面の長さ [px]
     */
    fun zoomAbout(p: PanView, dxPx: Float, dyPx: Float, before: Double, after: Double): PanView {
        if (before <= 0 || after <= 0) return p
        val k = 1 / before - 1 / after
        val move = Geo.fromScreen(Screen(right = dxPx * k, fwd = -dyPx * k), p.upDeg)
        val (lat, lon) = Geo.fromEN(p.lat, p.lon, move)
        return p.copy(lat = lat, lon = lon)
    }
}
