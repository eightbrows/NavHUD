package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.geo.EN
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.geo.Screen
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.view.HudInsets
import io.github.eightbrows.navhud.core.view.HudRect
import io.github.eightbrows.navhud.core.view.HudViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PanTest {

    private val lat0 = TestGeo.LAT0
    private val lon0 = TestGeo.LON0

    private fun en(lat: Double, lon: Double) = Geo.toEN(lat0, lon0, lat, lon)

    @Test
    fun inverseConversions() {
        val p = EN(1_234.5, -987.6)
        val (lat, lon) = Geo.fromEN(lat0, lon0, p)
        val back = Geo.toEN(lat0, lon0, lat, lon)
        assertEquals(p.e, back.e, 0.01)
        assertEquals(p.n, back.n, 0.01)
        for (up in listOf(0.0, 37.0, 90.0, 250.0)) {
            val s = Geo.toScreen(p, up)
            val q = Geo.fromScreen(Screen(s.right, s.fwd), up)
            assertEquals(p.e, q.e, 1e-9)
            assertEquals(p.n, q.n, 1e-9)
        }
    }

    @Test
    fun dragMovesTheMapWithTheFinger() {
        // 1 m = 0.1 px。North Up で指を右へ 100 px → 地図が右へ動く = 中心は西へ 1000 m
        val north = PanView(lat0, lon0, upDeg = 0.0)
        val right = Pan.drag(north, 100f, 0f, 0.1)
        assertEquals(-1_000.0, en(right.lat, right.lon).e, 0.5)
        assertEquals(0.0, en(right.lat, right.lon).n, 0.5)
        // 指を下へ 100 px → 中心は北（画面の上）へ
        val down = Pan.drag(north, 0f, 100f, 0.1)
        assertEquals(1_000.0, en(down.lat, down.lon).n, 0.5)
        // ARC で東を向いて固定（上 = 090）: 指を下へ → 中心は東へ。向きは変わらない
        val east = Pan.drag(PanView(lat0, lon0, upDeg = 90.0), 0f, 100f, 0.1)
        assertEquals(1_000.0, en(east.lat, east.lon).e, 0.5)
        assertEquals(0.0, en(east.lat, east.lon).n, 0.5)
        assertEquals(90.0, east.upDeg, 0.0)
    }

    @Test
    fun startFromTheFrameCenter() {
        val p = Pan.start(lat0, lon0, EN(0.0, 500.0), upDeg = 30.0)
        assertEquals(500.0, en(p.lat, p.lon).n, 0.5)
        assertEquals(30.0, p.upDeg, 0.0)
    }

    /** エミュレータと同じ寸法の画面。 */
    private val viewport = HudViewport(HudRect(0f, 0f, 720f, 690f), reserved = HudInsets(right = 102f, bottom = 81.6f))

    private fun engine(): NavEngine {
        val e = NavEngine(
            // AUTO の上限は 20km の段（PAN の前後で AUTO が動くことを見るため）
            NavSettings(displayMode = DisplayMode.NORTH_UP, initialRangeKm = 2.0, autoMaxRangeKm = 20.0),
            sourceKind = SourceKind.LIVE,
        )
        e.setViewport(viewport)
        // 北へ 1.5km の WP
        e.setWaypoints(listOf(Waypoint("A", TestGeo.lat(1_500.0), lon0)))
        e.onFix(Fix(timeMs = 0, lat = lat0, lon = lon0), 0)
        return e
    }

    @Test
    fun engineStartsPanAtTheViewCenterAndPausesAuto() {
        val e = engine()
        val before = e.state.rangeM
        assertNull(e.state.pan)
        // North Up は自機が表示枠の中央なので、PAN の始点は自機。指を左へ動かすと中心は東へ
        val s = e.panBy(-50f, 0f)
        assertNotNull(s.pan)
        val pan = s.pan!!
        assertEquals(0.0, pan.upDeg, 0.0)
        assertEquals(50.0 / viewport.pxPerM(before, s.settings), en(pan.lat, pan.lon).e, 0.5)
        // PAN 中は AUTO を止める: WP が遠くなっても縮尺は変わらない
        e.setWaypoints(listOf(Waypoint("A", TestGeo.lat(30_000.0), lon0)))
        e.onFix(Fix(timeMs = 1_000, lat = lat0, lon = lon0), 1_000)
        assertEquals(before, e.state.rangeM, 0.0)
        assertEquals(true, e.state.rangeAuto)
        // 現在地に戻ると AUTO が動き出す（広げる方向はすぐ）
        e.endPan()
        assertNull(e.state.pan)
        e.onTick(2_000)
        assertEquals(20_000.0, e.state.rangeM, 0.0)
    }

    @Test
    fun panToWaypointAndModeChange() {
        val e = engine()
        e.panTo(34.0, 133.5)
        assertEquals(34.0, e.state.pan!!.lat, 0.0)
        assertEquals(133.5, e.state.pan!!.lon, 0.0)
        // 表示モードを変えたら PAN をやめる
        e.updateSettings(e.settings.copy(displayMode = DisplayMode.ARC))
        assertNull(e.state.pan)
    }

    @Test
    fun arcPanKeepsTheHeadingAtStart() {
        val e = NavEngine(NavSettings(displayMode = DisplayMode.ARC), sourceKind = SourceKind.LIVE)
        e.setViewport(viewport)
        // 東へ 20 m/s（方位 090、精度よし）
        e.onFix(Fix(timeMs = 0, lat = lat0, lon = lon0, speedMps = 20f, bearingDeg = 90f), 0)
        e.panBy(0f, 0f)
        assertEquals(90.0, e.state.pan!!.upDeg, 1e-6)
        // その後に向きが変わっても、PAN の向きはそのまま
        e.onFix(Fix(timeMs = 1_000, lat = lat0, lon = lon0 + 0.0002, speedMps = 20f, bearingDeg = 180f), 1_000)
        e.panBy(10f, 0f)
        assertEquals(90.0, e.state.pan!!.upDeg, 1e-6)
    }

    @Test
    fun noPanWithoutViewportOrFix() {
        val e = NavEngine(NavSettings(), sourceKind = SourceKind.LIVE)
        assertNull(e.panBy(10f, 10f).pan)
        e.setViewport(viewport)
        assertNull(e.panBy(10f, 10f).pan)
    }

    @Test
    fun zoomDuringPanKeepsAutoAndAutoDecidesOnReturn() {
        val e = engine()
        assertEquals(2_000.0, e.state.rangeM, 0.0)
        e.panBy(-20f, 0f)
        // PAN 中の − は AUTO を OFF にしない（縮尺は PAN の間だけ変わる）
        e.zoomOut()
        e.zoomOut()
        assertEquals(10_000.0, e.state.rangeM, 0.0)
        assertEquals(true, e.state.rangeAuto)
        // 現在地に戻ると、狭める方向でも待たずに AUTO が決め直す（北 1.5km → 2km）
        e.endPan()
        assertEquals(2_000.0, e.state.rangeM, 0.0)
        // PAN の外の ＋ は従来どおり AUTO を OFF にする
        e.zoomIn()
        assertEquals(false, e.state.rangeAuto)
        // AUTO が OFF なら、PAN 中に変えた縮尺のまま戻る
        e.panBy(-20f, 0f)
        e.zoomOut()
        e.endPan()
        assertEquals(2_000.0, e.state.rangeM, 0.0)
        assertEquals(false, e.state.rangeAuto)
    }
}
