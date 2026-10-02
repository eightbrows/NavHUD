package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.io.TrackCsv
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
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * 標高プロファイル（§6.6）を、track.csv のリプレイの場面で確かめる（REPLAY 0:01 = 07:53:43 JST、峠の入口を過ぎて次は展望台）。
 * track.csv は git 管理外なので、ファイルがなければスキップする。
 */
class ProfileSampleTest {

    companion object {
        private const val PATH = "sample/session_20260814_075234/track.csv"
        private val file: File? = listOf(File(PATH), File("../$PATH")).firstOrNull { it.exists() }
        private val track: List<Fix>? by lazy { file?.inputStream()?.use { TrackCsv.parse(it).fixes } }

        /** 動作確認で使う WP リスト（wp_profile.csv）。標高は 峠の入口・展望台・ダム・終点 */
        private val WP_PROFILE = """
            lat,lon,ele,name,target_time,deadline_time,enabled
            33.47649101,133.00275172,1300,峠の入口,,,1
            33.46679346,132.96174603,1180,展望台,8:30,8:45,1
            33.48377796,132.87220649,,旧道（通行止め）,,,0
            33.48750794,132.90060923,,道の駅,09:10,9:20:00,1
            33.55944388,133.00766313,620,ダム,,,1
            33.667743,132.8949383,150,終点,11:00,,1
        """.trimIndent()

        /** 標高が 峠の入口 にしかない WP リスト（waypoints.csv。D02 のスクリーンショットで読まれていたもの） */
        private val WP_NO_ELE = WP_PROFILE.lines().joinToString("\n") { line ->
            val c = line.split(",").toMutableList()
            if (c[3] != "峠の入口" && c[3] != "name") c[2] = ""
            c.joinToString(",")
        }

        private val AT = Instant.parse("2026-08-13T22:53:43Z").toEpochMilli()
    }

    private val fixes: List<Fix>
        get() {
            assumeTrue("track.csv がないためスキップ: $PATH", track != null)
            return track!!
        }

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
