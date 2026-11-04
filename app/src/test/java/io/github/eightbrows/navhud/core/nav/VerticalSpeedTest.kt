package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.replay.ReplayPlayer
import io.github.eightbrows.navhud.core.view.HudFormat
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * V/S（昇降率。20261104-D03 で RATE を置き換えた）: 直前 60 秒の高さの変化 [m/min]。
 * 高さは ALT と同じ（Fix.mslAltM、なければ 楕円体高 − 標高オフセット）。60 秒分そろうまで・途中で途切れたとき・NO FIX は "---"。
 */
class VerticalSpeedTest {

    private val t0 = 1_786_665_600_000L
    private val jst = ZoneId.of("Asia/Tokyo")

    /** 1 秒ごと、北へ 10 m/s。高さ（楕円体高）は alt(秒) */
    private fun track(seconds: Int, alt: (Int) -> Double?): List<Fix> = (0..seconds).map { s ->
        Fix(timeMs = t0 + s * 1000L, lat = TestGeo.lat(10.0 * s, 33.0), lon = 133.0, altRawM = alt(s), speedMps = 10f, bearingDeg = 0f, horizAccM = 5f)
    }

    private fun engine(s: NavSettings = NavSettings()) = NavEngine(s, jst, SourceKind.REPLAY)

    private fun run(fixes: List<Fix>, e: NavEngine = engine()): NavState {
        fixes.forEach { e.onFix(it, it.timeMs) }
        return e.state
    }

    @Test
    fun metersPerMinuteWithSign() {
        // 60 秒で +42 m（上り）・−15 m（下り）・0 m（平ら）
        assertEquals(42.0, run(track(60) { 100.0 + 0.7 * it }).verticalSpeedMpm!!, 1e-9)
        assertEquals(-15.0, run(track(60) { 500.0 - 0.25 * it }).verticalSpeedMpm!!, 1e-9)
        assertEquals(0.0, run(track(60) { 300.0 }).verticalSpeedMpm!!, 1e-9)
        // 表示: 上りは +、下りは -、0 は符号なし
        assertEquals("+42 m/min", HudFormat.verticalSpeed(run(track(60) { 100.0 + 0.7 * it }).verticalSpeedMpm))
        assertEquals("-15 m/min", HudFormat.verticalSpeed(run(track(60) { 500.0 - 0.25 * it }).verticalSpeedMpm))
        assertEquals("0 m/min", HudFormat.verticalSpeed(run(track(60) { 300.0 }).verticalSpeedMpm))
        // 直前 60 秒だけを見る: 120 秒走って、前半は上り・後半は平らなら 0
        assertEquals(0.0, run(track(120) { if (it < 60) 100.0 + it else 160.0 }).verticalSpeedMpm!!, 1e-9)
        // 1 分あたりにする: 60 秒で +3 m なら +3 m/min（30 秒の変化の 2 倍ではない）
        assertEquals(3.0, run(track(60) { 0.05 * it }).verticalSpeedMpm!!, 1e-9)
    }

    @Test
    fun usesTheSameHeightAsAlt() {
        // 海抜高度（AltitudeConverter）があればそれ、なければ 楕円体高 − 標高オフセット（オフセットは差では消える）
        val withMsl = track(60) { 1000.0 }.mapIndexed { i, f -> f.copy(mslAltM = 200.0 + i) }
        assertEquals(60.0, run(withMsl).verticalSpeedMpm!!, 1e-9)
        assertEquals(30.0, run(track(60) { 50.0 + 0.5 * it }, engine(NavSettings(altOffsetM = 40.0))).verticalSpeedMpm!!, 1e-9)
        // 高さがない Fix が起点か最新なら出さない
        assertNull(run(track(60) { if (it == 0) null else 10.0 }).verticalSpeedMpm)
        assertNull(run(track(60) { if (it == 60) null else 10.0 }).verticalSpeedMpm)
    }

    @Test
    fun noneUntilSixtySecondsAreCollected() {
        val e = engine()
        val fixes = track(60) { 100.0 + it }
        for ((i, f) in fixes.withIndex()) {
            val s = e.onFix(f, f.timeMs)
            if (i < 60) assertNull("$i 秒", s.verticalSpeedMpm) else assertEquals(60.0, s.verticalSpeedMpm!!, 1e-9)
        }
        assertEquals("---", HudFormat.verticalSpeed(engine().onFix(fixes[0], t0).verticalSpeedMpm))
    }

