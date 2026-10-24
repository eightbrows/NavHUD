package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.geo.EN
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.io.AirportKind
import io.github.eightbrows.navhud.core.io.BaseMap
import io.github.eightbrows.navhud.core.io.MapLayer
import io.github.eightbrows.navhud.core.io.MapLevel
import io.github.eightbrows.navhud.core.io.MapLine
import io.github.eightbrows.navhud.core.io.MapPoint
import io.github.eightbrows.navhud.core.io.PeakKind
import io.github.eightbrows.navhud.core.io.RailKind
import io.github.eightbrows.navhud.core.io.RoadKind
import io.github.eightbrows.navhud.core.io.WaterKind
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.Heading
import io.github.eightbrows.navhud.core.nav.LandmarkItem
import io.github.eightbrows.navhud.core.nav.MapItem
import io.github.eightbrows.navhud.core.nav.MapRange
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.nav.PanView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 背景地図（§8）: 画面に入る線・点を選ぶ、レイヤごとの表示、間引いた段の選び方、点の数を減らす、HUD に入れる。 */
class BaseMapBuilderTest {

    private val rect = HudRect(0f, 0f, 720f, 1200f)

    /** 全部の項目を「すべて」 */
    private val all = MapItem.entries.associateWith { MapRange.ALL }

    /** ランドマークの全部の項目を「すべて」 */
    private val allMarks = LandmarkItem.entries.associateWith { MapRange.ALL }

    /** 選んだ項目だけを range、ほかは描かない */
    private fun items(vararg on: MapItem, range: MapRange = MapRange.ALL) = on.associateWith { range }

    /** 自機（基準の地点）を画面の (360, 600) に置く。pxPerM = 0.1 は 1 px = 10 m */
    private fun proj(upDeg: Double = 0.0, pxPerM: Double = 0.1) = HudProjection(P(360f, 600f), pxPerM, upDeg)

    /** 基準の地点から 東 e・北 n [m] の点を並べた線（試験では細かい単位で持つ） */
    private fun line(layer: MapLayer, vararg en: Pair<Double, Double>) =
        MapLine.of(layer, DoubleArray(en.size) { TestGeo.lat(en[it].second) }, DoubleArray(en.size) { TestGeo.lon(en[it].first) }, unit = 10_000_000)

    private fun point(layer: MapLayer, e: Double, n: Double) = MapPoint(layer, 0, TestGeo.lat(n), TestGeo.lon(e), "")

    /** 道路の線（レイヤの中の種類 = rtt） */
    private fun road(kind: Int, vararg en: Pair<Double, Double>) =
        MapLine.of(MapLayer.ROAD, DoubleArray(en.size) { TestGeo.lat(en[it].second) }, DoubleArray(en.size) { TestGeo.lon(en[it].first) }, kind, unit = 10_000_000)

    private fun build(
        map: BaseMap,
        items: Map<MapItem, MapRange> = all,
        p: HudProjection = proj(),
        r1M: Double = 500.0,
        minStepPx: Float = 1f,
        width: Int = 4,
        brightness: Int = 4,
        landmarks: Map<LandmarkItem, MapRange> = emptyMap(),
        landmarkBrightness: Int = 4,
    ) = BaseMapBuilder.build(map, items, landmarks, TestGeo.LAT0, TestGeo.LON0, p, rect, r1M, width, brightness, landmarkBrightness, minStepPx = minStepPx, marginPx = 0f)

    @Test
    fun visibleBoxCoversTheWholeFrame() {
        // 北が上: 枠は 720×1200 px = 東西 7.2 km・南北 12 km。自機は枠の中央
        val b = BaseMapBuilder.visibleBox(TestGeo.LAT0, TestGeo.LON0, proj(), rect)
        assertEquals(TestGeo.lat(-6000.0), b.minLat, 1e-6)
        assertEquals(TestGeo.lat(6000.0), b.maxLat, 1e-6)
        assertEquals(TestGeo.lon(-3600.0), b.minLon, 3e-5)
        assertEquals(TestGeo.lon(3600.0), b.maxLon, 3e-5)
        // 画面が回っている（ARC で機首方位 45°）ときは、四隅を含む範囲なので東西に広くなる
        val r = BaseMapBuilder.visibleBox(TestGeo.LAT0, TestGeo.LON0, proj(upDeg = 45.0), rect)
        assertTrue(r.maxLon - r.minLon > b.maxLon - b.minLon)
        val half = Math.hypot(3600.0, 6000.0)
        assertTrue(r.maxLat < TestGeo.lat(half + 1) && r.maxLon < TestGeo.lon(half + 1))
    }

    @Test
    fun onlyLinesOverlappingTheFrameAreDrawn() {
        val inside = line(MapLayer.COASTLINE, -1000.0 to 0.0, 1000.0 to 500.0)
        val crossing = line(MapLayer.COASTLINE, -10_000.0 to 1000.0, 10_000.0 to 1000.0)   // 画面を横切る（両端は外）
        val farEast = line(MapLayer.COASTLINE, 20_000.0 to 0.0, 21_000.0 to 0.0)
        val farNorth = line(MapLayer.PREF_BOUNDARY, 0.0 to 30_000.0, 500.0 to 31_000.0)
        val out = build(BaseMap(listOf(inside, crossing, farEast, farNorth))).lines
        assertEquals(2, out.size)
        // 地図から画面への直し方は、軌跡・WP と同じ（基準の地点 = 自機の画面の位置）
        val first = proj().toScreen(Geo.toEN(TestGeo.LAT0, TestGeo.LON0, inside.lat(0), inside.lon(0)))
        assertEquals(first.x, out[0].points[0].x, 0.01f)
        assertEquals(260f, out[0].points[0].x, 0.05f)   // 西 1000 m = 左 100 px
        assertEquals(600f, out[0].points[0].y, 0.05f)
        assertEquals(460f, out[0].points[1].x, 0.05f)
        assertEquals(550f, out[0].points[1].y, 0.05f)   // 北 500 m = 上 50 px
    }

