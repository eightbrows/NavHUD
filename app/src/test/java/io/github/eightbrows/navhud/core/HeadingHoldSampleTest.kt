package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.nav.Heading
import io.github.eightbrows.navhud.core.nav.NavEngine
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.nav.TravelMode
import io.github.eightbrows.navhud.core.nav.editReach
import io.github.eightbrows.navhud.core.nav.selectTravelMode
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

    /** 既定の設定（方位ソース GPS。20261119-D01 から止める / 戻す速度は 4 / 6 km/h） */
    private val settings = NavSettings()

    /** 既定の設定でトラック全体を流したときの、Fix ごとの方位。 */
    private val headings: List<Heading> by lazy {
        val e = NavEngine(settings, ZoneId.of("Asia/Tokyo"), SourceKind.REPLAY)
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

    /**
     * 既定の設定での Fix ごとの HDG を app/build/heading-check.txt に書き出す（20261119-D01。調べの計算と比べるため。build は git 管理外）。
     * 書き出すだけで、確かめは上と下のテスト
     */
    @Test
    fun headingsAreWrittenForChecking() {
        val dir = java.io.File("build").takeIf { it.isDirectory } ?: return
        val text = fixes.indices.joinToString("\n") { i ->
            val h = headings[i]
            "${fixes[i].timeMs} ${h.deg ?: "-"} ${h.src} ${h.held}"
        }
        java.io.File(dir, "heading-check.txt").writeText(text + "\n")
        assertTrue(headings.size == fixes.size)
    }

    /**
     * 20261119-D03: カスタムのプリセットで方位を止める / 戻す速度を変えると、HDG はその値に従う。
     * カスタム1 を 9 / 20 km/h にしたときの HDG を app/build/heading-check-custom.txt に書き出す（調べの計算と比べるため）。
     * 同じ値を NavSettings に直接入れたときと同じで、既定（自動車 4 / 6 km/h）とは違うことを確かめる
     */
    @Test
    fun customPresetHoldSpeedsDriveTheHeading() {
        val custom = NavSettings().selectTravelMode(TravelMode.CUSTOM1).editReach { it.copy(holdEnterKmh = 9, holdExitKmh = 20) }
        fun run(s: NavSettings): List<Heading> {
            val e = NavEngine(s, ZoneId.of("Asia/Tokyo"), SourceKind.REPLAY)
            return fixes.map { e.onFix(it, it.timeMs).heading }
        }
        val h = run(custom)
        val direct = run(NavSettings(holdEnterSpeedMps = NavSettings.kmhToMps(9), holdExitSpeedMps = NavSettings.kmhToMps(20)))
        assertTrue(h == direct)
        assertTrue(h != headings)
        java.io.File("build").takeIf { it.isDirectory }?.let { dir ->
            val text = fixes.indices.joinToString("\n") { i -> "${fixes[i].timeMs} ${h[i].deg ?: "-"} ${h[i].src} ${h[i].held}" }
            java.io.File(dir, "heading-check-custom.txt").writeText(text + "\n")
        }
    }

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
        var worst = 0.0
        for (stop in stops()) {
            val held = headings[stop.first].deg ?: continue
            val t0 = fixes[stop.first].timeMs
            // 停止前 5 秒で、保持解除速度（既定の設定の値。20261119-D01 から 6 km/h、前は 11 km/h ≒ 3 m/s）以上で走っていた間の方位
            val before = (0 until stop.first)
                .filter { t0 - fixes[it].timeMs in 1..BEFORE_STOP_MS }
                .map { fixes[it] }
                .filter { (it.speedMps ?: 0f) >= settings.holdExitSpeedMps && it.bearingDeg != null && (it.bearingAccDeg ?: 0f) <= 20f }
                .map { it.bearingDeg!!.toDouble() }
            if (before.size < 2) continue
            checked++
            val ref = circularMean(before)
            val d = angleDist(held.toDouble(), ref)
            if (d > 15.0) results += "${time(stop.first)} 保持 %.0f° / 停止前 %.0f° / 差 %.0f°".format(held, ref, d)
            worst = maxOf(worst, d)
        }
        assertTrue("確認できた停止区間が少ない: $checked", checked >= 5)
        // 20261119-D01（戻す速度 6 km/h）から、6〜11 km/h で這うように止まる所では、そのときの GPS の向き（精度 20° 以内）を保持する。
        // D05 の 00:06:10 は 8.6 km/h の点の向きが外れていて 36° ずれる。こういう停止は 1 割まで、ずれも 60° 以内であることを確かめる
        assertTrue("±15° を超えた停止区間が多い（$checked 回中）:\n" + results.joinToString("\n"), results.size * 10 <= checked)
        assertTrue("60° を超えてずれた停止区間:\n" + results.joinToString("\n"), worst <= 60.0)
    }

    @Test
    fun noRapidToggleAroundStops() {
        // 停止の 10 秒前から停止の終わりの 5 秒後までで、GPS ⇔ HLD の切り替わりはふつう多くても 2 回（入る・出る）。
        // 20261119-D01（戻す速度 6 km/h）から、止まる直前に 6 km/h を超えて這うように動く所（D05 の 23:00:36 ごろ、6.6 km/h）では
        // 本当に動いているので 4 回になることがある。そういう停止は 1 割まで、切り替わりは速さの決まりどおり（戻すのは 6 km/h を超えたとき、
        // 止めるのは 4 km/h 未満のとき）であることを確かめる
        val bad = mutableListOf<String>()
        val many = mutableListOf<String>()
        val n = stops().size
        for (stop in stops()) {
            val from = (stop.first - 10).coerceAtLeast(1)
            val to = (stop.last + 5).coerceAtMost(fixes.lastIndex)
            val toggles = (from..to).filter { headings[it].held != headings[it - 1].held }
            for (i in toggles) {
                val v = fixes[i].speedMps ?: continue
                if (headings[i].held && v >= settings.holdEnterSpeedMps) bad += "${time(i)} ${v * 3.6f} km/h で止めた"
                if (!headings[i].held && v <= settings.holdExitSpeedMps) bad += "${time(i)} ${v * 3.6f} km/h で戻した"
            }
            if (toggles.size > 2) many += "${time(stop.first)} 切り替わり ${toggles.size} 回"
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
        assertTrue("切り替わりが多い停止が多すぎる（$n 回中）:\n" + many.joinToString("\n"), many.size * 10 <= n)
    }
}
