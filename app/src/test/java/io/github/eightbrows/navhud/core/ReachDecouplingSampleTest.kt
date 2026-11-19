package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.io.SessionZip
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.nav.NavEngine
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

/**
 * 20261119-D01: 真横通過の判定を始める速さを、方位を止める / 戻す速度から切り離した。
 * 記録（D05 の track.csv と sample/compass の zip。git 管理外なので、なければスキップ）の上に仮の WP を道の左右に置き、
 * 方位の値を変えても、到達の判定（理由・時刻）が同じことを確かめる。
 * 既定の設定での結果は app/build/reach-check.txt に書き出す（前の版と比べるため。build は git 管理外）。
 */
class ReachDecouplingSampleTest {

    private fun tracks(): List<Pair<String, List<Fix>>> {
        val out = mutableListOf<Pair<String, List<Fix>>>()
        listOf(File(SampleTrack.PATH), File("../${SampleTrack.PATH}")).firstOrNull { it.exists() }?.let {
            out += "D05" to SampleTrack.fixes()
        }
        val dir = listOf(File("sample/compass"), File("../sample/compass")).firstOrNull { it.isDirectory }
        dir?.listFiles { f -> f.name.endsWith(".zip") }?.sortedBy { it.name }?.forEach { zip ->
            for (s in zip.inputStream().use { SessionZip.list(it) }.sortedBy { it.entryName }) {
                val r = zip.inputStream().use { SessionZip.read(it, s.entryName) } ?: continue
                out += s.name to r.fixes
            }
        }
        assumeTrue("記録がないためスキップ", out.isNotEmpty())
        return out
    }

    /** fixes[i] の進む向きから左右へ offsetM ずらした仮の WP（向きは前後 20 点の位置から） */
    private fun besideTrack(fixes: List<Fix>, i: Int, offsetM: Double): Waypoint? {
        val a = fixes[(i - 20).coerceAtLeast(0)]
        val b = fixes[(i + 20).coerceAtMost(fixes.lastIndex)]
        if (Geo.distanceM(a.lat, a.lon, b.lat, b.lon) < 10.0) return null
        val course = Geo.bearingDeg(a.lat, a.lon, b.lat, b.lon)
        val r = Math.toRadians(course + 90)
        val f = fixes[i]
        return Waypoint("W", TestGeo.lat(offsetM * cos(r), f.lat), TestGeo.lon(offsetM * sin(r), f.lat, f.lon))
    }

    /** 仮の WP と、流す区間（前後 300 点） */
    private fun cases(): List<Triple<String, Waypoint, IntRange>> {
        val out = mutableListOf<Triple<String, Waypoint, IntRange>>()
        for ((name, fixes) in tracks()) {
            for (i in 30 until fixes.size - 30 step 90) {
                for (off in listOf(-120.0, -80.0, -40.0, -15.0, 15.0, 40.0, 80.0, 120.0)) {
                    val w = besideTrack(fixes, i, off) ?: continue
                    out += Triple("$name ${fixes[i].timeMs} $off", w, (i - 300).coerceAtLeast(0)..(i + 300).coerceAtMost(fixes.lastIndex))
                }
            }
        }
        return out
    }

    private fun results(settings: NavSettings, cases: List<Triple<String, Waypoint, IntRange>>): List<String> {
        val byName = tracks().toMap()
        return cases.map { (key, w, range) ->
            val fixes = byName.getValue(key.substringBefore(' '))
            val e = NavEngine(settings, ZoneId.of("Asia/Tokyo"), SourceKind.REPLAY)
            e.setWaypoints(listOf(w))
            for (i in range) e.onFix(fixes[i], fixes[i].timeMs)
            val r = e.state.waypoints.single().reach
            "$key ${r?.reason ?: "-"} ${r?.timeMs ?: "-"}"
        }
    }

    @Test
    fun reachDoesNotDependOnHeadingHoldSpeeds() {
        val cases = cases()
        val base = results(NavSettings(), cases)
        File("build").takeIf { it.isDirectory }?.let { File(it, "reach-check.txt").writeText(base.joinToString("\n") + "\n") }
        // 前の初期値 7 / 11 km/h・とても低い 2 / 4 km/h・とても高い 20 / 30 km/h でも、到達の判定は同じ
        for ((enter, exit) in listOf(7 to 11, 2 to 4, 20 to 30)) {
            val s = NavSettings(holdEnterSpeedMps = NavSettings.kmhToMps(enter), holdExitSpeedMps = NavSettings.kmhToMps(exit))
            val r = results(s, cases)
            val diff = base.indices.filter { base[it] != r[it] }.map { "${base[it]} → ${r[it]}" }
            assertEquals("$enter / $exit km/h で変わった WP:\n" + diff.take(20).joinToString("\n"), 0, diff.size)
        }
        // 到達の理由は真横通過がいちばん多い（確かめが真横通過を含んでいること）
        assertEquals(true, base.count { it.contains(" SIDE ") } > base.size / 3)
    }
}
