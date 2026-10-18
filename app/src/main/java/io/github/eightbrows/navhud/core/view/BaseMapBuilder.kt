package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.geo.Screen
import io.github.eightbrows.navhud.core.io.BaseMap
import io.github.eightbrows.navhud.core.io.MapLayer
import io.github.eightbrows.navhud.core.io.MapLine
import io.github.eightbrows.navhud.core.io.RoadKind
import io.github.eightbrows.navhud.core.io.WaterKind
import io.github.eightbrows.navhud.core.nav.MapItem
import io.github.eightbrows.navhud.core.nav.MapRange
import io.github.eightbrows.navhud.core.nav.NavSettings
import kotlin.math.cos
import kotlin.math.sin

/** 緯度・経度の範囲 [度]。 */
data class LatLonBox(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double) {
    /** 線の範囲と重なるか（重ならない線は画面に入らないので飛ばす） */
    fun intersects(line: MapLine): Boolean =
        line.maxLat >= minLat && line.minLat <= maxLat && line.maxLon >= minLon && line.minLon <= maxLon

    fun contains(lat: Double, lon: Double): Boolean = lat in minLat..maxLat && lon in minLon..maxLon
}

/** 背景地図の、画面に描くもの（線はレイヤの描く順に並んでいる）。 */
data class BaseMapScene(val lines: List<Polyline>, val marks: List<MapMark>)

/** 背景地図のレイヤの描き方（色の種類・太さ・線の種類）。点のレイヤは記号の形と塗り。 */
data class MapLayerStyle(val ink: Ink, val widthDp: Float = 0f, val dash: LineDash = LineDash.SOLID, val shape: MarkShape = MarkShape.CIRCLE, val filled: Boolean = false)

/**
 * 背景地図（§8）: 画面に入る線・点だけを選び、地図（緯度・経度）から画面の位置に直す。Android に依存しない。
 * - 今の R1（自機から 1 つ目の距離環の距離）に合う段（前もって点を間引いた線の組）を使う
 * - 地図から画面への直し方は、軌跡・WP と同じ（基準の地点からの東・北 [m] → HudProjection）
 * - 点の数を減らす: 画面の上で、前に残した点から minStepPx より近い点は飛ばす（線の最後の点は残す）
 */
object BaseMapBuilder {

    /** 描く順番（下から上）。面の輪郭 → 水（河川・海岸線）→ 境 → 交通 → ダム。点は線の上 */
    val DRAW_ORDER: List<MapLayer> = listOf(
        MapLayer.BUILT_UP_AREA, MapLayer.INLAND_WATER, MapLayer.RIVER, MapLayer.COASTLINE,
        MapLayer.MUNI_BOUNDARY, MapLayer.PREF_BOUNDARY,
        MapLayer.ROAD, MapLayer.RAILWAY, MapLayer.FERRY, MapLayer.DAM_LINE,
        MapLayer.CITY, MapLayer.STATION, MapLayer.PORT, MapLayer.AIRPORT, MapLayer.DAM_POINT,
    )

    /** レイヤの描き方。どれも HUD を邪魔しない暗い色（実際の色は ui/HudColors） */
    fun styleOf(layer: MapLayer): MapLayerStyle = when (layer) {
        MapLayer.COASTLINE -> MapLayerStyle(Ink.COASTLINE, Tuning.BASEMAP_COAST_LINE_DP)
        MapLayer.PREF_BOUNDARY -> MapLayerStyle(Ink.PREF_BOUNDARY, Tuning.BASEMAP_PREF_LINE_DP, LineDash.DOT)
        MapLayer.MUNI_BOUNDARY -> MapLayerStyle(Ink.MUNI_BOUNDARY, Tuning.BASEMAP_MUNI_LINE_DP, LineDash.FINE_DOT)
        MapLayer.RIVER -> MapLayerStyle(Ink.MAP_WATER, Tuning.BASEMAP_RIVER_LINE_DP)
        MapLayer.INLAND_WATER -> MapLayerStyle(Ink.MAP_WATER, Tuning.BASEMAP_AREA_LINE_DP)
        MapLayer.BUILT_UP_AREA -> MapLayerStyle(Ink.MAP_BUILT_UP, Tuning.BASEMAP_AREA_LINE_DP)
        MapLayer.ROAD -> MapLayerStyle(Ink.MAP_ROAD, Tuning.BASEMAP_ROAD_LINE_DP)
        MapLayer.RAILWAY -> MapLayerStyle(Ink.MAP_RAIL, Tuning.BASEMAP_RAIL_LINE_DP, LineDash.DASH)
        MapLayer.FERRY -> MapLayerStyle(Ink.MAP_FERRY, Tuning.BASEMAP_FERRY_LINE_DP, LineDash.LONG_DASH)
        MapLayer.DAM_LINE -> MapLayerStyle(Ink.MAP_DAM, Tuning.BASEMAP_DAM_LINE_DP)
        MapLayer.CITY -> MapLayerStyle(Ink.MAP_CITY, shape = MarkShape.CIRCLE)
        MapLayer.STATION -> MapLayerStyle(Ink.MAP_STATION, shape = MarkShape.SQUARE, filled = true)
        MapLayer.PORT -> MapLayerStyle(Ink.MAP_PORT, shape = MarkShape.CIRCLE, filled = true)
        MapLayer.AIRPORT -> MapLayerStyle(Ink.MAP_AIRPORT, shape = MarkShape.PLANE, filled = true)
        MapLayer.DAM_POINT -> MapLayerStyle(Ink.MAP_DAM, shape = MarkShape.DIAMOND, filled = true)
    }

