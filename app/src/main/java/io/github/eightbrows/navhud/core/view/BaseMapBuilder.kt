package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.geo.Screen
import io.github.eightbrows.navhud.core.io.AirportKind
import io.github.eightbrows.navhud.core.io.BaseMap
import io.github.eightbrows.navhud.core.io.MapLayer
import io.github.eightbrows.navhud.core.io.MapLine
import io.github.eightbrows.navhud.core.io.RailKind
import io.github.eightbrows.navhud.core.io.RoadKind
import io.github.eightbrows.navhud.core.io.RunwayKind
import io.github.eightbrows.navhud.core.io.WaterKind
import io.github.eightbrows.navhud.core.nav.LandmarkItem
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
data class BaseMapScene(
    val lines: List<Polyline>,
    val marks: List<MapMark>,
    /** 空港の敷地（塗る面の輪。20261022-D01） */
    val areas: List<Polyline> = emptyList(),
    /** 空港の略号の文字（20261022-D01） */
    val labels: List<MapLabel> = emptyList(),
    /** 滑走路と延長線（延長線が先、滑走路があと。20261024-D04） */
    val runways: List<Polyline> = emptyList(),
)

/** 背景地図のレイヤの描き方（色の種類・太さ・線の種類）。点のレイヤ（空港）は色だけ（記号は飛行機） */
data class MapLayerStyle(val ink: Ink, val widthDp: Float = 0f, val dash: LineDash = LineDash.SOLID)

/**
 * 背景地図（§8）: 画面に入る線・点だけを選び、地図（緯度・経度）から画面の位置に直す。Android に依存しない。
 * - 今の R1（自機から 1 つ目の距離環の距離）に合う段（前もって点を間引いた線の組）を使う
 * - 地図から画面への直し方は、軌跡・WP と同じ（基準の地点からの東・北 [m] → HudProjection）
 * - 点の数を減らす: 画面の上で、前に残した点から minStepPx より近い点は飛ばす（線の最後の点は残す）
 */
object BaseMapBuilder {

    /** 線のレイヤの描く順番（下から上）。湖沼などの輪郭 → 水（河川・海岸線）→ 境 → 交通。点（ランドマーク）は線の全部より上（LANDMARK_DRAW_ORDER） */
    val DRAW_ORDER: List<MapLayer> = listOf(
        MapLayer.INLAND_WATER, MapLayer.RIVER, MapLayer.COASTLINE,
        MapLayer.MUNI_BOUNDARY, MapLayer.PREF_BOUNDARY,
        MapLayer.ROAD, MapLayer.RAILWAY, MapLayer.FERRY,
    )

    /**
     * ランドマークの記号の描く順番（下から上。20261021-D04。20261022-D01 で作り直した）: 山頂（低）→（中）→（高）→
     * 空港・軍用（小）→ 民間（小）→ 軍用（大）→ 民間（大）（20261024-D01）。
     * 空港の敷地はこれより下（線の上）、空港の略号はこれより上に描く。同じ見た目が続くので、Canvas はまとめて描ける
     */
    val LANDMARK_DRAW_ORDER: List<LandmarkItem> = listOf(
        LandmarkItem.PEAK_LOW, LandmarkItem.PEAK_MID, LandmarkItem.PEAK_HIGH,
        LandmarkItem.AIRPORT_MIL_SMALL, LandmarkItem.AIRPORT_CIVIL_SMALL, LandmarkItem.AIRPORT_MIL_LARGE, LandmarkItem.AIRPORT_CIVIL_LARGE,
    )

