package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.geo.Screen
import io.github.eightbrows.navhud.core.io.BaseMap
import io.github.eightbrows.navhud.core.io.MapLayer
import io.github.eightbrows.navhud.core.io.MapLine
import kotlin.math.cos
import kotlin.math.sin

/** 緯度・経度の範囲 [度]。 */
data class LatLonBox(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double) {
    /** 線の範囲と重なるか（重ならない線は画面に入らないので飛ばす） */
    fun intersects(line: MapLine): Boolean =
        line.maxLat >= minLat && line.minLat <= maxLat && line.maxLon >= minLon && line.minLon <= maxLon
}

/**
 * 背景地図（§8）: 画面に入る線だけを選び、地図（緯度・経度）から画面の位置に直す。Android に依存しない。
 * 地図から画面への直し方は、軌跡・WP と同じ（基準の地点からの東・北 [m] → HudProjection）。
 * 点の数を減らす: 画面の上で、前に残した点から minStepPx より近い点は飛ばす（線の最後の点は残す）。
 */
object BaseMapBuilder {

    /** 描く線の種類（今は海岸線と県境。あとで河川・道路・鉄道を足す） */
    val DRAWN_LAYERS = setOf(MapLayer.COASTLINE, MapLayer.PREF_BOUNDARY)

    /**
     * @param refLat 基準の地点（通常は自機、PAN は PAN の中心。proj の EN の原点）
     * @param rect 描画の枠。この枠に（marginPx だけ外まで）入る範囲の線を選ぶ
     */
    fun build(
        map: BaseMap,
        refLat: Double,
        refLon: Double,
        proj: HudProjection,
        rect: HudRect,
        minStepPx: Float = Tuning.BASEMAP_MIN_STEP_PX,
        marginPx: Float = Tuning.BASEMAP_MARGIN_PX,
    ): List<Polyline> {
        val box = visibleBox(refLat, refLon, proj, rect, marginPx)
        val out = ArrayList<Polyline>()
        for (line in map.lines) {
            if (line.layer !in DRAWN_LAYERS || !box.intersects(line)) continue
            val pts = thin(project(line, refLat, refLon, proj), minStepPx)
            if (pts.size >= 2) out += Polyline(pts, inkOf(line.layer), widthOf(line.layer), dashed = line.layer == MapLayer.PREF_BOUNDARY)
        }
        return out
    }

    /** 描画の枠の四隅（＋ 余白）を地図に戻した範囲。ARC で画面が回っていても、四隅を含む範囲なので枠全体が入る。 */
    fun visibleBox(refLat: Double, refLon: Double, proj: HudProjection, rect: HudRect, marginPx: Float = 0f): LatLonBox {
        val corners = listOf(
            P(rect.left - marginPx, rect.top - marginPx), P(rect.right + marginPx, rect.top - marginPx),
            P(rect.left - marginPx, rect.bottom + marginPx), P(rect.right + marginPx, rect.bottom + marginPx),
        ).map { p ->
            val en = Geo.fromScreen(Screen(right = (p.x - proj.origin.x) / proj.pxPerM, fwd = (proj.origin.y - p.y) / proj.pxPerM), proj.upDeg)
            Geo.fromEN(refLat, refLon, en)
        }
        return LatLonBox(corners.minOf { it.first }, corners.minOf { it.second }, corners.maxOf { it.first }, corners.maxOf { it.second })
    }

    /**
     * Geo.toEN → HudProjection.toScreen と同じ計算（軌跡・WP と同じ位置になる）。点が多いので、画面の向きの三角関数は
     * 線ごとに1回だけ求める（点ごとに求めるのは、緯度で変わる東西の縮みの cos だけ）。
     */
    private fun project(line: MapLine, refLat: Double, refLon: Double, proj: HudProjection): List<P> {
        val h = Math.toRadians(proj.upDeg)
        val ch = cos(h)
        val sh = sin(h)
        val r = Geo.EARTH_RADIUS_M
        return List(line.size) {
            val lat = line.lats[it]
            val e = Math.toRadians(Geo.angleDiff(refLon, line.lons[it])) * r * cos(Math.toRadians((refLat + lat) / 2))
            val n = Math.toRadians(lat - refLat) * r
            val right = e * ch - n * sh
            val fwd = e * sh + n * ch
            P((proj.origin.x + right * proj.pxPerM).toFloat(), (proj.origin.y - fwd * proj.pxPerM).toFloat())
        }
    }

    /** 前に残した点から minStepPx より近い点を飛ばす。最初と最後の点は残す（最後の点が前に残した点と同じ位置なら残さない）。 */
    fun thin(points: List<P>, minStepPx: Float): List<P> {
        if (points.size <= 2 || minStepPx <= 0f) return points
        val out = ArrayList<P>(points.size)
        out += points[0]
        var last = points[0]
        for (i in 1 until points.size - 1) {
            val p = points[i]
            if (HudGeometry.dist(last, p) >= minStepPx) {
                out += p
                last = p
            }
        }
        val end = points.last()
        if (end != last) out += end
        return out
    }

    private fun inkOf(layer: MapLayer): Ink = if (layer == MapLayer.PREF_BOUNDARY) Ink.PREF_BOUNDARY else Ink.COASTLINE

    private fun widthOf(layer: MapLayer): Float = if (layer == MapLayer.PREF_BOUNDARY) Tuning.BASEMAP_PREF_LINE_DP else Tuning.BASEMAP_COAST_LINE_DP
}
