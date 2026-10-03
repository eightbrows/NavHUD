package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.nav.Heading
import io.github.eightbrows.navhud.core.nav.NavEngine
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.SourceKind
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * ステップ7a: track.csv の停止区間で、GPS 方位の保持（HLD）を確かめる。
 * track.csv は git 管理外なので、ファイルがなければスキップする。
 */
class HeadingHoldSampleTest {

    companion object {
        /** 停止区間とみなす、速度 0 が続く最短の点数 */
        private const val MIN_STOP_FIXES = 10

        /** 停止前の走行方位を見る時間（長くすると、曲がりながら止まったときに曲がる前の方位が混ざる） */
        private const val BEFORE_STOP_MS = 5_000L
    }

    private val fixes: List<Fix>
        get() = SampleTrack.fixes()

    /** 既定の設定（方位ソース GPS）でトラック全体を流したときの、Fix ごとの方位。 */
    private val headings: List<Heading> by lazy {
        val e = NavEngine(NavSettings(), ZoneId.of("Asia/Tokyo"), SourceKind.REPLAY)
        fixes.map { e.onFix(it, it.timeMs).heading }
    }

    /** 速度 0 が欠損なしで MIN_STOP_FIXES 点以上続く区間（開始・終了の番号）。 */
    private fun stops(): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var start = -1
        for (i in fixes.indices) {
            val contiguous = i > 0 && fixes[i].timeMs - fixes[i - 1].timeMs <= 1_500
            if (fixes[i].speedMps == 0f && (start < 0 || contiguous)) {
                if (start < 0) start = i
            } else {
                if (start >= 0 && i - start >= MIN_STOP_FIXES) out += start until i
                start = if (fixes[i].speedMps == 0f) i else -1
            }
        }
        return out
    }

    private fun angleDist(a: Double, b: Double): Double = abs(Geo.angleDiff(a, b))

    /** 方位の平均（単位ベクトルの平均）。 */
    private fun circularMean(degs: List<Double>): Double {
        val x = degs.sumOf { cos(Math.toRadians(it)) }
        val y = degs.sumOf { sin(Math.toRadians(it)) }
        return Geo.normalize360(Math.toDegrees(atan2(y, x)))
    }

    private fun time(i: Int) = Instant.ofEpochMilli(fixes[i].timeMs).toString()

    @Test
    fun trackHasStops() {
        assertTrue("停止区間が少なすぎる: ${stops().size}", stops().size >= 10)
    }

    @Test
    fun whileStoppedHeadingIsHeldAndConstant() {
        val bad = mutableListOf<String>()
        for (stop in stops()) {
            val hs = stop.map { headings[it] }
            val first = hs.first()
            if (first.deg == null) continue // トラックの冒頭で一度も走っていない場合
            if (!hs.all { it.src == HeadingSrc.GPS && it.held }) bad += "${time(stop.first)} HLD でない点がある"
            if (hs.any { it.deg != first.deg }) bad += "${time(stop.first)} 停止中に方位が変わる"
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun heldHeadingIsCloseToHeadingBeforeTheStop() {
        val results = mutableListOf<String>()
        var checked = 0
        for (stop in stops()) {
            val held = headings[stop.first].deg ?: continue
            val t0 = fixes[stop.first].timeMs
            // 停止前 5 秒で、保持解除速度（3 m/s）以上で走っていた間の方位
            val before = (0 until stop.first)
                .filter { t0 - fixes[it].timeMs in 1..BEFORE_STOP_MS }
                .map { fixes[it] }
                .filter { (it.speedMps ?: 0f) >= 3.0f && it.bearingDeg != null && (it.bearingAccDeg ?: 0f) <= 20f }
                .map { it.bearingDeg!!.toDouble() }
            if (before.size < 2) continue
            checked++
            val ref = circularMean(before)
            val d = angleDist(held.toDouble(), ref)
            if (d > 15.0) results += "${time(stop.first)} 保持 %.0f° / 停止前 %.0f° / 差 %.0f°".format(held, ref, d)
        }
        assertTrue("確認できた停止区間が少ない: $checked", checked >= 5)
        assertTrue("±15° を超えた停止区間:\n" + results.joinToString("\n"), results.isEmpty())
    }

    @Test
    fun noRapidToggleAroundStops() {
        // 停止の 10 秒前から停止の終わりの 5 秒後までで、GPS ⇔ HLD の切り替わりは多くても 2 回（入る・出る）。
        // 窓を広げると、渋滞などで本当に停止と発進を繰り返した分まで数えてしまう
        val bad = mutableListOf<String>()
        for (stop in stops()) {
            val from = (stop.first - 10).coerceAtLeast(1)
            val to = (stop.last + 5).coerceAtMost(fixes.lastIndex)
            val toggles = (from..to).count { headings[it].held != headings[it - 1].held }
            if (toggles > 2) bad += "${time(stop.first)} 切り替わり $toggles 回"
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }
}