    /**
     * @param items 項目ごとの、どの縮尺まで描くか（設定の NavSettings.mapItems）。R1 がその値以下の項目だけを描く
     * @param refLat 基準の地点（通常は自機、PAN は PAN の中心。proj の EN の原点）
     * @param rect 描画の枠。この枠に（marginPx だけ外まで）入る範囲の線・点を選ぶ
     * @param r1M 今の R1 [m]（縮尺の 1/2）。使う段と、描く項目を決める
     * @param widthLevel 線の太さの段（設定の 1〜6。全部の線の太さに Tuning.BASEMAP_WIDTH_FACTORS を掛ける）
     * @param brightnessLevel 線の明るさの段（設定の 1〜6。全部の線と記号の色に Tuning.BASEMAP_BRIGHTNESS_FACTORS を掛ける）
     */
    fun build(
        map: BaseMap,
        items: Map<MapItem, MapRange>,
        refLat: Double,
        refLon: Double,
        proj: HudProjection,
        rect: HudRect,
        r1M: Double,
        widthLevel: Int = NavSettings.MAP_LINE_DEFAULT_LEVEL,
        brightnessLevel: Int = NavSettings.MAP_LINE_DEFAULT_LEVEL,
        minStepPx: Float = Tuning.BASEMAP_MIN_STEP_PX,
        marginPx: Float = Tuning.BASEMAP_MARGIN_PX,
    ): BaseMapScene {
        val shown = items.filter { it.value.shows(r1M) }.keys
        if (shown.isEmpty()) return BaseMapScene(emptyList(), emptyList())
        val layers = shown.flatMap { it.layers }.toSet()
        val box = visibleBox(refLat, refLon, proj, rect, marginPx)
        val level = map.levelFor(r1M)
        val wk = factor(Tuning.BASEMAP_WIDTH_FACTORS, widthLevel)
        val k = factor(Tuning.BASEMAP_BRIGHTNESS_FACTORS, brightnessLevel)
        val lines = ArrayList<Polyline>()
        val marks = ArrayList<MapMark>()
        for (layer in DRAW_ORDER) {
            if (layer !in layers) continue
            val style = styleOf(layer)
            if (layer.isPoint) {
                for (p in map.points) {
                    if (p.layer != layer || !box.contains(p.lat, p.lon)) continue
                    marks += MapMark(project(p.lat, p.lon, refLat, refLon, proj), style.ink, style.shape, Tuning.BASEMAP_MARK_DP, style.filled, k)
                }
            } else if (layer == MapLayer.ROAD) {
                // 道路: 項目（高速道路・その他の自動車専用道路・国道・県道）ごとに、その縮尺で描く種類だけ。種類ごとに色と太さを変える。
                // 県道 → 国道 → その他の自動車専用道路 → 高速道路の順に並べる（太い線が上。同じ見た目が続くので、Canvas はまとめて描ける）
                val byItem = ROAD_DRAW_ORDER.associateWith { ArrayList<Polyline>() }
                for (line in level?.candidates(layer, box.minLat, box.minLon, box.maxLat, box.maxLon).orEmpty()) {
                    val item = roadItem(line.kind)
                    if (item !in shown) continue
                    if (!box.intersects(line)) continue
                    val pts = projectThin(line, refLat, refLon, proj, minStepPx)
                    if (pts.size >= 2) byItem.getValue(item) += Polyline(pts, roadInk(item), roadWidthDp(line.kind) * wk, style.dash, k)
                }
                for (item in ROAD_DRAW_ORDER) lines += byItem.getValue(item)
            } else {
                // 幅のある川: R1 10 km 以上は川の中心の線（河川の印付きの線）、未満は川の面の輪郭（湖沼などの印付きの線）
                val wide = r1M >= Tuning.BASEMAP_WIDE_RIVER_R1_M
                for (line in level?.candidates(layer, box.minLat, box.minLon, box.maxLat, box.maxLon).orEmpty()) {
                    if (layer == MapLayer.RIVER && !wide && WaterKind.isWideRiver(line.kind)) continue
                    if (layer == MapLayer.INLAND_WATER && wide && WaterKind.isWideRiver(line.kind)) continue
                    if (!box.intersects(line)) continue
                    val pts = projectThin(line, refLat, refLon, proj, minStepPx)
                    if (pts.size >= 2) lines += Polyline(pts, style.ink, style.widthDp * wk, style.dash, k)
                }
            }
        }
        return BaseMapScene(lines, marks)
    }