    @Test
    fun eachLayerHasItsOwnLookAndDrawOrder() {
        // 地図の中の順番によらず、描く順（DRAW_ORDER）に並ぶ: 湖沼などの輪郭 → 水 → 境 → 交通
        val layers = listOf(MapLayer.FERRY, MapLayer.ROAD, MapLayer.RAILWAY, MapLayer.PREF_BOUNDARY, MapLayer.MUNI_BOUNDARY,
            MapLayer.COASTLINE, MapLayer.RIVER, MapLayer.INLAND_WATER)
        val out = build(BaseMap(layers.map { line(it, 0.0 to 0.0, 100.0 to 100.0) })).lines
        assertEquals(
            listOf(Ink.MAP_WATER, Ink.MAP_WATER, Ink.COASTLINE, Ink.MUNI_BOUNDARY, Ink.PREF_BOUNDARY, Ink.MAP_NATIONAL_ROAD, Ink.MAP_RAIL, Ink.MAP_FERRY),
            out.map { it.ink },
        )
        // 線の種類: 県境は点線、市区町村境は細かい点線、鉄道（在来線）は破線、フェリーは長い破線、ほかは実線
        assertEquals(
            listOf(LineDash.SOLID, LineDash.SOLID, LineDash.SOLID, LineDash.FINE_DOT, LineDash.DOT, LineDash.SOLID, LineDash.DASH, LineDash.LONG_DASH),
            out.map { it.dash },
        )
        // 線のレイヤどうしは、色か線の種類のどちらかで見分けられる（河川と湖沼の輪郭だけが同じ。どちらも水）
        val looks = layers.map { BaseMapBuilder.styleOf(it).let { s -> s.ink to s.dash } }
        assertEquals(layers.size - 1, looks.distinct().size)
        // 点のレイヤは空港と山頂（20261021-D02）。点はランドマークとして線の全部より上に描く（20261021-D04）
        assertEquals(listOf(MapLayer.AIRPORT, MapLayer.PEAK), MapLayer.entries.filter { it.isPoint })
        assertTrue(BaseMapBuilder.DRAW_ORDER.none { it.isPoint })
        assertEquals(
            listOf(
                LandmarkItem.PEAK_LOW, LandmarkItem.PEAK_MID, LandmarkItem.PEAK_HIGH,
                LandmarkItem.AIRPORT_MIL_SMALL, LandmarkItem.AIRPORT_CIVIL_SMALL, LandmarkItem.AIRPORT_MIL_LARGE, LandmarkItem.AIRPORT_CIVIL_LARGE,
            ),
            BaseMapBuilder.LANDMARK_DRAW_ORDER,
        )
    }

    @Test
    fun overlappingAirportCodesKeepOnlyTheHigherPriority() {
        // 空港の略号の重なり（20261023-D02）: 文字が重なるときは、大 > 小、同じ区分なら国土数値情報 > OurAirports だけの 1 つだけ。記号は両方描く
        fun airport(e: Double, n: Double, kind: Int, code: String) = MapPoint(MapLayer.AIRPORT, kind, TestGeo.lat(n), TestGeo.lon(e), code)
        val oa = AirportKind.OURAIRPORTS
        val map = BaseMap(
            listOf(MapLevel(0.0, emptyList<MapLine>())),
            listOf(
                // 伊江島: 米軍の補助飛行場（OurAirports だけ・小）が先、伊江島空港（国土数値情報・小）があと。東西に 100 m
                airport(0.0, 0.0, oa, "RODE"), airport(100.0, 0.0, 0, "RORE"),
                // 北 2 km: 調布（国土数値情報・小）と横田（OurAirports だけ・大）。東西に 100 m
                airport(0.0, 2_000.0, 0, "RJTF"), airport(100.0, 2_000.0, AirportKind.LARGE or oa, "RJTY"),
                // 南 2 km: 離れた 1 つ（重ならないので出す）
                airport(0.0, -2_000.0, oa, "RJTC"),
            ),
        )
        // 1 px = 10 m: 100 m は 10 px で、4 文字（約 25 px）の文字が重なる
        val wide = build(map, landmarks = allMarks)
        assertEquals(5, wide.marks.size)
        assertEquals(listOf("RORE", "RJTY", "RJTC"), wide.labels.map { it.text })
        // 1 px = 1 m（詳細）: 100 px 離れるので両方出す。並びは元の順
        val detail = build(map, p = proj(pxPerM = 1.0), landmarks = allMarks)
        assertEquals(listOf("RODE", "RORE"), detail.labels.map { it.text })
        // 優先の順位: 大で国土数値情報 3 > 大で OurAirports だけ 2 > 小で国土数値情報 1 > 小で OurAirports だけ 0
        assertEquals(listOf(3, 2, 1, 0), listOf(AirportKind.LARGE, AirportKind.LARGE or oa, 0, oa).map { BaseMapBuilder.codePriority(it) })
        // 画面密度: 文字の枠は dp で決めるので、密度 2 の画面では 1 px = 5 m でも重なる
        assertEquals(listOf("RORE", "RJTY", "RJTC"), BaseMapBuilder.build(map, emptyMap(), allMarks, TestGeo.LAT0, TestGeo.LON0, proj(pxPerM = 0.2), rect, 500.0, m = HudMetrics().scaled(2f), marginPx = 0f).labels.map { it.text })
    }