    @Test
    fun noneAfterAGapUntilSixtySecondsAgain() {
        // 30〜45 秒は位置が来ない（15 秒 > 測位が途切れたとみなす時間 10 秒）: 途中で途切れたので、そこから 60 秒そろうまで "---"
        val fixes = track(130) { 100.0 + it }.filter { it.timeMs - t0 !in 31_000L..44_000L }
        val e = engine()
        val states = fixes.map { e.onFix(it, it.timeMs) }
        fun at(sec: Int) = states[fixes.indexOfFirst { it.timeMs == t0 + sec * 1000L }]
        assertNull(at(60).verticalSpeedMpm)
        assertNull(at(104).verticalSpeedMpm)
        assertEquals(60.0, at(105).verticalSpeedMpm!!, 1e-9)
        assertEquals(60.0, at(130).verticalSpeedMpm!!, 1e-9)
        // 「測位が途切れたとみなす時間」より短い抜け（5 秒）なら続けて出す
        val short = track(70) { 100.0 + it }.filter { it.timeMs - t0 !in 31_000L..34_000L }
        assertEquals(60.0, run(short).verticalSpeedMpm!!, 1e-9)
        // 設定を 3 秒にすると、5 秒の抜けも途切れたとみなす
        assertNull(run(short, engine(NavSettings(noFixTimeoutSec = 3))).verticalSpeedMpm)
    }

    @Test
    fun noneWhileNoFix() {
        val e = engine()
        run(track(60) { 100.0 + it }, e)
        assertNotNull(e.state.verticalSpeedMpm)
        // 最後の Fix から 10 秒を超えたら NO FIX: V/S も "---"
        assertNotNull(e.onTick(t0 + 70_000).verticalSpeedMpm)
        assertNull(e.onTick(t0 + 70_001).verticalSpeedMpm)
    }

    @Test
    fun replaySeekShowsTheValueRightAway() {
        // 0〜300 秒、100 秒までは上り（+1 m/s）、そのあとは下り（−0.5 m/s）
        val fixes = track(300) { if (it <= 100) 100.0 + it else 200.0 - 0.5 * (it - 100) }
        // 続けて再生したときの 200 秒の値
        val straight = run(fixes.take(201))
        assertEquals(-30.0, straight.verticalSpeedMpm!!, 1e-9)

        // 0 秒から 200 秒へ前方シーク（ViewModel と同じ: seekReset → シーク先の前の記録を入れる → シーク先の Fix）
        val e = engine()
        val p = ReplayPlayer(fixes)
        p.due(t0).forEach { e.onFix(it, it.timeMs) }
        val r = p.seek(t0 + 200_000)
        e.seekReset(toStart = false, passed = r.passed)
        e.primeVerticalSpeed(r.before)
        val s = e.onFix(r.last!!, r.last!!.timeMs)
        assertEquals(straight.verticalSpeedMpm!!, s.verticalSpeedMpm!!, 1e-9)

        // 後方へ（80 秒）: 80 秒の時点の上りの値（20〜80 秒）がすぐ出る
        val back = p.seek(t0 + 80_000)
        e.seekReset(toStart = false, passed = back.passed)
        e.primeVerticalSpeed(back.before)
        assertEquals(60.0, e.onFix(back.last!!, back.last!!.timeMs).verticalSpeedMpm!!, 1e-9)

        // 先頭から 30 秒へ: 60 秒分の記録がないので "---"
        val early = p.seek(t0 + 30_000)
        e.seekReset(toStart = true, passed = early.passed)
        e.primeVerticalSpeed(early.before)
        assertNull(e.onFix(early.last!!, early.last!!.timeMs).verticalSpeedMpm)

        // before はシーク先の Fix を含まず、60 秒の窓＋余裕（2 秒）の分
        val r2 = p.seek(t0 + 200_000)
        assertEquals(t0 + 138_000, r2.before.first().timeMs)
        assertEquals(t0 + 199_000, r2.before.last().timeMs)
    }

    @Test
    fun independentOfReplaySpeed() {
        // 記録の時刻で測るので、倍速で Fix がまとめて来ても同じ値（onFixes は倍速のときの入れ方）
        val fixes = track(120) { 100.0 + 0.3 * it }
        val one = run(fixes)
        val e = engine()
        fixes.chunked(30).forEach { e.onFixes(it, it.last().timeMs) }
        assertEquals(one.verticalSpeedMpm!!, e.state.verticalSpeedMpm!!, 1e-9)
        assertEquals(18.0, one.verticalSpeedMpm!!, 1e-9)
    }
}
