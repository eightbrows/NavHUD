package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.Heading
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.nav.OwnshipPosition
import io.github.eightbrows.navhud.core.nav.PanView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 位置が決まる前の WP（20261102-D01）: LIVE で位置がまだないとき（NO FIX・起動直後）も、地図の中心（前回の位置か皇居）を基準に
 * WP の印・WP どうしの線・名前を描く。自機から次の WP への線と、次の WP の距離・方位は出さない。
 */
class NoFixWaypointsTest {

    private val rect = HudRect(0f, 0f, 720f, 900f)
    private val m = HudMetrics()
    private val lat0 = TestGeo.LAT0
    private val lon0 = TestGeo.LON0

    private val wps = listOf(
        Waypoint("A", TestGeo.lat(500.0), lon0),
        Waypoint("B", TestGeo.lat(1_000.0), TestGeo.lon(300.0)),
        Waypoint("C", TestGeo.lat(1_500.0), lon0),
    )

    /** 位置がない（fix = null・NO FIX）。地図の中心は idleCenter */
    private fun idle(mode: DisplayMode = DisplayMode.ARC, pan: PanView? = null) = NavState(
        nowMs = null,
        fix = null,
        noFix = true,
        heading = Heading.NONE,
        waypoints = wps,
        nextWpIndex = 0,
        settings = NavSettings(displayMode = mode, ownshipPosition = OwnshipPosition.STANDARD),
        rangeM = 2_000.0,
        idleCenter = lat0 to lon0,
        pan = pan,
    )

    /** 同じ所に位置がある（比べるため） */
    private fun withFix() = idle().copy(nowMs = 0, fix = Fix(timeMs = 0, lat = lat0, lon = lon0), noFix = false, heading = Heading(0f, HeadingSrc.GPS))

    @Test
    fun waypointsAreDrawnAroundTheIdleCenter() {
        val scene = HudSceneBuilder.build(idle(), rect, m)
        // 印は 3 つとも、位置があるときと同じ所（地図の中心 = 前回の位置か皇居を、自機の場所に置く）
        val withFix = HudSceneBuilder.build(withFix(), rect, m)
        assertEquals(listOf("A", "B", "C"), scene.wpMarks.map { it.name })
        for ((a, b) in scene.wpMarks.zip(withFix.wpMarks)) {
            assertEquals(b.at.x, a.at.x, 1e-3f)
            assertEquals(b.at.y, a.at.y, 1e-3f)
        }
        // WP どうしの線（A-B・B-C）はある。次の WP（A）は、NO FIX と同じくグレー
        val wpLines = scene.segments.filter { s -> scene.wpMarks.any { it.at == s.a } && scene.wpMarks.any { it.at == s.b } }
        assertEquals(2, wpLines.size)
        assertEquals(Ink.STALE, scene.wpMarks.first { it.next }.ink)
    }

    @Test
    fun noLineFromOwnShipAndNoDistanceOrBearingWithoutAPosition() {
        val scene = HudSceneBuilder.build(idle(), rect, m)
        // 自機から次の WP への線（マゼンタの長い破線）はない
        assertTrue(scene.nextWpSegments.isEmpty())
        // 次の WP の文字は名前だけ（距離・方位は出さない）
        assertEquals(listOf("A"), scene.wpMarks.first { it.next }.lines)
        // 位置があるときは 3 行（距離・名前・方位）と、自機からの線
        val withFix = HudSceneBuilder.build(withFix(), rect, m)
        assertEquals(3, withFix.wpMarks.first { it.next }.lines.size)
        assertEquals(1, withFix.nextWpSegments.size)
    }

    @Test
    fun northUpAndPanAlsoShowWaypoints() {
        assertEquals(3, HudSceneBuilder.build(idle(DisplayMode.NORTH_UP), rect, m).wpMarks.size)
        // 位置がないときの PAN（地図の中心から動かした）でも出る
        val pan = PanView(TestGeo.lat(1_000.0), lon0, 0.0)
        assertEquals(3, HudSceneBuilder.build(idle(pan = pan), rect, m).wpMarks.size)
    }

    @Test
    fun noWaypointsWithoutAMapCenter() {
        // 地図の中心もない（idleCenter がまだない）ときは、今までどおり描かない
        assertTrue(HudSceneBuilder.build(idle().copy(idleCenter = null), rect, m).wpMarks.isEmpty())
    }
}