    @Test
    fun landmarksAreAirportsAreasCodesAndThreePeakTiers() {
        // ランドマーク（20261022-D01。20261024-D01 で空港を民間・軍用に分けた）: 空港・民間（大・小）・軍用（大・小）・空港の敷地・空港の略号・
        // 山頂（高・中・低）。山頂は 3 段とも三角
        fun peak(e: Double, n: Double, elevM: Int, landmark: Boolean) =
            MapPoint(MapLayer.PEAK, (elevM / PeakKind.ELEVATION_STEP_M) or (if (landmark) PeakKind.LANDMARK else 0), TestGeo.lat(n), TestGeo.lon(e), "")
        fun airport(e: Double, n: Double, large: Boolean, code: String, military: Boolean = false) =
            MapPoint(
                MapLayer.AIRPORT, (if (large) AirportKind.LARGE else 0) or (if (military) AirportKind.OURAIRPORTS else 0),
                TestGeo.lat(n), TestGeo.lon(e), code,
            )
        val ring = line(MapLayer.AIRPORT_AREA, 250.0 to -50.0, 350.0 to -50.0, 350.0 to 50.0, 250.0 to 50.0, 250.0 to -50.0)
        val map = BaseMap(
            listOf(MapLevel(0.0, listOf(line(MapLayer.COASTLINE, 0.0 to 0.0, 100.0 to 100.0), ring))),
            listOf(
                airport(300.0, 0.0, true, "RJTT"), airport(-300.0, 0.0, false, ""), airport(0.0, 300.0, false, "RJTF"),
                airport(0.0, -300.0, true, "RJTA", military = true), airport(-300.0, 300.0, false, "RJTC", military = true),
                peak(100.0, 0.0, 3776, true), peak(0.0, 100.0, 877, true), peak(-100.0, 0.0, 2236, true),
                peak(0.0, -100.0, 1476, false), peak(50.0, -50.0, 3180, false),
            ),
        )
        val d = build(map, landmarks = allMarks)
        // 記号の順（下から上）: 山頂（低）→（中）→（高）→ 空港・軍用（小）→ 民間（小）→ 軍用（大）→ 民間（大）。山頂は 3 段とも三角。
        // 空港は民間（小）が塗りつぶし、民間（大）が塗りつぶしに丸い輪、軍用は白抜き
        assertEquals(
            List(5) { MarkShape.TRIANGLE } +
                listOf(MarkShape.PLANE_HOLLOW, MarkShape.PLANE, MarkShape.PLANE, MarkShape.PLANE_HOLLOW, MarkShape.PLANE_RING),
            d.marks.map { it.shape },
        )
        assertEquals(
            listOf(Ink.MAP_PEAK_LOW, Ink.MAP_PEAK_LOW, Ink.MAP_PEAK_MID, Ink.MAP_PEAK_HIGH, Ink.MAP_PEAK_HIGH) + List(5) { Ink.MAP_AIRPORT },
            d.marks.map { it.ink },
        )
        // 大きさ: 山頂 低 8・中 10・高 12dp、空港 小 11（民間・軍用とも）・軍用（大）16・民間（大）20dp
        assertEquals(listOf(8f, 8f, 10f, 12f, 12f, 11f, 11f, 11f, 16f, 20f), d.marks.map { it.sizeDp })
        // 項目の分け方: 山頂は目印になる山で 1,500 m 以上が高、未満が中、目印になりにくい山が低。空港は滑走路 2,000 m 以上（変換の印）が大
        fun item(elevM: Int, landmark: Boolean) = LandmarkItem.of(peak(0.0, 0.0, elevM, landmark))
        assertEquals(listOf(LandmarkItem.PEAK_MID, LandmarkItem.PEAK_HIGH, LandmarkItem.PEAK_HIGH), listOf(1450, 1500, 3750).map { item(it, true) })
        assertEquals(LandmarkItem.PEAK_LOW, item(3750, false))
        // 空港の民間・軍用: OurAirports だけにある飛行場（変換の印 AirportKind.OURAIRPORTS）が軍用、国土数値情報の空港が民間
        assertEquals(LandmarkItem.AIRPORT_CIVIL_LARGE, LandmarkItem.of(airport(0.0, 0.0, true, "")))
        assertEquals(LandmarkItem.AIRPORT_CIVIL_SMALL, LandmarkItem.of(airport(0.0, 0.0, false, "")))
        assertEquals(LandmarkItem.AIRPORT_MIL_LARGE, LandmarkItem.of(airport(0.0, 0.0, true, "", military = true)))
        assertEquals(LandmarkItem.AIRPORT_MIL_SMALL, LandmarkItem.of(airport(0.0, 0.0, false, "", military = true)))
        // 空港の敷地: 1 つの輪を塗る（記号より下に描く）
        assertEquals(listOf(Ink.MAP_AIRPORT_AREA), d.areas.map { it.ink })
        assertEquals(5, d.areas.single().points.size)
        // 空港の略号: 略号のある空港だけ。記号の右上（記号の幅の半分だけずらす。民間（大）は丸い輪にかからないよう、輪の半径 14dp の 0.8 倍）
        assertEquals(listOf("RJTT" to 11.2f, "RJTF" to 5.5f, "RJTA" to 8f, "RJTC" to 5.5f), d.labels.map { it.text to it.offsetDp })
        assertEquals(d.marks.last().at, d.labels.first().at)
        // 略号は、記号が出ている空港にだけ出す（20261024-D01。前は記号を OFF にしても略号だけ出せた）
        assertTrue(build(map, landmarks = mapOf(LandmarkItem.AIRPORT_CODE to MapRange.ALL)).labels.isEmpty())
        val milLargeOnly = build(map, landmarks = mapOf(LandmarkItem.AIRPORT_CODE to MapRange.ALL, LandmarkItem.AIRPORT_MIL_LARGE to MapRange.ALL))
        assertEquals(listOf("RJTA"), milLargeOnly.labels.map { it.text })
        // 項目ごとに選べる: 山頂（高）と空港・民間（大）だけ
        val highAndLarge = mapOf(LandmarkItem.PEAK_HIGH to MapRange.ALL, LandmarkItem.AIRPORT_CIVIL_LARGE to MapRange.ALL)
        assertEquals(listOf(Ink.MAP_PEAK_HIGH, Ink.MAP_PEAK_HIGH, Ink.MAP_AIRPORT), build(map, landmarks = highAndLarge).marks.map { it.ink })
        // 初期値: 敷地は〜10k、略号は〜25k。R1 25 km では敷地を描かず略号は描く、R1 50 km では略号も描かない
        val r25 = build(map, r1M = 25_000.0, landmarks = NavSettings.DEFAULT_LANDMARKS)
        assertTrue(r25.areas.isEmpty())
        assertEquals(4, r25.labels.size)
        assertTrue(build(map, r1M = 50_000.0, landmarks = NavSettings.DEFAULT_LANDMARKS).labels.isEmpty())
        // ランドマークの明るさの段: 段 4 は 1。段 6 は 1.6 を、記号・敷地・略号の全部に。大きさ・色の種類は変わらない
        assertTrue(d.marks.all { it.brightness == 1f } && d.areas.all { it.brightness == 1f } && d.labels.all { it.brightness == 1f })
        val lit = build(map, landmarks = allMarks, landmarkBrightness = 6)
        val k6 = Tuning.BASEMAP_BRIGHTNESS_FACTORS[5]
        assertTrue(lit.marks.all { it.brightness == k6 } && lit.areas.all { it.brightness == k6 } && lit.labels.all { it.brightness == k6 })
        assertEquals(d.marks.map { it.sizeDp to it.ink }, lit.marks.map { it.sizeDp to it.ink })
        // 線の太さ・明るさの段は、ランドマークに効かない
        assertEquals(d.marks, build(map, r1M = 500.0, width = 6, brightness = 1, landmarks = allMarks).marks)
        // 線の項目がなくても、ランドマークだけ描ける（背景地図のスイッチが OFF のとき）
        val only = build(map, emptyMap(), landmarks = allMarks)
        assertTrue(only.lines.isEmpty())
        assertEquals(d.marks, only.marks)
        // ランドマークが全部 OFF なら何も描かない
        assertTrue(build(map, landmarks = LandmarkItem.entries.associateWith { MapRange.OFF }).let { it.marks.isEmpty() && it.areas.isEmpty() && it.labels.isEmpty() })
    }

