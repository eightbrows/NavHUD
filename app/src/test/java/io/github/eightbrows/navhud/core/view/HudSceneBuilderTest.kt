package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.Heading
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.NavState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class HudSceneBuilderTest {

    private val rect = HudRect(0f, 0f, 720f, 900f)
    private val m = HudMetrics()
    private val mPerDegLat = 111_195.0
    private val lat0 = 33.5
    private val lon0 = 133.0

    /** 自機から北へ northM、東へ eastM の地点。 */
    private fun wp(name: String, northM: Double, eastM: Double = 0.0, enabled: Boolean = true, reached: Boolean = false) =
        Waypoint(
            name,
            lat0 + northM / mPerDegLat,
            lon0 + eastM / (mPerDegLat * Math.cos(Math.toRadians(lat0))),
            enabled = enabled,
            reached = reached,
        )

    private fun state(
        headingDeg: Float? = 0f,
        wps: List<Waypoint> = emptyList(),
        next: Int? = null,
        mode: DisplayMode = DisplayMode.ARC,
        lost: Boolean = false,
    ) = NavState(
        nowMs = 0,
        fix = Fix(timeMs = 0, lat = lat0, lon = lon0),
        positionLost = lost,
        heading = if (headingDeg == null) Heading.NONE else Heading(headingDeg, HeadingSrc.GPS),
        waypoints = wps,
        nextWpIndex = next,
        settings = NavSettings(displayMode = mode),
    )

    private fun build(s: NavState) = HudSceneBuilder.build(s, rect, m)

    private fun assertP(expected: P, actual: P, tol: Float = 1e-3f) {
        assertEquals("x", expected.x, actual.x, tol)
        assertEquals("y", expected.y, actual.y, tol)
    }

    @Test
    fun arcOwnShipAtBottomCenterAndRingsTouchEdges() {
        val scene = build(state())
        assertEquals(P(360f, 900f - m.arcOriginFromBottom), scene.ownShip.at)
        assertEquals(0f, scene.ownShip.angleDeg)
        // 2km の距離環の半径 = 画面幅の半分
        assertTrue(scene.arcs.any { kotlin.math.abs(it.radius - 360f) < 1e-3 })
        // 前方 180° の弧
        assertTrue(scene.arcs.all { it.startDeg == -90f && it.sweepDeg == 180f })
    }

    @Test
    fun arcCompassLabelsSitOnFrameEdge() {
        // 機首 000: N は上端の中央、E は右端、W は左端。S（後方）は出ない
        val labels = build(state(headingDeg = 0f)).labels.associateBy { it.text }
        val gap = m.tickMajor + m.labelGap
        assertP(P(360f, gap), labels.getValue("N").at)
        assertP(P(720f - gap, 810f), labels.getValue("E").at)
        assertP(P(gap, 810f), labels.getValue("W").at)
        assertFalse(labels.containsKey("S"))
    }

    @Test
    fun arcCompassLabelsRotateWithHeading() {
        // 機首 090: E が上端の中央、N は左端、S は右端
        val labels = build(state(headingDeg = 90f)).labels.associateBy { it.text }
        val gap = m.tickMajor + m.labelGap
        assertEquals(360f, labels.getValue("E").at.x, 1e-3f)
        assertEquals(gap, labels.getValue("E").at.y, 1e-3f)
        assertEquals(gap, labels.getValue("N").at.x, 1e-3f)
        assertEquals(720f - gap, labels.getValue("S").at.x, 1e-3f)
        assertFalse(labels.containsKey("W"))
    }

    @Test
    fun arcTicksAreOnTheFrameNotOnARing() {
        // 目盛りの外側の端はすべて表示枠の縁にある（同心円に沿わない）
        val o = P(360f, 810f)
        val ticks = build(state(headingDeg = 30f)).segments.filter { it.ink == Ink.SCALE }
        assertEquals(19, ticks.size) // -90°..+90° を 10° ごと
        for (t in ticks) {
            val e = 1e-2f
            val onEdge = abs(t.a.x) < e || abs(t.a.x - 720f) < e || abs(t.a.y) < e || abs(t.a.y - 900f) < e
            assertTrue("tick $t", onEdge)
        }
        val dists = ticks.map { hypot(it.a.x - o.x, it.a.y - o.y) }.toSet()
        assertTrue(dists.size > 5)
    }

    @Test
    fun bearingLinesEvery30Degrees() {
        val lines = build(state(headingDeg = 0f)).segments.filter { it.ink == Ink.BEARING_LINE }
        assertEquals(7, lines.size) // -90, -60, … , +90
        assertTrue(lines.all { it.a == P(360f, 810f) })
    }

    @Test
    fun northWaypointBecomesLeftEdgeArrowWhenHeadingEast() {
        // 機首 090 で北 5km の WP → 左端の矢印
        val scene = build(state(headingDeg = 90f, wps = listOf(wp("WP1", 5_000.0)), next = 0))
        assertTrue(scene.wpMarks.isEmpty())
        val arrow = scene.arrows.single()
        assertEquals(m.edgeInset, arrow.at.x, 1e-3f)
        assertEquals(810f, arrow.at.y, 0.5f)
        assertEquals(-90f, arrow.angleDeg, 0.1f)
        assertEquals(Ink.ACTIVE, arrow.ink)
        assertTrue(arrow.text, arrow.text.startsWith("WP1 5.00 km"))
        // 文字は矢印より内側
        assertTrue(arrow.textAt.x > arrow.at.x)
    }

    @Test
    fun nearbyWaypointIsDrawnOnScreen() {
        // 機首 000 で北 1km → 自機の真上、1km 環の上
        val scene = build(state(headingDeg = 0f, wps = listOf(wp("WP1", 1_000.0)), next = 0))
        val mark = scene.wpMarks.single()
        assertEquals(360f, mark.at.x, 0.5f)
        assertEquals(810f - 180f, mark.at.y, 0.5f)
        assertEquals(Ink.ACTIVE, mark.ink)
        assertTrue(scene.arrows.isEmpty())
        // 自機から次の WP への線（マゼンタ）
        val active = scene.segments.single { it.ink == Ink.ACTIVE }
        assertEquals(P(360f, 810f), active.a)
        assertEquals(mark.at, active.b)
    }

    @Test
    fun routeStyles() {
        val wps = listOf(
            wp("A", 300.0, reached = true),
            wp("B", 600.0),
            wp("C", 900.0, enabled = false),
            wp("D", 1_200.0),
        )
        val scene = build(state(wps = wps, next = 1))
        val route = scene.segments.filter { it.ink in setOf(Ink.WP, Ink.WP_DISABLED, Ink.WP_REACHED) }
        assertEquals(3, route.size)
        assertEquals(Ink.WP, route[0].ink) // A → B（B は未到達）
        assertEquals(Ink.WP_DISABLED, route[1].ink)
        assertTrue(route[1].dashed)
        assertEquals(Ink.WP_DISABLED, route[2].ink)
        val inks = scene.wpMarks.associate { it.name to it.ink }
        assertEquals(Ink.WP_REACHED, inks["A"])
        assertEquals(Ink.ACTIVE, inks["B"])
        assertEquals(Ink.WP_DISABLED, inks["C"])
        assertEquals(Ink.WP, inks["D"])
        assertTrue(scene.wpMarks.single { it.name == "C" }.dashed)
    }

    @Test
    fun offScreenArrowsOnlyForEnabledUnreached() {
        val wps = listOf(
            wp("A", 20_000.0, reached = true),
            wp("B", 20_000.0, enabled = false),
            wp("C", -20_000.0),
        )
        val scene = build(state(headingDeg = 0f, wps = wps, next = 2))
        val arrow = scene.arrows.single()
        assertTrue(arrow.text.startsWith("C "))
        // 真後ろ → 下端
        assertEquals(900f - m.edgeInset, arrow.at.y, 1e-3f)
        assertEquals(180f, kotlin.math.abs(arrow.angleDeg), 0.1f)
    }

    @Test
    fun northUpCompassCardOutsideOuterRing() {
        val scene = build(state(headingDeg = 45f, mode = DisplayMode.NORTH_UP))
        val o = P(360f, 450f)
        assertEquals(o, scene.ownShip.at)
        assertEquals(45f, scene.ownShip.angleDeg)
        val outer = 360f - m.northUpMargin
        // 全周の距離環（1km, 2km）
        assertEquals(2, scene.arcs.size)
        assertTrue(scene.arcs.all { it.sweepDeg == 360f })
        assertEquals(outer, scene.arcs.maxOf { it.radius }, 1e-3f)
        // 目盛りは 36 本、すべて最外周の外側
        val ticks = scene.segments.filter { it.ink == Ink.SCALE }
        assertEquals(36, ticks.size)
        for (t in ticks) assertTrue(hypot(t.a.x - o.x, t.a.y - o.y) >= outer - 1e-3f)
        // N は真上
        val n = scene.labels.single { it.text == "N" }
        assertEquals(360f, n.at.x, 1e-3f)
        assertTrue(n.at.y < 450f - outer)
    }

    @Test
    fun noHeadingUsesNorthUpInArcAndRoundOwnShip() {
        val scene = build(state(headingDeg = null))
        assertNull(scene.ownShip.angleDeg)
        assertEquals(360f, scene.labels.single { it.text == "N" }.at.x, 1e-3f)
        // ラバーラインは引かない
        assertTrue(scene.segments.none { it.ink == Ink.OWNSHIP })
    }

    @Test
    fun lostMakesPositionDependentPartsGray() {
        val scene = build(state(wps = listOf(wp("WP1", 1_000.0)), next = 0, lost = true))
        assertEquals(Ink.STALE, scene.ownShip.ink)
        assertEquals(Ink.STALE, scene.wpMarks.single().ink)
        assertTrue(scene.segments.none { it.ink == Ink.ACTIVE || it.ink == Ink.OWNSHIP })
        // 目盛りはそのまま
        assertTrue(scene.segments.any { it.ink == Ink.SCALE })
    }

    @Test
    fun noFixNoWaypoints() {
        val s = state(wps = listOf(wp("WP1", 1_000.0)), next = 0).copy(fix = null)
        val scene = build(s)
        assertTrue(scene.wpMarks.isEmpty())
        assertTrue(scene.arrows.isEmpty())
    }
}
