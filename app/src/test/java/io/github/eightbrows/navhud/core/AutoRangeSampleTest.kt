package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.io.WaypointCsv
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.ReachReason
import io.github.eightbrows.navhud.core.nav.NavEngine
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.nav.SidePassDetector
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.view.HudMetrics
import io.github.eightbrows.navhud.core.view.HudViewport
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    /** 段が変わった記録（時刻 [ms]、前の段 [m]、後の段 [m]、そのときの次の WP までの距離 [m]）。 */
    data class Change(val timeMs: Long, val fromM: Double, val toM: Double, val wpDistM: Double?) {
        /** 狭めたときの「次の WP までの距離 ÷ 狭める前の R1（1つ目の距離環 = 段の 1/2）」。1 より大きければ1つ目の円の外で狭めた */
        val zoomInRatio: Double? get() = if (toM < fromM) wpDistM?.let { it / (fromM / 2) } else null
    }

    /** トラック全体を流す。Fix の間も 1 秒ごとに時計を進める（アプリの刻みの代わり）。 */
    private fun run(vp: HudViewport, settings: NavSettings = NavSettings()): List<Change> {
        val e = NavEngine(settings, JST, SourceKind.REPLAY)
        e.setWaypoints(WaypointCsv.parse(WP_PROFILE).waypoints)
        e.setViewport(vp)
        val changes = mutableListOf<Change>()
        var last = e.state.rangeM
        fun record(t: Long, st: NavState) {
            val r = st.rangeM
            if (r != last) changes += Change(t, last, r, st.nextWpDistanceM)
            last = r
        }
        var prev: Long? = null
        for (f in fixes) {
            prev?.let { p -> var t = p + 1_000; while (t < f.timeMs) { record(t, e.onTick(t)); t += 1_000 } }
            record(f.timeMs, e.onFix(f, f.timeMs))
            prev = f.timeMs
        }
        return changes
    }

    private fun write(name: String, changes: List<Change>) {
        val fmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(JST)
        val dir = File("build/auto-range").apply { mkdirs() }
        File(dir, "$name.csv").writeText(
            "time,from_m,to_m,wp_dist_m,dist_per_r1\n" + changes.joinToString("\n") {
                "${fmt.format(Instant.ofEpochMilli(it.timeMs))},${it.fromM.toInt()},${it.toM.toInt()}," +
                    "${it.wpDistM?.toInt() ?: ""},${it.zoomInRatio?.let { r -> "%.2f".format(Locale.US, r) } ?: ""}"
            } + "\n",
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

    /** WP ごとの記録: 到達した時刻と理由、通り過ぎた時刻、到達のあと最初に縮尺が変わった時刻と段。 */
    data class WpTiming(
        val name: String,
        var reachedMs: Long? = null,
        var reason: ReachReason? = null,
        var passedMs: Long? = null,
        var changeMs: Long? = null,
        var changeFromM: Double? = null,
        var changeToM: Double? = null,
    )

    /**
     * トラック全体を流して、WP ごとの到達・通り過ぎ・縮尺の変化を記録する（レポート用）。
     * 「通り過ぎた」は真横通過と同じ条件（WP が進行方向から 90° 以上・いちばん近づいた距離から離れる距離以上・速度と方位）で、
     * 距離の上限（sidePassMaxM）は使わない。SidePassDetector をそのまま使う（版をまたいで同じ測り方）。
     */
    private fun timing(vp: HudViewport, settings: NavSettings = NavSettings()): List<WpTiming> {
        val wps = WaypointCsv.parse(WP_PROFILE).waypoints
        val e = NavEngine(settings, JST, SourceKind.REPLAY)
        e.setWaypoints(wps)
        e.setViewport(vp)
        val rec = wps.map { WpTiming(it.name) }
        val passSettings = settings.copy(sidePass = true, sidePassMaxM = Double.MAX_VALUE)
        val det = SidePassDetector()
        var pending: Int? = null
        var last = e.state.rangeM
        var lastReached: Int? = null
        fun step(t: Long, st: NavState, fix: Fix?) {
            for ((i, wp) in st.waypoints.withIndex()) {
                val r = wp.reach ?: continue
                if (rec[i].reachedMs != null) continue
                rec[i].reachedMs = t
                rec[i].reason = r.reason
                lastReached = i
                pending = i
                det.reset()
                if (r.reason == ReachReason.SIDE) {
                    rec[i].passedMs = t
                    pending = null
                }
            }
            val p = pending
            if (p != null && fix != null && det.update(fix, wps[p], p, passSettings)) {
                rec[p].passedMs = t
                pending = null
            }
            if (st.rangeM != last) {
                lastReached?.let { i ->
                    if (rec[i].changeMs == null) {
                        rec[i].changeMs = t
                        rec[i].changeFromM = last
                        rec[i].changeToM = st.rangeM
                    }
                }
                last = st.rangeM
            }
        }
        var prev: Long? = null
        for (f in fixes) {
            prev?.let { p -> var t = p + 1_000; while (t < f.timeMs) { step(t, e.onTick(t), null); t += 1_000 } }
            step(f.timeMs, e.onFix(f, f.timeMs), f)
            prev = f.timeMs
        }
        return rec
    }

    @Test
    fun waypointTimingOverTheWholeTrack() {
        val fmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(JST)
        fun t(ms: Long?) = ms?.let { fmt.format(Instant.ofEpochMilli(it)) } ?: ""
        val dir = File("build/auto-range").apply { mkdirs() }
        for ((name, vp) in listOf("live" to LIVE, "replay" to REPLAY)) {
            val rec = timing(vp)
            File(dir, "wp_timing_$name.csv").writeText(
                "wp,reached,reason,passed,change,from_m,to_m\n" + rec.joinToString("\n") {
                    "${it.name},${t(it.reachedMs)},${it.reason ?: ""},${t(it.passedMs)},${t(it.changeMs)}," +
                        "${it.changeFromM?.toInt() ?: ""},${it.changeToM?.toInt() ?: ""}"
                } + "\n",
            )
            // 有効な WP はどれも到達する
            assertTrue(rec.filterIndexed { i, _ -> WaypointCsv.parse(WP_PROFILE).waypoints[i].enabled }.all { it.reachedMs != null })
        }
    }
}