    @Test
    fun shinkansenAndConventionalRailwaysAreTheirOwnItems() {
        // 鉄道の種類: 下位は fco、0x10 = 新幹線、0x20 = 山形・秋田（在来線として描く）。地図の中では混ざって並んでいる
        fun rail(kind: Int, vararg en: Pair<Double, Double>) =
            MapLine.of(MapLayer.RAILWAY, DoubleArray(en.size) { TestGeo.lat(en[it].second) }, DoubleArray(en.size) { TestGeo.lon(en[it].first) }, kind, unit = 10_000_000)
        val map = BaseMap(listOf(
            rail(2 or RailKind.SHINKANSEN, 0.0 to -1000.0, 0.0 to 1000.0),
            rail(2, -1000.0 to 0.0, 1000.0 to 0.0),
            rail(3 or RailKind.MINI_SHINKANSEN, -1000.0 to -1000.0, 1000.0 to 1000.0),
            rail(3 or RailKind.SHINKANSEN, 500.0 to -1000.0, 500.0 to 1000.0),
        ))
        assertEquals(MapItem.SHINKANSEN, BaseMapBuilder.railItem(2 or RailKind.SHINKANSEN))
        assertEquals(MapItem.RAILWAY, BaseMapBuilder.railItem(3 or RailKind.MINI_SHINKANSEN))
        assertEquals(MapItem.RAILWAY, BaseMapBuilder.railItem(0))
        // 両方: 在来線（山形・秋田を含む）→ 新幹線の順（新幹線が上）。新幹線は明るい紫で少し太い
        val both = build(map, items(MapItem.SHINKANSEN, MapItem.RAILWAY)).lines
        assertEquals(listOf(Ink.MAP_RAIL, Ink.MAP_RAIL, Ink.MAP_SHINKANSEN, Ink.MAP_SHINKANSEN), both.map { it.ink })
        assertEquals(listOf(Tuning.BASEMAP_RAIL_LINE_DP, Tuning.BASEMAP_RAIL_LINE_DP, Tuning.BASEMAP_SHINKANSEN_LINE_DP, Tuning.BASEMAP_SHINKANSEN_LINE_DP), both.map { it.widthDp })
        assertTrue(Tuning.BASEMAP_SHINKANSEN_LINE_DP > Tuning.BASEMAP_RAIL_LINE_DP)
        // 線の種類: 新幹線は実線、在来線は破線（20261020-D01）
        assertEquals(listOf(LineDash.DASH, LineDash.DASH, LineDash.SOLID, LineDash.SOLID), both.map { it.dash })
        // 片方だけ
        assertEquals(listOf(Ink.MAP_SHINKANSEN, Ink.MAP_SHINKANSEN), build(map, items(MapItem.SHINKANSEN)).lines.map { it.ink })
        assertEquals(listOf(Ink.MAP_RAIL, Ink.MAP_RAIL), build(map, items(MapItem.RAILWAY)).lines.map { it.ink })
        // 縮尺も別に選べる（この試験の R1 は 500 m）
        assertTrue(build(map, mapOf(MapItem.SHINKANSEN to MapRange.ALL, MapItem.RAILWAY to MapRange.OFF)).lines.all { it.ink == Ink.MAP_SHINKANSEN })
        // 初期値は両方とも OFF
        assertEquals(MapRange.OFF, NavSettings().mapRangeOf(MapItem.SHINKANSEN))
        assertEquals(MapRange.OFF, NavSettings().mapRangeOf(MapItem.RAILWAY))
    }

    @Test
    fun onlySelectedLayersAreDrawn() {
        val map = BaseMap(
            listOf(MapLevel(0.0, listOf(line(MapLayer.COASTLINE, 0.0 to 0.0, 100.0 to 100.0), line(MapLayer.ROAD, 0.0 to 0.0, -100.0 to 100.0)))),
            listOf(point(MapLayer.AIRPORT, 200.0, 200.0), point(MapLayer.AIRPORT, 50_000.0, 0.0)),
        )
        // 初期値（高速道路・国道・空港が「すべて」。20261021-D01）: 海岸線は描かない。種類の分からない道路は国道。画面の外の空港は描かない
        val d = build(map, NavSettings.DEFAULT_MAP_ITEMS, landmarks = NavSettings.DEFAULT_LANDMARKS)
        assertEquals(listOf(Ink.MAP_NATIONAL_ROAD), d.lines.map { it.ink })
        assertEquals(listOf(Ink.MAP_AIRPORT), d.marks.map { it.ink })
        // 海岸線だけ
        val c = build(map, items(MapItem.COASTLINE))
        assertEquals(listOf(Ink.COASTLINE), c.lines.map { it.ink })
        assertTrue(c.marks.isEmpty())
        // 国道（種類の分からない道路も国道）だけ
        val r = build(map, items(MapItem.NATIONAL_ROAD))
        assertEquals(listOf(Ink.MAP_NATIONAL_ROAD), r.lines.map { it.ink })
        assertTrue(r.marks.isEmpty())
        // 全部: 画面の外の点（東 50 km の空港）は描かない
        val a = build(map, all, landmarks = allMarks)
        assertEquals(listOf(Ink.MAP_AIRPORT), a.marks.map { it.ink })
        assertEquals(380f, a.marks.single().at.x, 0.05f)   // 東 200 m = 右 20 px
        // 何も選ばなければ何も描かない
        assertTrue(build(map, emptyMap()).let { it.lines.isEmpty() && it.marks.isEmpty() })
        assertTrue(build(map, MapItem.entries.associateWith { MapRange.OFF }).let { it.lines.isEmpty() && it.marks.isEmpty() })
    }