    /** 道路の線がどの項目に入るか: 高速道路・その他の自動車専用道路・国道・県道（種類の分からない道路は国道） */
    fun roadItem(kind: Int): MapItem = when (kind) {
        RoadKind.MOTORWAY -> MapItem.MOTORWAY
        RoadKind.OTHER_MOTORWAY -> MapItem.OTHER_MOTORWAY
        RoadKind.SECONDARY -> MapItem.PREF_ROAD
        else -> MapItem.NATIONAL_ROAD
    }

    /** 道路の色: 高速道路は青、その他の自動車専用道路は暗めの青、国道は赤、県道は茶 */
    fun roadInk(item: MapItem): Ink = when (item) {
        MapItem.MOTORWAY -> Ink.MAP_MOTORWAY
        MapItem.OTHER_MOTORWAY -> Ink.MAP_OTHER_MOTORWAY
        MapItem.NATIONAL_ROAD -> Ink.MAP_NATIONAL_ROAD
        else -> Ink.MAP_ROAD
    }

    /**
     * 道路の線の太さ [dp]（線の太さの段 1 のとき）: 高速道路 3.0・その他の自動車専用道路 1.5・国道 1.5・県道 0.7
     * （種類の分からない道路は国道と同じ）
     */
    fun roadWidthDp(kind: Int): Float = when (kind) {
        RoadKind.MOTORWAY -> Tuning.BASEMAP_MOTORWAY_LINE_DP
        RoadKind.OTHER_MOTORWAY -> Tuning.BASEMAP_OTHER_MOTORWAY_LINE_DP
        RoadKind.SECONDARY -> Tuning.BASEMAP_PREF_ROAD_LINE_DP
        else -> Tuning.BASEMAP_ROAD_LINE_DP
    }

    /** 道路の描く順（下から）: 県道 → 国道 → その他の自動車専用道路 → 高速道路 */
    val ROAD_DRAW_ORDER = listOf(MapItem.PREF_ROAD, MapItem.NATIONAL_ROAD, MapItem.OTHER_MOTORWAY, MapItem.MOTORWAY)

    /** 段（1〜）を掛ける数にする（範囲の外は端の段） */
    fun factor(factors: List<Float>, level: Int): Float = factors[(level - 1).coerceIn(0, factors.lastIndex)]

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

    /** 1 点を画面の位置に直す（Geo.toEN → HudProjection.toScreen と同じ） */
    private fun project(lat: Double, lon: Double, refLat: Double, refLon: Double, proj: HudProjection): P =
        proj.toScreen(Geo.toEN(refLat, refLon, lat, lon))

    /**
     * 画面の位置に直しながら、点を減らす（thin と同じ決まり。いったん全部の点の一覧を作らないので速い）。
     */
    internal fun projectThin(line: MapLine, refLat: Double, refLon: Double, proj: HudProjection, minStepPx: Float): List<P> {
        val n = line.size
        if (n <= 2 || minStepPx <= 0f) return project(line, refLat, refLon, proj)
        val h = Math.toRadians(proj.upDeg)
        val ch = cos(h)
        val sh = sin(h)
        val r = Geo.EARTH_RADIUS_M
        val min2 = minStepPx * minStepPx
        val out = ArrayList<P>()
        var lastX = 0f
        var lastY = 0f
        for (i in 0 until n) {
            val lat = line.lat(i)
            val e = Math.toRadians(Geo.angleDiff(refLon, line.lon(i))) * r * cos(Math.toRadians((refLat + lat) / 2))
            val nn = Math.toRadians(lat - refLat) * r
            val x = (proj.origin.x + (e * ch - nn * sh) * proj.pxPerM).toFloat()
            val y = (proj.origin.y - (e * sh + nn * ch) * proj.pxPerM).toFloat()
            val keep = when (i) {
                0 -> true
                n - 1 -> x != lastX || y != lastY
                else -> (x - lastX) * (x - lastX) + (y - lastY) * (y - lastY) >= min2
            }
            if (keep) {
                out += P(x, y)
                lastX = x
                lastY = y
            }
        }
        return out
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
            val lat = line.lat(it)
            val e = Math.toRadians(Geo.angleDiff(refLon, line.lon(it))) * r * cos(Math.toRadians((refLat + lat) / 2))
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
}
