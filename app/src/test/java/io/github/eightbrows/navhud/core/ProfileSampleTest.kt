package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.io.WaypointCsv
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.nav.NavEngine
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.view.HudRect
import io.github.eightbrows.navhud.core.view.Ink
import io.github.eightbrows.navhud.core.view.ProfileBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * 標高プロファイル（§6.6）を、track.csv のリプレイの場面で確かめる（REPLAY 0:01 = 07:53:43 JST、峠の入口を過ぎて次は展望台）。
 * track.csv は git 管理外なので、ファイルがなければスキップする。
 */
class ProfileSampleTest {

    companion object {
        /** 標高が 峠の入口 にしかない WP リスト（waypoints.csv。D02 のスクリーンショットで読まれていたもの） */
        private val WP_NO_ELE = WP_PROFILE.lines().joinToString("\n") { line ->
            val c = line.split(",").toMutableList()
            if (c[3] != "峠の入口" && c[3] != "name") c[2] = ""
            c.joinToString(",")
        }

        private val AT = Instant.parse("2026-08-13T22:53:43Z").toEpochMilli()
    }

    private val fixes: List<Fix>
        get() = SampleTrack.fixes()

    /** 07:53:43 までをリプレイした NavState。 */
    private fun stateAt(csv: String): NavState {
        val e = NavEngine(NavSettings(), ZoneId.of("Asia/Tokyo"), SourceKind.REPLAY)
        e.setWaypoints(WaypointCsv.parse(csv).waypoints)
        fixes.filter { it.timeMs <= AT }.forEach { e.onFix(it, it.timeMs) }
        return e.state
    }

    private val rect = HudRect(0f, 0f, 720f, 95f)

    @Test
    fun replayAtOneMinuteDrawsTheLinesToTheWaypoints() {
        val s = stateAt(WP_PROFILE)
        assertEquals("展望台", s.waypoints[s.nextWpIndex!!].name)
        val p = ProfileBuilder.build(s, rect)
        // 点: 現在地・展望台（次、マゼンタ）・ダム・終点（道の駅は標高なしで描かない）
        assertEquals(listOf(Ink.OWNSHIP, Ink.ACTIVE, Ink.WP, Ink.WP), p.points.map { it.ink })
        // 線: 現在地 → 展望台（マゼンタの実線）、展望台 → ダム（道の駅を挟むので破線）、ダム → 終点（実線）
        assertEquals(3, p.segments.size)
        assertEquals(Ink.ACTIVE, p.segments[0].ink)
        assertFalse(p.segments[0].dashed)
        assertTrue(p.segments[1].dashed)
        assertFalse(p.segments[2].dashed)
        // 縦軸: 終点の 150 m から現在地の ALT まで
        assertEquals("150", p.labels[1].text)
        assertEquals(s.altM!!, p.labels[0].text.toDouble(), 1.0)
    }

    @Test
    fun withoutElevationsOnlyTheCurrentPointIsDrawn() {
        // D02 のスクリーンショットの場面: 標高があるのは到達済みの峠の入口だけ。
        // 目標（展望台から先）に標高がないので、点は現在地だけで線はない（§6.6 の「片側しか分からなければ線を描かない」）。
        // 縦軸は現在地の ALT を中心に最小の幅 50 m
        val s = stateAt(WP_NO_ELE)
        val p = ProfileBuilder.build(s, rect)
        assertEquals(listOf(Ink.OWNSHIP), p.points.map { it.ink })
        assertTrue(p.segments.isEmpty())
        val hi = p.labels[0].text.toDouble()
        val lo = p.labels[1].text.toDouble()
        assertEquals(50.0, hi - lo, 1.0)
    }
}
