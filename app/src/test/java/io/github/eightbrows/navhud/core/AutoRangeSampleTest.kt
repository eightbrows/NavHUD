package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.io.TrackCsv
import io.github.eightbrows.navhud.core.io.WaypointCsv
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.nav.NavEngine
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.view.HudInsets
import io.github.eightbrows.navhud.core.view.HudMetrics
import io.github.eightbrows.navhud.core.view.HudRect
import io.github.eightbrows.navhud.core.view.HudViewport
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * AUTO 縮尺を track.csv の REPLAY（WP は wp_profile.csv）で通して、段が変わった回数と時刻を数える（§6.1）。
 * 画面はエミュレータで測った寸法（LIVE / REPLAY）。結果は build/auto-range/ に CSV で書き出す（レポート用）。
 * track.csv は git 管理外なので、ファイルがなければスキップする。
 */
class AutoRangeSampleTest {

    companion object {
        private const val PATH = "sample/session_20260814_075234/track.csv"
        private val file: File? = listOf(File(PATH), File("../$PATH")).firstOrNull { it.exists() }
        private val track: List<Fix>? by lazy { file?.inputStream()?.use { TrackCsv.parse(it).fixes } }
        private val JST: ZoneId = ZoneId.of("Asia/Tokyo")

        private val WP_PROFILE = """
            lat,lon,ele,name,target_time,deadline_time,enabled
            33.47649101,133.00275172,1300,峠の入口,,,1
            33.46679346,132.96174603,1180,展望台,8:30,8:45,1
            33.48377796,132.87220649,,旧道（通行止め）,,,0
            33.48750794,132.90060923,,道の駅,09:10,9:20:00,1
            33.55944388,133.00766313,620,ダム,,,1
            33.667743,132.8949383,150,終点,11:00,,1
        """.trimIndent()

        private val rect = HudRect(0f, 0f, 720f, 1239f)
        private val metrics = HudMetrics().scaled(1.7f)
        val LIVE = HudViewport(rect, metrics, HudInsets(top = 211f, right = 102f, bottom = 204f, rightSpan = 470f..776f))
        val REPLAY = HudViewport(rect, metrics, HudInsets(top = 211f, right = 102f, bottom = 265f, rightSpan = 439.5f..745.5f))
    }

    private val fixes: List<Fix>
        get() {
            assumeTrue("track.csv がないためスキップ: $PATH", track != null)
            return track!!
        }

    /** 段が変わった記録（時刻 [ms]、前の段 [m]、後の段 [m]）。 */
    data class Change(val timeMs: Long, val fromM: Double, val toM: Double)

    /** トラック全体を流す。Fix の間も 1 秒ごとに時計を進める（アプリの刻みの代わり）。 */
    private fun run(vp: HudViewport, settings: NavSettings = NavSettings()): List<Change> {
        val e = NavEngine(settings, JST, SourceKind.REPLAY)
        e.setWaypoints(WaypointCsv.parse(WP_PROFILE).waypoints)
        e.setViewport(vp)
        val changes = mutableListOf<Change>()
        var last = e.state.rangeM
        fun record(t: Long, r: Double) {
            if (r != last) changes += Change(t, last, r)
            last = r
        }
        var prev: Long? = null
        for (f in fixes) {
            prev?.let { p -> var t = p + 1_000; while (t < f.timeMs) { record(t, e.onTick(t).rangeM); t += 1_000 } }
            record(f.timeMs, e.onFix(f, f.timeMs).rangeM)
            prev = f.timeMs
        }
        return changes
    }

    private fun write(name: String, changes: List<Change>) {
        val fmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(JST)
        val dir = File("build/auto-range").apply { mkdirs() }
        File(dir, "$name.csv").writeText(
            "time,from_m,to_m\n" + changes.joinToString("\n") { "${fmt.format(Instant.ofEpochMilli(it.timeMs))},${it.fromM.toInt()},${it.toM.toInt()}" } + "\n",
        )
    }

    @Test
    fun countRangeChangesOverTheWholeTrack() {
        val live = run(LIVE)
        val replay = run(REPLAY)
        write("live", live)
        write("replay", replay)
        // 記録が取れていること（段は少なくとも一度は決まる）
        assertTrue(live.isNotEmpty() && replay.isNotEmpty())
        // 既定の AUTO（下限 100m・上限 1km の段）: どの段も範囲の中
        assertTrue((live + replay).all { it.toM in 100.0..1_000.0 })
    }
}
