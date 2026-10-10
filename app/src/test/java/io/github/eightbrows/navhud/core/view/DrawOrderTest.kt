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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 描く順番（§6.1）: 次の WP の情報（自機からの線・印・文字、画面外のときの文字）を、方位の三角・方位目盛りの文字・距離環の数字より
 * 上に描くための分け方（HudScene の nextWpSegments / baseSegments / nextWpMarks / otherWpMarks）。
 */
class DrawOrderTest {

    private val rect = HudRect(0f, 0f, 720f, 900f)
    private val m = HudMetrics()
    private val reserved = HudInsets(top = 211f, bottom = 120f)

    private fun wp(name: String, northM: Double, eastM: Double = 0.0, enabled: Boolean = true, reached: Boolean = false) =
        Waypoint(name, TestGeo.lat(northM), TestGeo.lon(eastM), enabled = enabled, reached = reached)

    private fun scene(wps: List<Waypoint>, next: Int?, noFix: Boolean = false, mode: DisplayMode = DisplayMode.ARC) =
        HudSceneBuilder.build(
            NavState(
                nowMs = 0,
                fix = Fix(timeMs = 0, lat = TestGeo.LAT0, lon = TestGeo.LON0),
                noFix = noFix,
                heading = Heading(0f, HeadingSrc.GPS),
                waypoints = wps,
                nextWpIndex = next,
                settings = NavSettings(displayMode = mode, ownshipPosition = OwnshipPosition.CENTER),
                rangeM = 2_000.0,
            ),
            rect, m, reserved,
        )

    @Test
    fun nextWaypointItemsAreSeparatedFromTheRest() {
        // 到達済み・次（前方 400m）・無効・その先 の4つ
        val wps = listOf(wp("済", -300.0, reached = true), wp("次", 400.0), wp("無効", 600.0, 200.0, enabled = false), wp("先", 900.0, -200.0))
        val s = scene(wps, next = 1)
        // 次の WP への線: 自機からの長い破線の1本だけ。ほかの線（方位目盛り・方位線・ラバーライン・WP を結ぶ線）には入らない
        assertEquals(1, s.nextWpSegments.size)
        assertTrue(s.nextWpSegments.single().longDash)
        assertEquals(Ink.ACTIVE, s.nextWpSegments.single().ink)
        assertEquals(s.ownShip!!.at, s.nextWpSegments.single().a)
        assertTrue(s.baseSegments.none { it.longDash })
        assertEquals(s.segments.size, s.baseSegments.size + 1)
        // 順番は変えない（分けるだけ）
        assertEquals(s.segments.filter { !it.longDash }, s.baseSegments)
        // 次の WP の印と文字: 1つだけ。3行（距離・名前・方位）
        assertEquals(listOf("次"), s.nextWpMarks.map { it.name })
        assertEquals(3, s.nextWpMarks.single().lines.size)
        assertEquals(Ink.ACTIVE, s.nextWpMarks.single().ink)
        // ほかの WP（到達済み・無効・その先）は今までの層のまま
        assertEquals(listOf("済", "無効", "先"), s.otherWpMarks.map { it.name })
        assertEquals(s.wpMarks.filter { !it.next }, s.otherWpMarks)
        assertEquals(s.wpMarks.size, s.nextWpMarks.size + s.otherWpMarks.size)
    }

    @Test
    fun noFixKeepsTheSameSeparation() {
        // NO FIX 中は次の WP の色がグレーになるが、分け方は変わらない（色では見分けない）
        val wps = listOf(wp("次", 400.0), wp("先", 900.0, -200.0))
        val s = scene(wps, next = 0, noFix = true)
        assertEquals(1, s.nextWpSegments.size)
        assertEquals(Ink.STALE, s.nextWpSegments.single().ink)
        assertEquals(listOf("次"), s.nextWpMarks.map { it.name })
        assertEquals(Ink.STALE, s.nextWpMarks.single().ink)
        assertEquals(listOf("先"), s.otherWpMarks.map { it.name })
    }

    @Test
    fun withoutANextWaypointNothingMovesUp() {
        // 次の WP がない（全部到達）: 上に描くものはない
        val s = scene(listOf(wp("済", 300.0, reached = true)), next = null)
        assertTrue(s.nextWpSegments.isEmpty())
        assertTrue(s.nextWpMarks.isEmpty())
        assertTrue(s.arrows.isEmpty())
        assertEquals(s.segments, s.baseSegments)
        assertEquals(s.wpMarks, s.otherWpMarks)
    }

    @Test
    fun offScreenNextWaypointUsesTheArrowText() {
        // 画面外の次の WP（前方 5km）: 印は画面の外で描かず、文字は画面外の文字（arrows）。これも三角より上に描く層
        val s = scene(listOf(wp("遠い", 5_000.0)), next = 0)
        assertEquals(1, s.arrows.size)
        assertTrue(s.nextWpMarks.all { it.nameAt == null })
        assertEquals(1, s.nextWpSegments.size)
        // 自機が中央のとき、ARC の上部の方位の三角は次の WP への線の上にある（重なる場面）: 三角の先端は自機の真上
        val tip = s.pointers.single().tip
        assertEquals(s.ownShip!!.at.x, tip.x, 1e-3f)
        assertFalse(s.pointers.isEmpty())
    }
}
