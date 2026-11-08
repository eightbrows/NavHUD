package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.replay.ReplayPlayer
import io.github.eightbrows.navhud.core.view.HudFormat
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V/S（昇降率。20261108-D01 から）: 点ごとの傾き r を時定数 τ（8 秒）の一次遅れでなまらせる。
 * 測り始めから τ 秒は "---"、点の間が「測位が途切れたとみなす時間」を超えたら測り直す、NO FIX は "---"。
 * 高さは ALT と同じ（Fix.mslAltM、なければ 楕円体高 − 標高オフセット）。
 */
class VerticalSpeedTest {

    private val t0 = 1_786_665_600_000L
    private val jst = ZoneId.of("Asia/Tokyo")
    private val tau = Tuning.VS_TAU_SEC

    /** step 秒ごと、北へ 10 m/s。高さ（楕円体高）は alt(秒) */
    private fun track(seconds: Int, step: Int = 1, alt: (Int) -> Double?): List<Fix> = (0..seconds step step).map { s ->
        Fix(timeMs = t0 + s * 1000L, lat = TestGeo.lat(10.0 * s, 33.0), lon = 133.0, altRawM = alt(s), speedMps = 10f, bearingDeg = 0f, horizAccM = 5f)
    }

    private fun engine(s: NavSettings = NavSettings()) = NavEngine(s, jst, SourceKind.REPLAY)

    /** 点を 1 つずつ入れ、点ごとの V/S（null は "---"）を返す */
    private fun run(fixes: List<Fix>, e: NavEngine = engine()): List<Double?> = fixes.map { e.onFix(it, it.timeMs).verticalSpeedMpm }

    @Test
    fun tauIsAConstant() {
        assertEquals(8.0, Tuning.VS_TAU_SEC, 0.0)
        assertEquals(5, Tuning.VS_DISPLAY_STEP_MPM)
        // シークのあと V を作っておく長さは τ の 10 倍
        assertEquals(80_000L, Tuning.VS_PRIME_MS)
        assertEquals(Tuning.VS_PRIME_MS, ReplayPlayer.BEFORE_SPAN_MS)
    }

    @Test
    fun stepReaches63PercentAfterTau() {
        // 理想の段: 120 秒平らのあと 60 m/min で上る。τ 秒（8 秒）で 63%（60 × (1 − e^−1) = 37.9）。間隔 1・2・4・8 秒で同じ
        for (step in listOf(1, 2, 4, 8)) {
            val fixes = track(240, step) { s -> if (s <= 120) 500.0 else 500.0 + (s - 120) }
            val v = run(fixes)
            fun at(sec: Int) = v[fixes.indexOfFirst { it.timeMs == t0 + sec * 1000L }]!!
            assertEquals("step $step", 0.0, at(120), 1e-9)
            assertEquals("step $step", 60 * (1 - exp(-1.0)), at(120 + 8), 1e-9)
            assertEquals("step $step", 60 * (1 - exp(-2.0)), at(120 + 16), 1e-9)
            // 十分たてば 60 に追いつく
            assertEquals("step $step", 60.0, at(240), 0.01)
        }
        // 63% を初めて越えるのは、変わってから 8 秒目（1 秒ごとの点）
        val fixes = track(200) { s -> if (s <= 120) 0.0 else (s - 120).toDouble() }
        val v = run(fixes)
        assertEquals(128, (0..200).first { it > 120 && v[it]!! >= 0.63 * 60 })
    }

    @Test
    fun startsFromTheFirstSlopeAndWaitsTau() {
        // 一定の上り 30 m/min: 測り始めは r（30）から。最初の点から 8 秒たつまでは "---"
        val v = run(track(20) { 100.0 + 0.5 * it })
        for (s in 0..7) assertNull("$s 秒", v[s])
        for (s in 8..20) assertEquals("$s 秒", 30.0, v[s]!!, 1e-9)
        // 下り・平らも同じ
        assertEquals(-12.0, run(track(10) { 100.0 - 0.2 * it })[10]!!, 1e-9)
        assertEquals(0.0, run(track(10) { 100.0 })[10]!!, 1e-9)
        // 2 秒ごと: 8 秒の点（5 つ目）から出る
        val v2 = run(track(20, 2) { 100.0 + 0.5 * it })
        assertNull(v2[3])
        assertEquals(30.0, v2[4]!!, 1e-9)
        // 8 秒ごと: 2 つ目の点（8 秒）から出る
        val v8 = run(track(40, 8) { 100.0 + 0.5 * it })
        assertNull(v8[0])
        assertEquals(30.0, v8[1]!!, 1e-9)
    }