    /** レイヤの描き方。どれも HUD を邪魔しない暗い色（実際の色は ui/HudColors） */
    fun styleOf(layer: MapLayer): MapLayerStyle = when (layer) {
        MapLayer.COASTLINE -> MapLayerStyle(Ink.COASTLINE, Tuning.BASEMAP_COAST_LINE_DP)
        MapLayer.PREF_BOUNDARY -> MapLayerStyle(Ink.PREF_BOUNDARY, Tuning.BASEMAP_PREF_LINE_DP, LineDash.DOT)
        MapLayer.MUNI_BOUNDARY -> MapLayerStyle(Ink.MUNI_BOUNDARY, Tuning.BASEMAP_MUNI_LINE_DP, LineDash.FINE_DOT)
        MapLayer.RIVER -> MapLayerStyle(Ink.MAP_WATER, Tuning.BASEMAP_RIVER_LINE_DP)
        MapLayer.INLAND_WATER -> MapLayerStyle(Ink.MAP_WATER, Tuning.BASEMAP_AREA_LINE_DP)
        MapLayer.ROAD -> MapLayerStyle(Ink.MAP_ROAD, Tuning.BASEMAP_ROAD_LINE_DP)
        MapLayer.RAILWAY -> MapLayerStyle(Ink.MAP_RAIL, Tuning.BASEMAP_RAIL_LINE_DP, LineDash.DASH)
        MapLayer.FERRY -> MapLayerStyle(Ink.MAP_FERRY, Tuning.BASEMAP_FERRY_LINE_DP, LineDash.LONG_DASH)
        MapLayer.AIRPORT -> MapLayerStyle(Ink.MAP_AIRPORT)
        MapLayer.PEAK -> MapLayerStyle(Ink.MAP_PEAK_HIGH)
        MapLayer.AIRPORT_AREA -> MapLayerStyle(Ink.MAP_AIRPORT_AREA)
        MapLayer.RUNWAY -> MapLayerStyle(Ink.MAP_RUNWAY, Tuning.BASEMAP_RUNWAY_MIN_DP)
    }

    /** 記号の幅 [dp]（空港の民間・軍用と大小、山頂の高中低）。記号のない項目（敷地・略号）は 0 */
    fun landmarkSizeDp(item: LandmarkItem): Float = when (item) {
        LandmarkItem.AIRPORT_CIVIL_LARGE -> Tuning.BASEMAP_AIRPORT_CIVIL_LARGE_MARK_DP
        LandmarkItem.AIRPORT_MIL_LARGE -> Tuning.BASEMAP_AIRPORT_MIL_LARGE_MARK_DP
        LandmarkItem.AIRPORT_CIVIL_SMALL, LandmarkItem.AIRPORT_MIL_SMALL -> Tuning.BASEMAP_AIRPORT_SMALL_MARK_DP
        LandmarkItem.PEAK_HIGH -> Tuning.BASEMAP_PEAK_HIGH_MARK_DP
        LandmarkItem.PEAK_MID -> Tuning.BASEMAP_PEAK_MID_MARK_DP
        LandmarkItem.PEAK_LOW -> Tuning.BASEMAP_PEAK_LOW_MARK_DP
        LandmarkItem.AIRPORT_AREA, LandmarkItem.AIRPORT_CODE, LandmarkItem.RUNWAY, LandmarkItem.RUNWAY_EXT -> 0f
    }

    /**
     * 空港の略号を記号からどれだけ右上にずらすか [dp]（20261024-D01）: 記号の幅の半分。民間（大）は丸い輪にかからないよう、輪の半径の 0.8 倍
     * （略号の左下の角が輪の外に出る）
     */
    fun codeOffsetDp(item: LandmarkItem): Float =
        if (item == LandmarkItem.AIRPORT_CIVIL_LARGE) maxOf(landmarkSizeDp(item) / 2, Tuning.BASEMAP_AIRPORT_RING_RADIUS_DP * 0.8f) else landmarkSizeDp(item) / 2

    /**
     * ランドマークの記号（色・大きさ・形）。大きさと色は、縮尺・線の太さ・明るさの段によらない。
     * brightness はランドマークの明るさの段の掛ける数（Canvas が HudColors.landmark で色に掛ける）
     */
    fun landmarkMark(item: LandmarkItem, at: P, brightness: Float = 1f): MapMark {
        val shape = when (item) {
            LandmarkItem.AIRPORT_CIVIL_LARGE -> MarkShape.PLANE_RING
            LandmarkItem.AIRPORT_CIVIL_SMALL -> MarkShape.PLANE
            LandmarkItem.AIRPORT_MIL_LARGE, LandmarkItem.AIRPORT_MIL_SMALL -> MarkShape.PLANE_HOLLOW
            else -> MarkShape.TRIANGLE
        }
        val ink = when (item) {
            LandmarkItem.AIRPORT_CIVIL_LARGE, LandmarkItem.AIRPORT_CIVIL_SMALL, LandmarkItem.AIRPORT_MIL_LARGE, LandmarkItem.AIRPORT_MIL_SMALL -> Ink.MAP_AIRPORT
            LandmarkItem.PEAK_HIGH -> Ink.MAP_PEAK_HIGH
            LandmarkItem.PEAK_MID -> Ink.MAP_PEAK_MID
            LandmarkItem.PEAK_LOW -> Ink.MAP_PEAK_LOW
            LandmarkItem.AIRPORT_AREA, LandmarkItem.AIRPORT_CODE, LandmarkItem.RUNWAY, LandmarkItem.RUNWAY_EXT -> throw IllegalArgumentException("記号のない項目: $item")
        }
        return MapMark(at, ink, landmarkSizeDp(item), shape, brightness)
    }

