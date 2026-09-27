package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.NavState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileBuilderTest {

    private val lat0 = 33.5
    private val lon0 = 133.0
    private val mPerDegLat = 111_195.0

    /** 自機から北へ northM の WP。 */
    private fun wp(name: String, northM: Double, ele: Double?, enabled: Boolean = true, reached: Boolean = false) =
        Waypoint(name, lat0 + northM / mPerDegLat, lon0, ele, enabled = enabled, reached = reached)

    private fun state(wps: List<Waypoint>, next: Int? = 0, alt: Double? = 1_000.0, count: Int = 4, noFix: Boolean = false) = NavState(
        nowMs = 0,
        fix = Fix(timeMs = 0, lat = lat0, lon = lon0),
        noFix = noFix,
        altM = alt,
        waypoints = wps,
        nextWpIndex = next,
        settings = NavSettings(hudWpCount = count),
    )

    // 描画領域 400×100、余白 左 40・右 14・上 10・下 8 → グラフは x 40..386（幅 346）、y 10..92（高さ 82）
    private val rect = HudRect(0f, 0f, 400f, 100f)

    private fun assertP(expected: P, actual: P) {
        assertEquals("x", expected.x, actual.x, 0.3f)
        assertEquals("y", expected.y, actual.y, 0.3f)
    }

    @Test
    fun distanceAndElevationToScreen() {
        // 現在地 1000 m → A（1km 先、1100 m、次）→ B（2km、標高なし）→ C（3km、1050 m）→ D（4km、標高なし）
        val wps = listOf(wp("A", 1_000.0, 1_100.0), wp("B", 2_000.0, null), wp("C", 3_000.0, 1_050.0), wp("D", 4_000.0, null))
        val p = ProfileBuilder.build(state(wps), rect)
        // 縦軸 1000..1100、横軸 0..4000 m
        assertEquals(3, p.points.size) // 標高のない B・D は点を描かない
        assertP(P(40f, 92f), p.points[0].at)
        assertEquals(Ink.OWNSHIP, p.points[0].ink)
        assertP(P(126.5f, 10f), p.points[1].at)
        assertEquals(Ink.ACTIVE, p.points[1].ink) // 次の WP はマゼンタ
        assertP(P(299.5f, 51f), p.points[2].at)
        assertEquals(Ink.WP, p.points[2].ink)
        // 線: 現在地 → A（実線・マゼンタ）、A → C（B の標高がないので破線）。D は片側しかないので線なし
        assertEquals(2, p.segments.size)
        assertFalse(p.segments[0].dashed)
        assertEquals(Ink.ACTIVE, p.segments[0].ink)
        assertTrue(p.segments[1].dashed)
        assertEquals(Ink.WP, p.segments[1].ink)
        assertP(P(299.5f, 51f), p.segments[1].b)
        // 縦軸の数字
        assertEquals(listOf("1100", "1000"), p.labels.map { it.text })
    }

    @Test
    fun minimumSpanIs50m() {
        val wps = listOf(wp("A", 1_000.0, 1_000.0), wp("B", 2_000.0, 1_010.0))
        val p = ProfileBuilder.build(state(wps), rect)
        // 1000..1010 は幅 10 m → 中心 1005 から ±25 m（980..1030）
        assertEquals(listOf("1030", "980"), p.labels.map { it.text })
        assertP(P(40f, 92f - 20f / 50f * 82f), p.points[0].at)
    }

    @Test
    fun countFollowsHudWpCountAndSkipsNonTargets() {
        val wps = listOf(
            wp("R", 500.0, 900.0, reached = true),
            wp("A", 1_000.0, 1_100.0),
            wp("X", 1_500.0, 2_000.0, enabled = false),
            wp("B", 2_000.0, 1_200.0),
            wp("C", 3_000.0, 1_300.0),
        )
        val p = ProfileBuilder.build(state(wps, next = 1, count = 2), rect)
        // 現在地・A・B だけ（到達済み R・無効 X・3つ目の C は入れない）。横軸の右端は B（2km）
        assertEquals(3, p.points.size)
        assertEquals(386f, p.points.last().at.x, 0.3f)
        assertEquals(listOf("1200", "1000"), p.labels.map { it.text })
    }

    @Test
    fun unknownNextIsDashedMagenta() {
        // 次の WP の標高がなく、その先が分かる: 現在地 → B を破線（次の WP へ向かう線なのでマゼンタ）
        val wps = listOf(wp("A", 1_000.0, null), wp("B", 2_000.0, 1_100.0))
        val p = ProfileBuilder.build(state(wps), rect)
        assertEquals(1, p.segments.size)
        assertTrue(p.segments[0].dashed)
        assertEquals(Ink.ACTIVE, p.segments[0].ink)
    }

    @Test
    fun noFixMakesCurrentGray() {
        val wps = listOf(wp("A", 1_000.0, 1_100.0), wp("B", 2_000.0, 1_050.0))
        val p = ProfileBuilder.build(state(wps, noFix = true), rect)
        assertEquals(Ink.STALE, p.points[0].ink)
        assertEquals(Ink.STALE, p.segments[0].ink)
        assertEquals(Ink.ACTIVE, p.points[1].ink)
    }

    @Test
    fun nothingToDraw() {
        // 次の WP がない / 標高がどこにもない / ALT がなく WP 1つだけ分かる（線なし）
        assertTrue(ProfileBuilder.build(state(listOf(wp("A", 1_000.0, 1_100.0, reached = true)), next = null), rect).points.isEmpty())
        assertTrue(ProfileBuilder.build(state(listOf(wp("A", 1_000.0, null)), alt = null), rect).points.isEmpty())
        val one = ProfileBuilder.build(state(listOf(wp("A", 1_000.0, 1_100.0)), alt = null), rect)
        assertEquals(1, one.points.size)
        assertTrue(one.segments.isEmpty())
    }

    @Test
    fun namesOnlyWhenAsked() {
        val wps = listOf(wp("A", 1_000.0, 1_100.0))
        assertEquals(listOf(null, null), ProfileBuilder.build(state(wps), rect).points.map { it.name })
        assertEquals(listOf(null, "A"), ProfileBuilder.build(state(wps), rect, showNames = true).points.map { it.name })
    }
}