    @Test
    fun itemsAreDrawnOnlyUpToTheirRange() {
        val map = BaseMap(listOf(line(MapLayer.COASTLINE, 0.0 to 0.0, 100.0 to 100.0)))
        fun drawn(range: MapRange, r1Km: Double) = build(map, items(MapItem.COASTLINE, range = range), r1M = r1Km * 1000).lines.isNotEmpty()
        // R1 がその値以下のときだけ描く（ちょうどの値は描く）
        assertEquals(listOf(true, true, false), listOf(5.0, 10.0, 25.0).map { drawn(MapRange.UPTO_10KM, it) })
        assertEquals(listOf(true, false), listOf(25.0, 50.0).map { drawn(MapRange.UPTO_25KM, it) })
        assertEquals(listOf(true, false), listOf(50.0, 100.0).map { drawn(MapRange.UPTO_50KM, it) })
        assertEquals(listOf(true, true, true), listOf(0.025, 25.0, 100.0).map { drawn(MapRange.ALL, it) })
        assertEquals(listOf(false, false), listOf(0.025, 100.0).map { drawn(MapRange.OFF, it) })
    }

    @Test
    fun roadItemsChooseTheKindsOfRoads() {
        val map = BaseMap(listOf(
            road(RoadKind.MOTORWAY, -1000.0 to 0.0, 1000.0 to 0.0),
            road(RoadKind.PRIMARY, 0.0 to -1000.0, 0.0 to 1000.0),
            road(RoadKind.SECONDARY, -1000.0 to -1000.0, 1000.0 to 1000.0),
        ))
        // 高速道路・国道・県道の項目が、それぞれの種類の線を描く
        assertEquals(listOf(3f), build(map, items(MapItem.MOTORWAY)).lines.map { it.widthDp })
        assertEquals(listOf(1.5f), build(map, items(MapItem.NATIONAL_ROAD)).lines.map { it.widthDp })
        assertEquals(listOf(0.7f), build(map, items(MapItem.PREF_ROAD)).lines.map { it.widthDp })
        val on = build(map, items(MapItem.MOTORWAY, MapItem.NATIONAL_ROAD, MapItem.PREF_ROAD)).lines
        assertEquals(3, on.size)
        assertEquals(listOf(Ink.MAP_ROAD, Ink.MAP_NATIONAL_ROAD, Ink.MAP_MOTORWAY), on.map { it.ink })
        // 項目ごとに縮尺が違う: R1 50 km で、高速道路は「すべて」・国道は「〜25 km」なら高速道路だけ
        val mixed = mapOf(MapItem.MOTORWAY to MapRange.ALL, MapItem.NATIONAL_ROAD to MapRange.UPTO_25KM, MapItem.PREF_ROAD to MapRange.UPTO_10KM)
        assertEquals(listOf(3f), build(map, mixed, r1M = 50_000.0).lines.map { it.widthDp })
        assertEquals(listOf(1.5f, 3f), build(map, mixed, r1M = 25_000.0).lines.map { it.widthDp })
        // ほかのレイヤの種類（rtt と同じ 15 の値でも）は飛ばさない（河川・湖沼の項目で描く）
        val river = MapLine.of(MapLayer.RIVER, doubleArrayOf(TestGeo.LAT0, TestGeo.lat(100.0)), doubleArrayOf(TestGeo.LON0, TestGeo.LON0), RoadKind.SECONDARY, unit = 10_000_000)
        assertEquals(1, build(BaseMap(listOf(river)), items(MapItem.WATER)).lines.size)
        // 道路の項目が全部 OFF なら描かない
        assertTrue(build(map, items(MapItem.COASTLINE)).lines.isEmpty())
    }

    @Test
    fun wideRiversAreBanksInDetailAndOneCenterLineFrom10Km() {
        // 幅のある川: 川の面の輪郭（湖沼などの印付き）と、川の中心の線（河川の印付き）。ふつうの川・湖は印なし
        fun water(layer: MapLayer, kind: Int, vararg en: Pair<Double, Double>) =
            MapLine.of(layer, DoubleArray(en.size) { TestGeo.lat(en[it].second) }, DoubleArray(en.size) { TestGeo.lon(en[it].first) }, kind, unit = 10_000_000)
        val bank = water(MapLayer.INLAND_WATER, 3 or WaterKind.WIDE_RIVER, -1000.0 to -50.0, 1000.0 to -50.0, 1000.0 to 50.0, -1000.0 to 50.0, -1000.0 to -50.0)
        val center = water(MapLayer.RIVER, 3 or WaterKind.WIDE_RIVER, -1000.0 to 0.0, 1000.0 to 0.0)
        val river = water(MapLayer.RIVER, 3, 1000.0 to 0.0, 2000.0 to 500.0)
        val lake = water(MapLayer.INLAND_WATER, 3, 0.0 to 200.0, 100.0 to 200.0, 100.0 to 300.0, 0.0 to 200.0)
        val map = BaseMap(listOf(bank, center, river, lake))
        fun drawn(r1Km: Double) = build(map, items(MapItem.WATER), r1M = r1Km * 1000, p = proj(pxPerM = 0.01)).lines.map { it.points.size }.sorted()
        // R1 10 km 未満: 両岸の輪郭（5 点）・ふつうの川・湖。中心の線は描かない
        assertEquals(listOf(2, 4, 5), drawn(5.0))
        // R1 10 km 以上: 中心の線（2 点）・ふつうの川・湖。両岸の輪郭は描かない
        assertEquals(listOf(2, 2, 4), drawn(10.0))
        assertEquals(listOf(2, 2, 4), drawn(100.0))
        assertEquals(10_000.0, Tuning.BASEMAP_WIDE_RIVER_R1_M, 0.0)
    }