    @Test
    fun restartsAfterAGapLongerThanTheNoFixTime() {
        // 30〜45 秒は位置が来ない（15 秒 > 測位が途切れたとみなす時間 10 秒）: 45 秒の点から測り直し、53 秒まで "---"
        val fixes = track(80) { 100.0 + it }.filter { it.timeMs - t0 !in 31_000L..44_000L }
        val e = engine()
        val v = fixes.map { e.onFix(it, it.timeMs).verticalSpeedMpm }
        fun at(sec: Int) = v[fixes.indexOfFirst { it.timeMs == t0 + sec * 1000L }]
        assertEquals(60.0, at(30)!!, 1e-9)
        assertNull(at(45))
        assertNull(at(52))
        assertEquals(60.0, at(53)!!, 1e-9)
        // 10 秒ちょうどの間は続ける（超えたら測り直し）
        val ten = track(40) { 100.0 + it }.filter { it.timeMs - t0 !in 21_000L..29_000L }
        assertEquals(60.0, run(ten).last()!!, 1e-9)
        assertNotNull(run(ten)[ten.indexOfFirst { it.timeMs == t0 + 30_000L }])
        // 設定を 3 秒にすると、5 秒の抜けで測り直す
        val short = track(40) { 100.0 + it }.filter { it.timeMs - t0 !in 21_000L..24_000L }
        val vs = run(short, engine(NavSettings(noFixTimeoutSec = 3)))
        assertNull(vs[short.indexOfFirst { it.timeMs == t0 + 25_000L }])
        assertEquals(60.0, vs.last()!!, 1e-9)
        // 時刻が戻ったら測り直す（REPLAY の巻き戻し）
        val e2 = engine()
        run(track(30) { 100.0 + it }, e2)
        assertNull(e2.onFix(track(0) { 100.0 }[0], t0).verticalSpeedMpm)
    }

    @Test
    fun noneWhileNoFix() {
        val e = engine()
        run(track(30) { 100.0 + it }, e)
        assertNotNull(e.state.verticalSpeedMpm)
        assertNotNull(e.onTick(t0 + 40_000).verticalSpeedMpm)
        assertNull(e.onTick(t0 + 40_001).verticalSpeedMpm)
    }

    @Test
    fun usesTheSameHeightAsAlt() {
        // 海抜高度（AltitudeConverter）があればそれ
        val withMsl = track(20) { 1000.0 }.mapIndexed { i, f -> f.copy(mslAltM = 200.0 + i) }
        assertEquals(60.0, run(withMsl).last()!!, 1e-9)
        // なければ 楕円体高 − オフセット（差なのでオフセットは消える）
        assertEquals(30.0, run(track(20) { 50.0 + 0.5 * it }, engine(NavSettings(altOffsetM = 40.0))).last()!!, 1e-9)
        // 高さのない点は使わない（その前後の点で傾きを出す）
        val gaps = track(20) { if (it % 3 == 1) null else 100.0 + 0.5 * it }
        assertEquals(30.0, run(gaps).last()!!, 1e-9)
        // 標高オフセットを変えたら、高さが跳ぶので測り直す
        val e = engine()
        run(track(20) { 100.0 + 0.5 * it }, e)
        assertNull(e.updateSettings(e.settings.copy(altOffsetM = 10.0)).verticalSpeedMpm)
    }

    /** なめらかに上り下りする高さ（決まった形） */
    private fun hilly(s: Int) = 800.0 + 40 * sin(s / 30.0) + 15 * sin(s / 7.0)

