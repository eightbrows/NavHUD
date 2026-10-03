package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.nav.NavEngine
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.SourceKind
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

/**
 * 到達判定のタイミング（§5.4）を track.csv で測る。走行ルートに沿って、道路から 10〜90m 横に仮の WP を置き、
 * 「到達した時刻 − 最接近の時刻」を、前の規則（到達半径 100m・通過判定）と新しい規則（自動車の推奨値）で比べる。
 * 結果は build/pass-timing/ に CSV で書き出す（レポート用）。track.csv は git 管理外なので、なければスキップする。
 */
class PassTimingSampleTest {

    companion object {
        private val OFFSETS = listOf(10.0, 30.0, 50.0, 70.0, 90.0)

        /** 前の規則（D05 まで）: 到達半径 100m、真横通過なし、通過判定 300m・+50m・5 秒 */
        val OLD = NavSettings(reachRadiusM = 100.0, sidePass = false)

        /** 新しい規則: 自動車の推奨値（到着半径 30m、真横通過 150m・+10m、通過判定） */
        val NEW = NavSettings()
    }

    private val fixes: List<Fix>
        get() = SampleTrack.fixes()

    /** 1件の仮の WP。i: 置いた所の Fix の番号、offsetM: 道路から横（+ 右 / − 左）。 */
    data class Probe(val i: Int, val offsetM: Double, val wp: Waypoint)

    /** 走行中（8 m/s 以上で方位あり、前後 5 点も走行中）の Fix の横に、100 点以上の間隔をあけて WP を置く。 */
    private fun probes(): List<Probe> {
        val f = fixes
        val out = mutableListOf<Probe>()
        var i = 200
        var k = 0
        while (i < f.size - 200) {
            val ok = (i - 5..i + 5).all { (f[it].speedMps ?: 0f) >= 8f && f[it].bearingDeg != null }
            if (ok) {
                val off = OFFSETS[k % OFFSETS.size] * (if ((k / OFFSETS.size) % 2 == 0) 1 else -1)
                val course = Geo.bearingDeg(f[i - 5].lat, f[i - 5].lon, f[i + 5].lat, f[i + 5].lon)
                val a = Math.toRadians(course + 90)
                val lat = TestGeo.lat(off * cos(a), f[i].lat)
                val lon = TestGeo.lon(off * sin(a), f[i].lat, f[i].lon)
                out += Probe(i, off, Waypoint("P$k", lat, lon))
                k++
                i += 100
            } else {
                i += 10
            }
        }
        return out
    }

    /** 置いた所の 60 点前から 120 点後までを流して、到達した時刻 [ms]（ならなければ null）。 */
    private fun reachedAt(p: Probe, s: NavSettings): Long? {
        val e = NavEngine(s, ZoneId.of("Asia/Tokyo"), SourceKind.REPLAY)
        e.setWaypoints(listOf(p.wp))
        for (f in fixes.subList(p.i - 60, p.i + 120)) {
            if (e.onFix(f, f.timeMs).waypoints.single().reached) return f.timeMs
        }
        return null
    }

    /** 同じ区間の中で、WP にいちばん近い Fix の時刻 [ms]。 */
    private fun closestAt(p: Probe): Long =
        fixes.subList(p.i - 60, p.i + 120).minBy { Geo.distanceM(it.lat, it.lon, p.wp.lat, p.wp.lon) }.timeMs

    @Test
    fun reachTimingAroundTheClosestApproach() {
        val ps = probes()
        val fmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Tokyo"))
        val rows = ps.map { p ->
            val c = closestAt(p)
            Triple(p, c, listOf(OLD, NEW).map { s -> reachedAt(p, s)?.let { (it - c) / 1000.0 } })
        }
        val dir = File("build/pass-timing").apply { mkdirs() }
        File(dir, "probes.csv").writeText(
            "closest_time,offset_m,old_s,new_s\n" + rows.joinToString("\n") { (p, c, d) ->
                "${fmt.format(Instant.ofEpochMilli(c))},${p.offsetM.toInt()},${d.joinToString(",") { it?.toString() ?: "" }}"
            } + "\n",
        )
        // 規則ごと・横の距離ごとの集計: 件数、中央値、5 秒以上早い数、10 秒以上遅い数、到達しなかった数
        fun summary(values: List<Double?>): String {
            val v = values.filterNotNull().sorted()
            val median = if (v.isEmpty()) Double.NaN else if (v.size % 2 == 1) v[v.size / 2] else (v[v.size / 2 - 1] + v[v.size / 2]) / 2
            return "${values.size},$median,${v.count { it <= -5.0 }},${v.count { it >= 10.0 }},${values.count { it == null }}"
        }
        val lines = mutableListOf("rule,offset_m,count,median_s,early5,late10,not_reached")
        for ((ri, name) in listOf("old", "new").withIndex()) {
            lines += "$name,all,${summary(rows.map { it.third[ri] })}"
            for (o in OFFSETS) lines += "$name,${o.toInt()},${summary(rows.filter { kotlin.math.abs(it.first.offsetM) == o }.map { it.third[ri] })}"
        }
        File(dir, "summary.csv").writeText(lines.joinToString("\n") + "\n")
        assertTrue(ps.size >= 30)
    }
}