    @Test
    fun waterItemDrawsRiversAndLakeOutlinesInTheSameBlue() {
        val map = BaseMap(listOf(
            line(MapLayer.RIVER, -1000.0 to 0.0, 1000.0 to 0.0),
            line(MapLayer.INLAND_WATER, 0.0 to 0.0, 200.0 to 0.0, 200.0 to 200.0, 0.0 to 0.0),
        ))
        val out = build(map, items(MapItem.WATER)).lines
        assertEquals(2, out.size)
        assertTrue(out.all { it.ink == Ink.MAP_WATER && it.dash == LineDash.SOLID })
        assertTrue(build(map, items(MapItem.COASTLINE)).lines.isEmpty())
    }

    @Test
    fun otherMotorwaysAreTheirOwnItemBetweenNationalRoadsAndMotorways() {
        val map = BaseMap(listOf(
            road(RoadKind.OTHER_MOTORWAY, -1000.0 to 300.0, 1000.0 to 300.0),
            road(RoadKind.MOTORWAY, -1000.0 to 0.0, 1000.0 to 0.0),
            road(RoadKind.PRIMARY, 0.0 to -1000.0, 0.0 to 1000.0),
        ))
        // 「その他の自動車専用道路」の項目だけが、種類 17 の線を描く（高速道路の項目では描かない）
        assertEquals(listOf(Ink.MAP_OTHER_MOTORWAY), build(map, items(MapItem.OTHER_MOTORWAY)).lines.map { it.ink })
        assertEquals(listOf(Ink.MAP_MOTORWAY), build(map, items(MapItem.MOTORWAY)).lines.map { it.ink })
        // 太さは国道と同じ 1.5dp。描く順は国道と高速道路の間
        val all = build(map, items(MapItem.MOTORWAY, MapItem.OTHER_MOTORWAY, MapItem.NATIONAL_ROAD)).lines
        assertEquals(listOf(Ink.MAP_NATIONAL_ROAD, Ink.MAP_OTHER_MOTORWAY, Ink.MAP_MOTORWAY), all.map { it.ink })
        assertEquals(listOf(1.5f, 1.5f, 3f), all.map { it.widthDp })
        assertEquals(MapItem.OTHER_MOTORWAY, BaseMapBuilder.roadItem(RoadKind.OTHER_MOTORWAY))
        // 縮尺も別に選べる
        assertEquals(listOf(Ink.MAP_MOTORWAY), build(map, mapOf(MapItem.MOTORWAY to MapRange.ALL, MapItem.OTHER_MOTORWAY to MapRange.UPTO_10KM), r1M = 25_000.0).lines.map { it.ink })
    }

    @Test
    fun roadsAreSplitByWidthAndColor() {
        // 地図の中では種類が混ざって並んでいる
        val map = BaseMap(listOf(
            road(RoadKind.PRIMARY, -1000.0 to 0.0, 1000.0 to 0.0),
            road(RoadKind.MOTORWAY, 0.0 to -1000.0, 0.0 to 1000.0),
            road(RoadKind.SECONDARY, -1000.0 to -1000.0, 1000.0 to 1000.0),
            road(RoadKind.PRIMARY, -1000.0 to 500.0, 1000.0 to 500.0),
            road(RoadKind.MOTORWAY, 500.0 to -1000.0, 500.0 to 1000.0),
        ))
        // 太さ: 高速道路 3.0・国道 1.5・県道 0.7 [dp]。細い線から（県道 → 国道 → 高速道路）並ぶので、同じ太さが続く
        val roads = items(MapItem.MOTORWAY, MapItem.NATIONAL_ROAD, MapItem.PREF_ROAD)
        val out = build(map, roads, brightness = 2).lines
        assertEquals(listOf(0.7f, 1.5f, 1.5f, 3f, 3f), out.map { it.widthDp })
        // 色: 県道は茶・国道は赤・高速道路は青。線の種類はどれも実線。明るさはどの道路にも効く
        assertEquals(listOf(Ink.MAP_ROAD, Ink.MAP_NATIONAL_ROAD, Ink.MAP_NATIONAL_ROAD, Ink.MAP_MOTORWAY, Ink.MAP_MOTORWAY), out.map { it.ink })
        assertTrue(out.all { it.dash == LineDash.SOLID })
        assertTrue(out.all { it.brightness == Tuning.BASEMAP_BRIGHTNESS_FACTORS[1] })
        // 県道が OFF なら、国道と高速道路だけ
        assertEquals(listOf(1.5f, 1.5f, 3f, 3f), build(map, items(MapItem.MOTORWAY, MapItem.NATIONAL_ROAD)).lines.map { it.widthDp })
        // 線の太さの段 6 は、比を保って全部に 1.8 倍
        val w = Tuning.BASEMAP_WIDTH_FACTORS[5]
        assertEquals(listOf(0.7f * w, 1.5f * w, 1.5f * w, 3f * w, 3f * w), build(map, roads, width = 6).lines.map { it.widthDp })
        // 種類の分からない道路（0）は国道と同じ太さ
        assertEquals(Tuning.BASEMAP_ROAD_LINE_DP, BaseMapBuilder.roadWidthDp(0))
    }