    @Test
    fun replaySeekShowsAlmostTheSameValueRightAway() {
        val fixes = track(300) { hilly(it) }
        val straight = run(fixes)
        val e = engine()
        val p = ReplayPlayer(fixes)
        p.due(t0).forEach { e.onFix(it, it.timeMs) }
        // 前方へ（200 秒）: ViewModel と同じ（seekReset → シーク先の前の 80 秒を入れる → シーク先の Fix）
        for (target in listOf(200, 120, 250)) {
            val r = p.seek(t0 + target * 1000L)
            e.seekReset(toStart = false, passed = r.passed)
            e.primeVerticalSpeed(r.before)
            val v = e.onFix(r.last!!, r.last!!.timeMs).verticalSpeedMpm!!
            // 続けて再生したときとの差は、測り始めの値の影響（e^−10 ≈ 0.005%）だけ
            assertTrue("$target: $v ${straight[target]}", abs(v - straight[target]!!) < 0.05)
        }
        // before は シーク先の前の 80 秒（シーク先の Fix は含まない）
        val r = p.seek(t0 + 200_000)
        assertEquals(t0 + 120_000, r.before.first().timeMs)
        assertEquals(t0 + 199_000, r.before.last().timeMs)
        // 先頭から 5 秒へ: τ 秒分の記録がないので "---"
        val early = p.seek(t0 + 5_000)
        e.seekReset(toStart = true, passed = early.passed)
        e.primeVerticalSpeed(early.before)
        assertNull(e.onFix(early.last!!, early.last!!.timeMs).verticalSpeedMpm)
    }

    @Test
    fun independentOfReplaySpeed() {
        // 記録の時刻で測るので、倍速で Fix がまとめて来ても同じ値（onFixes は倍速のときの入れ方）
        val fixes = track(120) { hilly(it) }
        val one = run(fixes).last()!!
        val e = engine()
        fixes.chunked(30).forEach { e.onFixes(it, it.last().timeMs) }
        assertEquals(one, e.state.verticalSpeedMpm!!, 1e-9)
    }

    @Test
    fun coarserPointsGiveTheSameStepResponse() {
        // 2・4・8 秒ごとの点でも、同じ時刻には同じ割合で追いつく（なまらせ方が間の秒数で決まるため）
        val ref = run(track(200) { s -> if (s <= 120) 0.0 else (s - 120).toDouble() })
        for (step in listOf(2, 4, 8)) {
            val v = run(track(200, step) { s -> if (s <= 120) 0.0 else (s - 120).toDouble() })
            assertEquals("step $step", ref[200]!!, v.last()!!, 1e-9)
            assertEquals("step $step", ref[136]!!, v[136 / step]!!, 1e-9)
        }
    }

    @Test
    fun displayIsRoundedToFiveMetersPerMinute() {
        assertEquals("+40 m/min", HudFormat.verticalSpeed(42.4))
        assertEquals("+45 m/min", HudFormat.verticalSpeed(42.5))
        assertEquals("-15 m/min", HudFormat.verticalSpeed(-14.6))
        assertEquals("-15 m/min", HudFormat.verticalSpeed(-12.5))
        assertEquals("-10 m/min", HudFormat.verticalSpeed(-12.4))
        // 0 は符号なし
        assertEquals("0 m/min", HudFormat.verticalSpeed(2.4))
        assertEquals("0 m/min", HudFormat.verticalSpeed(-2.4))
        assertEquals("0 m/min", HudFormat.verticalSpeed(0.0))
        assertEquals("+5 m/min", HudFormat.verticalSpeed(2.5))
        assertEquals("-5 m/min", HudFormat.verticalSpeed(-2.5))
        // ±999 で頭打ち
        assertEquals("+995 m/min", HudFormat.verticalSpeed(997.4))
        assertEquals("+999 m/min", HudFormat.verticalSpeed(997.5))
        assertEquals("+999 m/min", HudFormat.verticalSpeed(1_500.0))
        assertEquals("-999 m/min", HudFormat.verticalSpeed(-2_000.0))
        assertEquals("---", HudFormat.verticalSpeed(null))
        assertEquals("---", HudFormat.verticalSpeed(Double.NaN))
    }
}
