package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.geo.EN
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.io.BaseMap
import io.github.eightbrows.navhud.core.io.MapLayer
import io.github.eightbrows.navhud.core.io.MapLine
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.Heading
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.nav.PanView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 背景地図（§8）: 画面に入る線を選ぶ、地図から画面の位置に直す、点の数を減らす、HUD に入れる。 */
class BaseMapBuilderTest {

    private val rect = HudRect(0f, 0f, 720f, 1200f)

    /** 自機（基準の地点）を画面の (360, 600) に置く。pxPerM = 0.1 は 1 px = 10 m */
    private fun proj(upDeg: Double = 0.0, pxPerM: Double = 0.1) = HudProjection(P(360f, 600f), pxPerM, upDeg)

    /** 基準の地点から 東 e・北 n [m] の点を並べた線 */
    private fun line(layer: MapLayer, vararg en: Pair<Double, Double>) =
        MapLine(layer, DoubleArray(en.size) { TestGeo.lat(en[it].second) }, DoubleArray(en.size) { TestGeo.lon(en[it].first) })

    private fun build(map: BaseMap, p: HudProjection = proj(), minStepPx: Float = 1f) =
        BaseMapBuilder.build(map, TestGeo.LAT0, TestGeo.LON0, p, rect, minStepPx, marginPx = 0f)

    @Test
    fun visibleBoxCoversTheWholeFrame() {
        // 北が上: 枠は 720×1200 px = 東西 7.2 km・南北 12 km。自機は枠の中央
        val b = BaseMapBuilder.visibleBox(TestGeo.LAT0, TestGeo.LON0, proj(), rect)
        assertEquals(TestGeo.lat(-6000.0), b.minLat, 1e-6)
        assertEquals(TestGeo.lat(6000.0), b.maxLat, 1e-6)
        assertEquals(TestGeo.lon(-3600.0), b.minLon, 3e-5)
        assertEquals(TestGeo.lon(3600.0), b.maxLon, 3e-5)
        // 画面が回っている（ARC で機首方位 45°）ときは、四隅を含む範囲なので北が上より広くなる
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
        val out = build(BaseMap(listOf(inside, crossing, farEast, farNorth)))
        assertEquals(2, out.size)
        // 地図から画面への直し方は、軌跡・WP と同じ（基準の地点 = 自機の画面の位置）
        val p = proj()
        assertEquals(p.toScreen(Geo.toEN(TestGeo.LAT0, TestGeo.LON0, inside.lats[0], inside.lons[0])), out[0].points[0])
        assertEquals(260f, out[0].points[0].x, 0.01f)   // 西 1000 m = 左 100 px
        assertEquals(600f, out[0].points[0].y, 0.01f)
        assertEquals(460f, out[0].points[1].x, 0.01f)
        assertEquals(550f, out[0].points[1].y, 0.01f)   // 北 500 m = 上 50 px
    }

    @Test
    fun coastlineIsSolidAndPrefectureBorderIsDotted() {
        val out = build(BaseMap(listOf(
            line(MapLayer.COASTLINE, 0.0 to 0.0, 100.0 to 100.0),
            line(MapLayer.PREF_BOUNDARY, 0.0 to 0.0, -100.0 to 100.0),
            line(MapLayer.RIVER, 0.0 to 0.0, 100.0 to -100.0),   // 今はまだ描かない種類
        )))
        assertEquals(listOf(Ink.COASTLINE, Ink.PREF_BOUNDARY), out.map { it.ink })
        assertEquals(listOf(false, true), out.map { it.dashed })
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
        val wide = build(BaseMap(listOf(dense)), proj(pxPerM = 1.0 / 70)).single()
        val detail = build(BaseMap(listOf(dense)), proj(pxPerM = 2.0)).single()
        assertTrue("広域 ${wide.points.size} 点", wide.points.size in 50..70)
        // 詳細では画面の外（左右 180 m より外）の点も、線の範囲が画面に入れば描く（線は分けずに渡す）
        assertEquals(401, detail.points.size)
        // 最初と最後の点は残る
        val pw = proj(pxPerM = 1.0 / 70)
        assertEquals(pw.toScreen(EN(-2000.0, 0.0)).x, wide.points.first().x, 0.01f)
        assertEquals(pw.toScreen(EN(2000.0, 0.0)).x, wide.points.last().x, 0.01f)
    }

    private fun state(settings: NavSettings = NavSettings(), pan: PanView? = null) = NavState(
        nowMs = 0,
        fix = Fix(timeMs = 0, lat = TestGeo.LAT0, lon = TestGeo.LON0),
        heading = Heading(30f, HeadingSrc.GPS),
        settings = settings,
        rangeM = 2_000.0,
        pan = pan,
    )

    private val map = BaseMap(listOf(line(MapLayer.COASTLINE, -500.0 to 200.0, 500.0 to 300.0), line(MapLayer.PREF_BOUNDARY, 0.0 to -300.0, 0.0 to 300.0)))

    @Test
    fun hudSceneHasBaseMapOnlyWhenSwitchedOn() {
        val on = HudSceneBuilder.build(state(), rect, baseMap = map)
        assertEquals(2, on.baseMap.size)
        // 設定で OFF・背景地図がまだ読めていない（null）なら描かない
        assertTrue(HudSceneBuilder.build(state(NavSettings(baseMap = false)), rect, baseMap = map).baseMap.isEmpty())
        assertTrue(HudSceneBuilder.build(state(), rect, baseMap = null).baseMap.isEmpty())
        // North Up と PAN でも描く。PAN は PAN の中心が基準
        assertEquals(2, HudSceneBuilder.build(state(NavSettings(displayMode = DisplayMode.NORTH_UP)), rect, baseMap = map).baseMap.size)
        val pan = HudSceneBuilder.build(state(pan = PanView(TestGeo.lat(200.0), TestGeo.LON0, 0.0)), rect, baseMap = map)
        assertEquals(2, pan.baseMap.size)
        assertFalse(pan.baseMap == on.baseMap)
        // 背景地図は軌跡とは別のリスト（Canvas がいちばん下に描く）
        assertTrue(on.trails.none { it.ink == Ink.COASTLINE || it.ink == Ink.PREF_BOUNDARY })
    }
}