    /**
     * @param items 線の項目ごとの、どの縮尺まで描くか（設定の NavSettings.mapItems。背景地図のスイッチが OFF なら、呼ぶ側が空にする）。
     *   R1 がその値以下の項目だけを描く
     * @param landmarks ランドマーク（空港・山頂の記号）の項目ごとの、どの縮尺まで描くか（NavSettings.landmarks。背景地図のスイッチによらない）
     * @param refLat 基準の地点（通常は自機、PAN は PAN の中心。proj の EN の原点）
     * @param rect 描画の枠。この枠に（marginPx だけ外まで）入る範囲の線・点を選ぶ
     * @param r1M 今の R1 [m]（縮尺の 1/2）。使う段と、描く項目を決める
     * @param widthLevel 線の太さの段（設定の 1〜6。全部の線の太さに Tuning.BASEMAP_WIDTH_FACTORS を掛ける）
     * @param brightnessLevel 線の明るさの段（設定の 1〜6。全部の線の色に Tuning.BASEMAP_BRIGHTNESS_FACTORS を掛ける。ランドマークの記号は掛けない）
     * @param landmarkBrightnessLevel ランドマークの明るさの段（設定の 1〜6。空港の記号・敷地・略号・山頂の三角の全部。20261022-D01）
     * @param m 描画の寸法（空港の略号の重なりの判定に、文字の大きさの目安と dp → px の倍率を使う。20261023-D02）
     */
    fun build(
        map: BaseMap,
        items: Map<MapItem, MapRange>,
        landmarks: Map<LandmarkItem, MapRange>,
        refLat: Double,
        refLon: Double,
        proj: HudProjection,
        rect: HudRect,
        r1M: Double,
        widthLevel: Int = NavSettings.MAP_LINE_DEFAULT_LEVEL,
        brightnessLevel: Int = NavSettings.MAP_LINE_DEFAULT_LEVEL,
        landmarkBrightnessLevel: Int = NavSettings.MAP_LINE_DEFAULT_LEVEL,
        m: HudMetrics = HudMetrics(),
        minStepPx: Float = Tuning.BASEMAP_MIN_STEP_PX,
        marginPx: Float = Tuning.BASEMAP_MARGIN_PX,
    ): BaseMapScene {
        val shown = items.filter { it.value.shows(r1M) }.keys
        val shownMarks = landmarks.filter { it.value.shows(r1M) }.keys
        if (shown.isEmpty() && shownMarks.isEmpty()) return BaseMapScene(emptyList(), emptyList())
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
            if (layer == MapLayer.ROAD) {
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
            } else if (layer == MapLayer.RAILWAY) {
                // 鉄道: 項目（新幹線・在来線）ごとに、その縮尺で描く種類だけ。在来線 → 新幹線の順に並べる（新幹線が上）。
                // 新幹線は実線、在来線は破線（20261020-D01）
                val conventional = ArrayList<Polyline>()
                val shinkansen = ArrayList<Polyline>()
                for (line in level?.candidates(layer, box.minLat, box.minLon, box.maxLat, box.maxLon).orEmpty()) {
                    val item = railItem(line.kind)
                    if (item !in shown) continue
                    if (!box.intersects(line)) continue
                    val pts = projectThin(line, refLat, refLon, proj, minStepPx)
                    if (pts.size < 2) continue
                    if (item == MapItem.SHINKANSEN) shinkansen += Polyline(pts, Ink.MAP_SHINKANSEN, Tuning.BASEMAP_SHINKANSEN_LINE_DP * wk, LineDash.SOLID, k)
                    else conventional += Polyline(pts, style.ink, style.widthDp * wk, style.dash, k)
                }
                lines += conventional
                lines += shinkansen
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
        // ランドマーク（空港・山頂。20261021-D04。20261022-D01 で空港の敷地・略号と明るさの段を足した）: 線の全部より上。
        // 空港の敷地 → 記号（LANDMARK_DRAW_ORDER の順）→ 空港の略号
        val areas = ArrayList<Polyline>()
        val labels = ArrayList<MapLabel>()
        val runways = ArrayList<Polyline>()
        if (shownMarks.isNotEmpty()) {
            val lk = factor(Tuning.BASEMAP_BRIGHTNESS_FACTORS, landmarkBrightnessLevel)
            if (LandmarkItem.AIRPORT_AREA in shownMarks) {
                for (line in level?.candidates(MapLayer.AIRPORT_AREA, box.minLat, box.minLon, box.maxLat, box.maxLon).orEmpty()) {
                    if (!box.intersects(line)) continue
                    val pts = projectThin(line, refLat, refLon, proj, minStepPx)
                    if (pts.size >= 3) areas += Polyline(pts, Ink.MAP_AIRPORT_AREA, 0f, LineDash.SOLID, lk)
                }
            }
            // 滑走路と延長線は別の項目（20261024-D05）
            val bar = LandmarkItem.RUNWAY in shownMarks
            val extension = LandmarkItem.RUNWAY_EXT in shownMarks
            if (bar || extension) {
                val lines = level?.candidates(MapLayer.RUNWAY, box.minLat, box.minLon, box.maxLat, box.maxLon).orEmpty()
                runways += runwayLines(lines, shownMarks, box, refLat, refLon, proj, m, lk, bar, extension)
            }
            // 略号は、記号が出ている空港にだけ（20261024-D01）
            val showCode = LandmarkItem.AIRPORT_CODE in shownMarks
            val byItem = LANDMARK_DRAW_ORDER.associateWith { ArrayList<MapMark>() }
            val codes = ArrayList<Pair<MapLabel, Int>>()
            for (p in map.points) {
                val item = LandmarkItem.of(p) ?: continue
                val mark = item in shownMarks
                val code = showCode && mark && p.layer == MapLayer.AIRPORT && p.name.isNotEmpty()
                if ((!mark && !code) || !box.contains(p.lat, p.lon)) continue
                val at = project(p.lat, p.lon, refLat, refLon, proj)
                if (mark) byItem.getValue(item) += landmarkMark(item, at, lk)
                // 略号は記号の右上
                if (code) codes += MapLabel(p.name, at, codeOffsetDp(item), Ink.MAP_AIRPORT_CODE, lk) to codePriority(p.kind)
            }
            for (item in LANDMARK_DRAW_ORDER) marks += byItem.getValue(item)
            labels += pickCodeLabels(codes, m)
        }
        return BaseMapScene(lines, marks, areas, labels, runways)
    }

    /**
     * 滑走路と延長線（20261024-D04）: 記号の項目（民間・軍用 × 大・小）が出ている空港の滑走路だけ。
     * 滑走路は両端を結ぶ実線で、太さは実際の幅を今の縮尺で直した太さ（Tuning.BASEMAP_RUNWAY_MIN_DP より細くしない）。
     * 延長線は両端から外へ、画面上の長さ Tuning.BASEMAP_RUNWAY_EXT_DP の破線（端から外へ向かう線。破線は端から描き始める）。
     * 返す並びは延長線の全部が先、滑走路があと（滑走路を上に描く）。20261024-D05 から、滑走路（bar）と延長線（extension）は
     * ランドマークの別の項目で、出すほうだけ返す
     */
    internal fun runwayLines(
        lines: List<MapLine>,
        shownMarks: Set<LandmarkItem>,
        box: LatLonBox,
        refLat: Double,
        refLon: Double,
        proj: HudProjection,
        m: HudMetrics,
        brightness: Float,
        bar: Boolean = true,
        extension: Boolean = true,
    ): List<Polyline> {
        val ext = ArrayList<Polyline>()
        val bars = ArrayList<Polyline>()
        val extPx = Tuning.BASEMAP_RUNWAY_EXT_DP * m.density
        for (line in lines) {
            if (line.size < 2 || !box.intersects(line)) continue
            if (LandmarkItem.airportMark(RunwayKind.airportKind(line.kind)) !in shownMarks) continue
            val a = project(line.lat(0), line.lon(0), refLat, refLon, proj)
            val b = project(line.lat(line.size - 1), line.lon(line.size - 1), refLat, refLon, proj)
            val widthPx = maxOf(Tuning.BASEMAP_RUNWAY_MIN_DP * m.density, (RunwayKind.widthM(line.kind) * proj.pxPerM).toFloat())
            if (bar) bars += Polyline(listOf(a, b), Ink.MAP_RUNWAY, widthPx / m.density, LineDash.SOLID, brightness)
            val len = HudGeometry.dist(a, b)
            if (!extension || len < 0.5f) continue
            val ux = (b.x - a.x) / len
            val uy = (b.y - a.y) / len
            ext += Polyline(listOf(a, P(a.x - ux * extPx, a.y - uy * extPx)), Ink.MAP_RUNWAY_EXT, Tuning.BASEMAP_RUNWAY_EXT_LINE_DP, LineDash.SHORT_DASH, brightness)
            ext += Polyline(listOf(b, P(b.x + ux * extPx, b.y + uy * extPx)), Ink.MAP_RUNWAY_EXT, Tuning.BASEMAP_RUNWAY_EXT_LINE_DP, LineDash.SHORT_DASH, brightness)
        }
        return ext + bars
    }

    /** 空港の略号の優先の順位（大きいほど優先。20261023-D02）: 民間（大）> 軍用（大）> 民間（小）> 軍用（小）（民間 = 国土数値情報の空港、軍用 = OurAirports だけの飛行場） */
    internal fun codePriority(kind: Int): Int =
        (if (AirportKind.isLarge(kind)) 2 else 0) + (if (AirportKind.isOurAirportsOnly(kind)) 0 else 1)

    /**
     * 空港の略号の重なり（20261023-D02）: 画面の上で文字の枠が重なるときは、優先の順位（codePriority）の高い方だけを残す
     * （記号は両方描く）。順位が同じなら前の点（ファイルの順）。残す順に 1 つずつ見て、残したどれかと重なるものは捨てる。
     * 枠は記号の右上（描く所と同じ）に、文字の幅の目安（等幅。HudMetrics.labelCharWidth を Tuning.BASEMAP_AIRPORT_CODE_SP に合わせる）×
     * 文字数と 1 行の高さ（HudMetrics.arrowLabelLine を同じく合わせる）、まわりに黒のふちの分（Tuning.TEXT_OUTLINE_DP の線の外側の半分）を足す。
     * 縮尺・向き・PAN が変わるたびに描く絵を作り直すので、離れれば両方出る。返す並びは元の順
     */
    internal fun pickCodeLabels(cands: List<Pair<MapLabel, Int>>, m: HudMetrics): List<MapLabel> {
        if (cands.size < 2) return cands.map { it.first }
        val k = Tuning.BASEMAP_AIRPORT_CODE_SP / Tuning.HUD_TEXT_METRICS_SP
        val charW = m.labelCharWidth * k
        val lineH = m.arrowLabelLine * k
        val pad = Tuning.TEXT_OUTLINE_DP / 2 * m.density
        // 文字の枠（左・上・右・下）[px]
        fun box(l: MapLabel): FloatArray {
            val off = l.offsetDp * m.density
            val left = l.at.x + off
            val bottom = l.at.y - off
            return floatArrayOf(left - pad, bottom - lineH - pad, left + l.text.length * charW + pad, bottom + pad)
        }
        val boxes = cands.map { box(it.first) }
        val kept = ArrayList<Int>()
        for (i in cands.indices.sortedByDescending { cands[it].second }) {
            val b = boxes[i]
            if (kept.none { j -> val o = boxes[j]; b[0] < o[2] && o[0] < b[2] && b[1] < o[3] && o[1] < b[3] }) kept += i
        }
        return kept.sorted().map { cands[it].first }
    }

    /** 道路の線がどの項目に入るか: 高速道路・その他の自動車専用道路・国道・県道（種類の分からない道路は国道） */
    fun roadItem(kind: Int): MapItem = when (kind) {
        RoadKind.MOTORWAY -> MapItem.MOTORWAY
        RoadKind.OTHER_MOTORWAY -> MapItem.OTHER_MOTORWAY
        RoadKind.SECONDARY -> MapItem.PREF_ROAD
        else -> MapItem.NATIONAL_ROAD
    }

    /** 鉄道の線がどの項目に入るか: 新幹線の印があれば新幹線、ほかは在来線（山形・秋田新幹線も在来線） */
    fun railItem(kind: Int): MapItem = if (RailKind.isShinkansen(kind)) MapItem.SHINKANSEN else MapItem.RAILWAY

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
