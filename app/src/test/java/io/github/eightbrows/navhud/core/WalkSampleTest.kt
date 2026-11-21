package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.io.SessionZip
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.nav.Heading
import io.github.eightbrows.navhud.core.nav.NavEngine
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.nav.TravelMode
import io.github.eightbrows.navhud.core.nav.selectTravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId

/**
 * 20261121-D01: 歩行の記録（sample/walk の zip。git 管理外なので、なければスキップ）で、歩行のプリセットの HDG を確かめる。
 * 位置から求める向き（TRK）になる所があり、GPS の方位がそのまま使えるときは GPS のまま。自動車のプリセットでは TRK にならない。
 */
class WalkSampleTest {

    private val fixes: List<Fix> by lazy {
        val dir = listOf(File("sample/walk"), File("../sample/walk")).firstOrNull { it.isDirectory }
        val zip = dir?.listFiles { f -> f.name.endsWith(".zip") }?.minByOrNull { it.name }
        assumeTrue("歩行の記録がないためスキップ", zip != null)
        val s = zip!!.inputStream().use { SessionZip.list(it) }.first()
        zip.inputStream().use { SessionZip.read(it, s.entryName) }!!.fixes
    }

    private fun headings(s: NavSettings): List<Heading> {
        val e = NavEngine(s, ZoneId.of("Asia/Tokyo"), SourceKind.REPLAY)
        return fixes.map { e.onFix(it, it.timeMs).heading }
    }

    @Test
    fun walkPresetUsesTrackWhenGpsCannotBeUsed() {
        val walk = headings(NavSettings().selectTravelMode(TravelMode.WALK))
        val car = headings(NavSettings())
        val trk = walk.indices.filter { walk[it].src == HeadingSrc.TRACK }
        // TRK になる所がある（歩いている間の 2 割以上）
        val walking = fixes.indices.filter { (fixes[it].speedMps ?: 0f) >= 2 / 3.6f }
        assertTrue("TRK ${trk.size}", trk.count { it in walking } > walking.size / 5)
        // 自動車では TRK にならない
        assertTrue(car.none { it.src == HeadingSrc.TRACK })
        // GPS の方位が使える点（保持していない・方位精度 30° 以内）は GPS で、その点の方位
        for (i in walk.indices) {
            val h = walk[i]
            if (h.src == HeadingSrc.GPS && !h.held) {
                val f = fixes[i]
                assertTrue("$i", f.bearingAccDeg == null || f.bearingAccDeg!! <= 30f || h.deg != f.bearingDeg)
            }
        }
        // 止まっている間（速さ 0）は TRK の向きを変えない
        for (i in 1 until walk.size) {
            if (fixes[i].speedMps == 0f && walk[i].src == HeadingSrc.TRACK && walk[i - 1].src == HeadingSrc.TRACK) {
                assertEquals("$i", walk[i - 1].deg, walk[i].deg)
            }
        }
    }
}
