package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.geo.EN
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.io.BaseMap
import io.github.eightbrows.navhud.core.io.MapLayer
import io.github.eightbrows.navhud.core.io.MapLevel
import io.github.eightbrows.navhud.core.io.MapLine
import io.github.eightbrows.navhud.core.io.MapPoint
import io.github.eightbrows.navhud.core.io.RoadKind
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.Heading
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
    private val all = MapLayer.entries.toSet()

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
        layers: Set<MapLayer> = all,
        p: HudProjection = proj(),
        r1M: Double = 500.0,
        minStepPx: Float = 1f,
        brightness: Map<MapLayer, Int> = emptyMap(),
        prefRoads: Boolean = false,
    ) = BaseMapBuilder.build(map, layers, TestGeo.LAT0, TestGeo.LON0, p, rect, r1M, brightness, prefRoads, minStepPx = minStepPx, marginPx = 0f)

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
        // 地図の中の順番によらず、描く順（DRAW_ORDER）に並ぶ: 面の輪郭 → 水 → 境 → 交通 → ダム
        val layers = listOf(MapLayer.FERRY, MapLayer.ROAD, MapLayer.RAILWAY, MapLayer.PREF_BOUNDARY, MapLayer.MUNI_BOUNDARY,
            MapLayer.COASTLINE, MapLayer.RIVER, MapLayer.INLAND_WATER, MapLayer.BUILT_UP_AREA, MapLayer.DAM_LINE)
        val out = build(BaseMap(layers.map { line(it, 0.0 to 0.0, 100.0 to 100.0) })).lines
        assertEquals(
            listOf(Ink.MAP_BUILT_UP, Ink.MAP_WATER, Ink.MAP_WATER, Ink.COASTLINE, Ink.MUNI_BOUNDARY, Ink.PREF_BOUNDARY, Ink.MAP_ROAD, Ink.MAP_RAIL, Ink.MAP_FERRY, Ink.MAP_DAM),
            out.map { it.ink },
        )
        // 線の種類: 県境は点線、市区町村境は細かい点線、鉄道は破線、フェリーは長い破線、ほかは実線
        assertEquals(
            listOf(LineDash.SOLID, LineDash.SOLID, LineDash.SOLID, LineDash.SOLID, LineDash.FINE_DOT, LineDash.DOT, LineDash.SOLID, LineDash.DASH, LineDash.LONG_DASH, LineDash.SOLID),
            out.map { it.dash },
        )
        // 線のレイヤどうしは、色か線の種類のどちらかで見分けられる（河川と湖沼の輪郭だけが同じ。どちらも水）
        val looks = layers.map { BaseMapBuilder.styleOf(it).let { s -> s.ink to s.dash } }
        assertEquals(layers.size - 1, looks.distinct().size)
        // 点のレイヤも、色か記号の形で見分けられる
        val marks = MapLayer.entries.filter { it.isPoint }.map { BaseMapBuilder.styleOf(it).let { s -> Triple(s.ink, s.shape, s.filled) } }
        assertEquals(marks.size, marks.distinct().size)
    }

    @Test
    fun onlySelectedLayersAreDrawn() {
        val map = BaseMap(
            listOf(MapLevel(0.0, listOf(line(MapLayer.COASTLINE, 0.0 to 0.0, 100.0 to 100.0), line(MapLayer.ROAD, 0.0 to 0.0, -100.0 to 100.0)))),
            listOf(point(MapLayer.STATION, 200.0, 200.0), point(MapLayer.CITY, -200.0, 200.0), point(MapLayer.AIRPORT, 50_000.0, 0.0)),
        )
        // 初期値（海岸線・県境・市区町村境・湖沼など・フェリー）: 道路と点は描かない
        val d = build(map, NavSettings.DEFAULT_MAP_LAYERS)
        assertEquals(listOf(Ink.COASTLINE), d.lines.map { it.ink })
        assertTrue(d.marks.isEmpty())
        // 道路と駅だけ
        val r = build(map, setOf(MapLayer.ROAD, MapLayer.STATION))
        assertEquals(listOf(Ink.MAP_ROAD), r.lines.map { it.ink })
        assertEquals(listOf(Ink.MAP_STATION), r.marks.map { it.ink })
        assertEquals(MarkShape.SQUARE, r.marks.single().shape)
        // 全部: 画面の外の点（東 50 km の空港）は描かない
        val a = build(map, all)
        assertEquals(setOf(Ink.MAP_STATION, Ink.MAP_CITY), a.marks.map { it.ink }.toSet())
        assertEquals(380f, a.marks.first { it.ink == Ink.MAP_STATION }.at.x, 0.05f)   // 東 200 m = 右 20 px
        // 何も選ばなければ何も描かない
        assertTrue(build(map, emptySet()).let { it.lines.isEmpty() && it.marks.isEmpty() })
    }

    @Test
    fun prefectureRoadsAreDrawnOnlyWhenSwitchedOn() {
        val map = BaseMap(listOf(
            road(RoadKind.MOTORWAY, -1000.0 to 0.0, 1000.0 to 0.0),
            road(RoadKind.PRIMARY, 0.0 to -1000.0, 0.0 to 1000.0),
            road(RoadKind.SECONDARY, -1000.0 to -1000.0, 1000.0 to 1000.0),
        ))
        // 初期値（県道も出す = OFF）: 高速道路と国道だけ
        assertEquals(2, build(map, setOf(MapLayer.ROAD)).lines.size)
        // ON: 県道も描く（3 本とも道路の見た目）
        val on = build(map, setOf(MapLayer.ROAD), prefRoads = true).lines
        assertEquals(3, on.size)
        assertTrue(on.all { it.ink == Ink.MAP_ROAD })
        // ほかのレイヤの種類（rtt と同じ 15 の値でも）は飛ばさない
        val river = MapLine.of(MapLayer.RIVER, doubleArrayOf(TestGeo.LAT0, TestGeo.lat(100.0)), doubleArrayOf(TestGeo.LON0, TestGeo.LON0), RoadKind.SECONDARY, unit = 10_000_000)
        assertEquals(1, build(BaseMap(listOf(river)), setOf(MapLayer.RIVER)).lines.size)
        // 道路のレイヤが OFF なら、県道も出すが ON でも描かない
        assertTrue(build(map, setOf(MapLayer.COASTLINE), prefRoads = true).lines.isEmpty())
    }

    @Test
    fun roadsAreSplitByWidthWithTheSameColor() {
        // 地図の中では種類が混ざって並んでいる
        val map = BaseMap(listOf(
            road(RoadKind.PRIMARY, -1000.0 to 0.0, 1000.0 to 0.0),
            road(RoadKind.MOTORWAY, 0.0 to -1000.0, 0.0 to 1000.0),
            road(RoadKind.SECONDARY, -1000.0 to -1000.0, 1000.0 to 1000.0),
            road(RoadKind.PRIMARY, -1000.0 to 500.0, 1000.0 to 500.0),
            road(RoadKind.MOTORWAY, 500.0 to -1000.0, 500.0 to 1000.0),
        ))
        // 太さ: 高速道路 3.0・国道 1.5・県道 0.7 [dp]。細い線から（県道 → 国道 → 高速道路）並ぶので、同じ太さが続く
        val out = build(map, setOf(MapLayer.ROAD), prefRoads = true, brightness = mapOf(MapLayer.ROAD to 4)).lines
        assertEquals(listOf(0.7f, 1.5f, 1.5f, 3f, 3f), out.map { it.widthDp })
        // 色・線の種類は同じ茶の実線。明るさはどの太さにも効く
        assertTrue(out.all { it.ink == Ink.MAP_ROAD && it.dash == LineDash.SOLID })
        assertTrue(out.all { it.brightness == Tuning.BASEMAP_BRIGHTNESS_FACTORS[3] })
        // 県道も出すが OFF なら、国道と高速道路だけ
        assertEquals(listOf(1.5f, 1.5f, 3f, 3f), build(map, setOf(MapLayer.ROAD)).lines.map { it.widthDp })
        // 種類の分からない道路（0）は国道と同じ太さ
        assertEquals(Tuning.BASEMAP_ROAD_LINE_DP, BaseMapBuilder.roadWidthDp(0))
    }

    @Test
    fun brightnessScalesEachLayerAndAirportIsAPlane() {
        val map = BaseMap(
            listOf(MapLevel(0.0, listOf(line(MapLayer.COASTLINE, 0.0 to 0.0, 100.0 to 100.0), line(MapLayer.RIVER, 0.0 to 0.0, -100.0 to 100.0)))),
            listOf(point(MapLayer.AIRPORT, 200.0, 200.0), point(MapLayer.STATION, -200.0, 200.0)),
        )
        // 初期値（全部 3）: 掛ける数は 1（D02 までと同じ明るさ）
        val d = build(map)
        assertTrue(d.lines.all { it.brightness == 1f } && d.marks.all { it.brightness == 1f })
        // レイヤごとに変えられる: 海岸線 5・空港 1。ほかは 3 のまま
        val b = build(map, brightness = mapOf(MapLayer.COASTLINE to 5, MapLayer.AIRPORT to 1))
        assertEquals(Tuning.BASEMAP_BRIGHTNESS_FACTORS[4], b.lines.first { it.ink == Ink.COASTLINE }.brightness)
        assertEquals(1f, b.lines.first { it.ink == Ink.MAP_WATER }.brightness)
        assertEquals(Tuning.BASEMAP_BRIGHTNESS_FACTORS[0], b.marks.first { it.ink == Ink.MAP_AIRPORT }.brightness)
        assertEquals(1f, b.marks.first { it.ink == Ink.MAP_STATION }.brightness)
        // 数が大きいほど明るい（3 が 1）
        val f = (1..5).map { BaseMapBuilder.brightnessFactor(it) }
        assertEquals(f.sorted(), f)
        assertEquals(1f, BaseMapBuilder.brightnessFactor(NavSettings.DEFAULT_MAP_BRIGHTNESS))
        // 空港は飛行機の記号（塗り）。大きさはほかの記号と同じ
        val plane = d.marks.first { it.ink == Ink.MAP_AIRPORT }
        assertEquals(MarkShape.PLANE, plane.shape)
        assertTrue(plane.filled)
        assertEquals(d.marks.first { it.ink == Ink.MAP_STATION }.sizeDp, plane.sizeDp)
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
        listOf(point(MapLayer.STATION, 100.0, 100.0)),
    )

    @Test
    fun hudSceneDrawsTheSelectedLayersWhenSwitchedOn() {
        // 初期値: 背景地図 ON、レイヤは海岸線・県境・市区町村境・湖沼など・フェリー（この地図にあるのは海岸線と県境）
        val on = HudSceneBuilder.build(state(), rect, baseMap = map)
        assertEquals(listOf(Ink.COASTLINE, Ink.PREF_BOUNDARY), on.baseMap.map { it.ink })
        assertTrue(on.baseMapMarks.isEmpty())
        // レイヤを足すと描く（道路と駅）
        val more = HudSceneBuilder.build(state(NavSettings(mapLayers = NavSettings.DEFAULT_MAP_LAYERS + MapLayer.ROAD + MapLayer.STATION)), rect, baseMap = map)
        assertEquals(listOf(Ink.COASTLINE, Ink.PREF_BOUNDARY, Ink.MAP_ROAD), more.baseMap.map { it.ink })
        assertEquals(1, more.baseMapMarks.size)
        // 背景地図を OFF にすると、選んだレイヤによらず全部描かない。背景地図がまだ読めていない（null）ときも描かない
        val off = HudSceneBuilder.build(state(NavSettings(baseMap = false, mapLayers = all)), rect, baseMap = map)
        assertTrue(off.baseMap.isEmpty() && off.baseMapMarks.isEmpty())
        assertTrue(HudSceneBuilder.build(state(), rect, baseMap = null).baseMap.isEmpty())
        // North Up と PAN でも描く。PAN は PAN の中心が基準
        assertEquals(2, HudSceneBuilder.build(state(NavSettings(displayMode = DisplayMode.NORTH_UP)), rect, baseMap = map).baseMap.size)
        val pan = HudSceneBuilder.build(state(pan = PanView(TestGeo.lat(200.0), TestGeo.LON0, 0.0)), rect, baseMap = map)
        assertEquals(2, pan.baseMap.size)
        assertFalse(pan.baseMap == on.baseMap)
        // 背景地図は軌跡とは別のリスト（Canvas がいちばん下に描く）
        assertTrue(on.trails.none { it.ink == Ink.COASTLINE || it.ink == Ink.PREF_BOUNDARY })
        // 設定の明るさと「県道も出す」が届く（この地図の道路は種類 0 なので、県道も出すによらず描く）
        val lit = HudSceneBuilder.build(state(NavSettings().withMapBrightness(MapLayer.PREF_BOUNDARY, 4)), rect, baseMap = map)
        assertEquals(listOf(1f, Tuning.BASEMAP_BRIGHTNESS_FACTORS[3]), lit.baseMap.map { it.brightness })
        val prefMap = BaseMap(listOf(road(RoadKind.SECONDARY, -300.0 to -300.0, 300.0 to 300.0)))
        assertTrue(HudSceneBuilder.build(state(NavSettings(mapLayers = setOf(MapLayer.ROAD))), rect, baseMap = prefMap).baseMap.isEmpty())
        assertEquals(1, HudSceneBuilder.build(state(NavSettings(mapLayers = setOf(MapLayer.ROAD), mapPrefRoads = true)), rect, baseMap = prefMap).baseMap.size)
    }
}