    @Test
    fun lineWidthAndBrightnessApplyToTheWholeMapAndAirportIsAPlane() {
        val map = BaseMap(
            listOf(MapLevel(0.0, listOf(line(MapLayer.COASTLINE, 0.0 to 0.0, 100.0 to 100.0), line(MapLayer.RIVER, 0.0 to 0.0, -100.0 to 100.0)))),
            listOf(point(MapLayer.AIRPORT, 200.0, 200.0)),
        )
        // 初期値（太さ 4・明るさ 4）: 掛ける数は 1（今の太さ・色）
        val d = build(map, landmarks = allMarks)
        assertTrue(d.lines.all { it.brightness == 1f })
        assertEquals(listOf(Tuning.BASEMAP_RIVER_LINE_DP, Tuning.BASEMAP_COAST_LINE_DP), d.lines.map { it.widthDp })
        // 段 6: 全部の線の太さに 1.8 倍・全部の線の色に 1.6 倍（項目どうしの比は同じ）。空港の記号は大きさも色も変えない（20261021-D01）
        val b = build(map, width = 6, brightness = 6, landmarks = allMarks)
        val w = Tuning.BASEMAP_WIDTH_FACTORS[5]
        assertEquals(listOf(Tuning.BASEMAP_RIVER_LINE_DP * w, Tuning.BASEMAP_COAST_LINE_DP * w), b.lines.map { it.widthDp })
        assertTrue(b.lines.all { it.brightness == Tuning.BASEMAP_BRIGHTNESS_FACTORS[5] })
        assertEquals(d.marks, b.marks)
        // 段 1: 全部の線の太さに 0.4 倍・色に 0.4 倍（細く・暗く）
        val t = build(map, width = 1, brightness = 1, landmarks = allMarks)
        assertEquals(listOf(Tuning.BASEMAP_RIVER_LINE_DP * 0.4f, Tuning.BASEMAP_COAST_LINE_DP * 0.4f), t.lines.map { it.widthDp })
        assertTrue(t.lines.all { it.brightness == 0.4f })
        assertEquals(d.marks, t.marks)
        // 6 段。段が大きいほど太く・明るい。初期値の段 4 が 1。範囲の外は端の段
        for (f in listOf(Tuning.BASEMAP_WIDTH_FACTORS, Tuning.BASEMAP_BRIGHTNESS_FACTORS)) {
            assertEquals(6, f.size)
            assertEquals(f.sorted(), f)
            assertEquals(1f, f[NavSettings.MAP_LINE_DEFAULT_LEVEL - 1])
            assertEquals(f.last(), BaseMapBuilder.factor(f, 9))
            assertEquals(f.first(), BaseMapBuilder.factor(f, 0))
        }
        // 空港は飛行機の記号（塗りと黒のふち。形は Canvas が描く）。この空港は小さい空港（種類 0）なので Tuning.BASEMAP_AIRPORT_SMALL_MARK_DP（11dp。民間（大）は 20dp・軍用（大）は 16dp。縮尺によらない）
        val plane = d.marks.single()
        assertEquals(Ink.MAP_AIRPORT, plane.ink)
        assertEquals(20f, Tuning.BASEMAP_AIRPORT_CIVIL_LARGE_MARK_DP)
        assertEquals(16f, Tuning.BASEMAP_AIRPORT_MIL_LARGE_MARK_DP)
        assertEquals(11f, Tuning.BASEMAP_AIRPORT_SMALL_MARK_DP)
        assertEquals(Tuning.BASEMAP_AIRPORT_SMALL_MARK_DP, plane.sizeDp)
        assertEquals(plane.sizeDp, build(map, r1M = 100_000.0, landmarks = allMarks).marks.single().sizeDp)
        assertEquals(plane.sizeDp, build(map, r1M = 250.0, landmarks = allMarks).marks.single().sizeDp)
    }

    @Test
    fun thinnedLevelIsChosenByR1() {
        // 段ごとに違う線を入れて、どの段が選ばれたかを見る
        val l0 = line(MapLayer.COASTLINE, 0.0 to 0.0, 10.0 to 10.0, 20.0 to 0.0)
        val l1 = line(MapLayer.COASTLINE, 0.0 to 0.0, 20.0 to 0.0)
        val l2 = line(MapLayer.COASTLINE, 0.0 to 0.0, 30.0 to 0.0)
        val map = BaseMap(listOf(MapLevel(25_000.0, listOf(l1)), MapLevel(0.0, listOf(l0)), MapLevel(100_000.0, listOf(l2))))
        assertEquals(listOf(0.0, 25_000.0, 100_000.0), map.levels.map { it.minR1M })
        // R1 25 km 未満は段 0（間引かない）、25 km 以上は段 1、100 km 以上は段 2
        assertSame(l0, map.levelFor(250.0)!!.lines.single())
        assertSame(l0, map.levelFor(10_000.0)!!.lines.single())
        assertSame(l1, map.levelFor(25_000.0)!!.lines.single())
        assertSame(l1, map.levelFor(50_000.0)!!.lines.single())
        assertSame(l2, map.levelFor(100_000.0)!!.lines.single())
        // 描くときも同じ段を使う（点の数で見分ける。1 px = 0.5 m なので、どの点も飛ばさない）
        assertEquals(3, build(map, r1M = 2_500.0, p = proj(pxPerM = 2.0)).lines.single().points.size)
        assertEquals(2, build(map, r1M = 25_000.0, p = proj(pxPerM = 2.0)).lines.single().points.size)
    }

    @Test
    fun thinDropsPointsCloserThanOnePixel() {
        val pts = listOf(P(0f, 0f), P(0.3f, 0f), P(0.6f, 0f), P(1.2f, 0f), P(1.5f, 0f), P(5f, 0f), P(5.2f, 0f))
        // 前に残した点から 1 px 以上離れた点だけ残す。最初と最後の点は残す
        assertEquals(listOf(P(0f, 0f), P(1.2f, 0f), P(5f, 0f), P(5.2f, 0f)), BaseMapBuilder.thin(pts, 1f))
        // 0 なら減らさない。2 点以下もそのまま
        assertEquals(pts, BaseMapBuilder.thin(pts, 0f))
        assertEquals(pts.take(2), BaseMapBuilder.thin(pts.take(2), 1f))
        // 全部が同じ位置なら 1 点だけになる（描かない）
        assertEquals(1, BaseMapBuilder.thin(List(5) { P(3f, 3f) }, 1f).size)
    }

