package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.geo.EN
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.view.HudInsets
import io.github.eightbrows.navhud.core.view.HudMetrics
import io.github.eightbrows.navhud.core.view.HudRect
import io.github.eightbrows.navhud.core.view.HudSceneBuilder
import io.github.eightbrows.navhud.core.view.HudViewport
import io.github.eightbrows.navhud.core.view.P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 地図のダブルタップ（20261023-D02）: タップした所を基準に 1段詳細にし、PAN にする。 */
class DoubleTapZoomTest {

    private val lat0 = TestGeo.LAT0
    private val lon0 = TestGeo.LON0

    /** エミュレータと同じ寸法の画面（PanTest と同じ） */
    private val rect = HudRect(0f, 0f, 720f, 690f)
    private val reserved = HudInsets(right = 102f, bottom = 81.6f)
    private val viewport = HudViewport(rect, reserved = reserved)

    private fun engine(mode: DisplayMode): NavEngine {
        val e = NavEngine(NavSettings(displayMode = mode, initialRangeKm = 2.0, autoMaxRangeKm = 20.0), sourceKind = SourceKind.LIVE)
        e.setViewport(viewport)
        e.setWaypoints(listOf(Waypoint("A", TestGeo.lat(1_500.0), lon0)))
        // 東へ 20 m/s（ARC は方位 090 が上）
        e.onFix(Fix(timeMs = 0, lat = lat0, lon = lon0, speedMps = 20f, bearingDeg = 90f), 0)
        return e
    }

    /** 地点 (lat, lon) が今の表示で画面のどこに描かれるか（HudSceneBuilder と同じ投影） */
    private fun screenOf(e: NavEngine, lat: Double, lon: Double): P {
        val s = e.state
        val avoid = HudSceneBuilder.avoidFrame(rect, reserved)
        val proj = HudSceneBuilder.projection(s.settings, rect, avoid, s.rangeM, s.heading.deg?.toDouble(), HudMetrics(), s.pan)
        val (refLat, refLon) = s.pan?.let { it.lat to it.lon } ?: (lat0 to lon0)
        return proj.toScreen(Geo.toEN(refLat, refLon, lat, lon))
    }

    /** 地点を画面の tap に置いてダブルタップし、そのあとも同じ所に描かれることを確かめる */
    private fun tapKeeps(e: NavEngine, target: EN) {
        val (lat, lon) = Geo.fromEN(lat0, lon0, target)
        val tap = screenOf(e, lat, lon)
        val before = e.state.rangeM
        e.zoomInAt(tap.x, tap.y)
        assertTrue(e.state.rangeM < before)
        assertNotNull(e.state.pan)
        val after = screenOf(e, lat, lon)
        assertEquals(tap.x, after.x, 0.5f)
        assertEquals(tap.y, after.y, 0.5f)
    }

    @Test
    fun tappedPointStaysUnderTheFingerNorthUp() {
        val e = engine(DisplayMode.NORTH_UP)
        // PAN でないとき: 北東 300 m の地点
        tapKeeps(e, EN(300.0, 300.0))
        assertEquals(1_000.0, e.state.rangeM, 0.0)
        assertEquals(0.0, e.state.pan!!.upDeg, 0.0)
        // PAN のとき: 南西の別の地点でもう 1 回
        tapKeeps(e, EN(-100.0, -150.0))
        assertEquals(500.0, e.state.rangeM, 0.0)
    }

    @Test
    fun tappedPointStaysUnderTheFingerArc() {
        val e = engine(DisplayMode.ARC)
        // ARC は方位 090 が上。PAN でないとき: 自機の前（東）500 m・左（北）200 m
        tapKeeps(e, EN(500.0, 200.0))
        assertEquals(90.0, e.state.pan!!.upDeg, 1e-6)
        // PAN のとき
        tapKeeps(e, EN(300.0, -100.0))
        assertEquals(90.0, e.state.pan!!.upDeg, 1e-6)
    }

    @Test
    fun autoIsHandledLikeThePlusButton() {
        // PAN でないときは ＋ と同じく AUTO を OFF にする
        val e = engine(DisplayMode.NORTH_UP)
        assertEquals(true, e.state.rangeAuto)
        e.zoomInAt(300f, 300f)
        assertEquals(false, e.state.rangeAuto)
        // PAN 中は ＋ と同じく AUTO をそのままにする
        val f = engine(DisplayMode.NORTH_UP)
        f.panBy(-10f, 0f)
        f.zoomInAt(300f, 300f)
        assertEquals(true, f.state.rangeAuto)
        assertEquals(1_000.0, f.state.rangeM, 0.0)
    }

    @Test
    fun nothingAtTheMostDetailedStep() {
        val e = engine(DisplayMode.NORTH_UP)
        repeat(30) { e.zoomIn() }
        val finest = e.state.rangeM
        e.zoomInAt(100f, 100f)
        assertNull(e.state.pan)
        assertEquals(finest, e.state.rangeM, 0.0)
    }

    @Test
    fun nothingWithoutFixOrViewport() {
        val e = NavEngine(NavSettings(initialRangeKm = 2.0), sourceKind = SourceKind.LIVE)
        e.zoomInAt(100f, 100f)
        assertNull(e.state.pan)
        assertEquals(2_000.0, e.state.rangeM, 0.0)
        e.setViewport(viewport)
        e.zoomInAt(100f, 100f)
        assertNull(e.state.pan)
        assertEquals(2_000.0, e.state.rangeM, 0.0)
    }
}
