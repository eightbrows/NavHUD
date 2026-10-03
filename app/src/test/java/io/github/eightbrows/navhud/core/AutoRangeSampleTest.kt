package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.io.WaypointCsv
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.nav.NavEngine
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.view.HudMetrics
import io.github.eightbrows.navhud.core.view.HudViewport
import org.junit.Assert.assertTrue
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
        private val JST: ZoneId = ZoneId.of("Asia/Tokyo")

        private val metrics = HudMetrics().scaled(MeasuredScreen.DENSITY)
        val LIVE = HudViewport(MeasuredScreen.RECT, metrics, MeasuredScreen.LIVE)
        val REPLAY = HudViewport(MeasuredScreen.RECT, metrics, MeasuredScreen.REPLAY)
    }

    private val fixes: List<Fix>
        get() = SampleTrack.fixes()

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