    @Test
    fun wideRangeHasFewerPointsThanDetail() {
        // 10 m おきの 401 点（4 km）の線。広域（1 px = 70 m）では 1 px より近い点を飛ばすので少なくなる。詳細（1 px = 0.5 m）では全部残る
        val dense = line(MapLayer.COASTLINE, *Array(401) { (it * 10.0 - 2000.0) to 0.0 })
        val pw = proj(pxPerM = 1.0 / 70)
        val wide = build(BaseMap(listOf(dense)), p = pw).lines.single()
        val detail = build(BaseMap(listOf(dense)), p = proj(pxPerM = 2.0)).lines.single()
        assertTrue("広域 ${wide.points.size} 点", wide.points.size in 50..70)
        assertEquals(401, detail.points.size)
        // 最初と最後の点は残る
        assertEquals(pw.toScreen(EN(-2000.0, 0.0)).x, wide.points.first().x, 0.05f)
        assertEquals(pw.toScreen(EN(2000.0, 0.0)).x, wide.points.last().x, 0.05f)
    }

    private fun state(settings: NavSettings = NavSettings(), pan: PanView? = null) = NavState(
        nowMs = 0,
        fix = Fix(timeMs = 0, lat = TestGeo.LAT0, lon = TestGeo.LON0),
        heading = Heading(30f, HeadingSrc.GPS),
        settings = settings,
        rangeM = 2_000.0,
        pan = pan,
    )

    private val map = BaseMap(
        listOf(MapLevel(0.0, listOf(
            line(MapLayer.COASTLINE, -500.0 to 200.0, 500.0 to 300.0),
            line(MapLayer.PREF_BOUNDARY, 0.0 to -300.0, 0.0 to 300.0),
            line(MapLayer.ROAD, -300.0 to -300.0, 300.0 to 300.0),
        ))),
        listOf(point(MapLayer.AIRPORT, 100.0, 100.0)),
    )

    @Test
    fun hudSceneDrawsTheSelectedLayersWhenSwitchedOn() {
        // 初期値: 背景地図 ON、高速道路・国道が「すべて」、ランドマークは全部「すべて」（この地図にあるのは国道（種類の分からない道路）と空港）
        val on = HudSceneBuilder.build(state(), rect, baseMap = map)
        assertEquals(listOf(Ink.MAP_NATIONAL_ROAD), on.baseMap.map { it.ink })
        assertEquals(1, on.baseMapMarks.size)
        // 項目を足すと描く（海岸線と県境）。空港を OFF にすると描かない
        val more = HudSceneBuilder.build(state(NavSettings().withMapRange(MapItem.COASTLINE, MapRange.ALL).withMapRange(MapItem.PREF_BOUNDARY, MapRange.UPTO_10KM)), rect, baseMap = map)
        assertEquals(listOf(Ink.COASTLINE, Ink.PREF_BOUNDARY, Ink.MAP_NATIONAL_ROAD), more.baseMap.map { it.ink })
        assertTrue(HudSceneBuilder.build(state(NavSettings().withLandmarkRange(LandmarkItem.AIRPORT_CIVIL_SMALL, MapRange.OFF)), rect, baseMap = map).baseMapMarks.isEmpty())
        // 背景地図を OFF にすると、選んだ線の項目によらず線は全部描かない。ランドマークは描く（20261021-D04）
        val off = HudSceneBuilder.build(state(NavSettings(baseMap = false, mapItems = all)), rect, baseMap = map)
        assertTrue(off.baseMap.isEmpty())
        assertEquals(on.baseMapMarks, off.baseMapMarks)
        val noMarks = NavSettings(baseMap = false, landmarks = LandmarkItem.entries.associateWith { MapRange.OFF })
        assertTrue(HudSceneBuilder.build(state(noMarks), rect, baseMap = map).let { it.baseMap.isEmpty() && it.baseMapMarks.isEmpty() })
        // 背景地図がまだ読めていない（null）ときは描かない
        assertTrue(HudSceneBuilder.build(state(), rect, baseMap = null).baseMap.isEmpty())
        // North Up と PAN でも描く。PAN は PAN の中心が基準
        assertEquals(1, HudSceneBuilder.build(state(NavSettings(displayMode = DisplayMode.NORTH_UP)), rect, baseMap = map).baseMap.size)
        val pan = HudSceneBuilder.build(state(pan = PanView(TestGeo.lat(200.0), TestGeo.LON0, 0.0)), rect, baseMap = map)
        assertEquals(1, pan.baseMap.size)
        assertFalse(pan.baseMap == on.baseMap)
        // PAN のときだけ、地図の中心（PAN の中心を描く点 = 画面の横の中央）に十字。PAN でなければ出さない（20261023-D01）
        assertEquals(rect.centerX, pan.crosshair!!.x, 0.01f)
        assertEquals(null, on.crosshair)
        // 背景地図は軌跡とは別のリスト（Canvas がいちばん下に描く）
        assertTrue(on.trails.none { it.ink == Ink.MAP_NATIONAL_ROAD })
        // 設定の線の太さ・明るさが届く
        val lit = HudSceneBuilder.build(state(NavSettings(mapLineWidth = 2, mapLineBrightness = 3)), rect, baseMap = map)
        assertTrue(lit.baseMap.all { it.brightness == Tuning.BASEMAP_BRIGHTNESS_FACTORS[2] })
        assertEquals(on.baseMap.map { it.widthDp * Tuning.BASEMAP_WIDTH_FACTORS[1] }, lit.baseMap.map { it.widthDp })
        // 項目の縮尺は今の R1（この状態は 1 km）で決まる: 県道「〜10 km」は描き、県境「OFF」は描かない
        val prefMap = BaseMap(listOf(road(RoadKind.SECONDARY, -300.0 to -300.0, 300.0 to 300.0)))
        assertTrue(HudSceneBuilder.build(state(), rect, baseMap = prefMap).baseMap.isEmpty())
        assertEquals(1, HudSceneBuilder.build(state(NavSettings().withMapRange(MapItem.PREF_ROAD, MapRange.UPTO_10KM)), rect, baseMap = prefMap).baseMap.size)
        assertEquals(listOf(Ink.COASTLINE), HudSceneBuilder.build(state(NavSettings(mapItems = mapOf(MapItem.COASTLINE to MapRange.ALL, MapItem.PREF_BOUNDARY to MapRange.OFF))), rect, baseMap = map).baseMap.map { it.ink })
    }
}
