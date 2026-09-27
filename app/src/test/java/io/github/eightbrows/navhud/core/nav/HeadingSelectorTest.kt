package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.model.SourceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingSelectorTest {

    private var t = 0L

    private fun fix(speed: Float? = 10f, bearing: Float? = 90f, acc: Float? = 5f, bearingAcc: Float? = 3f) =
        Fix(timeMs = (t++) * 1000, lat = 33.5, lon = 133.0, speedMps = speed, bearingDeg = bearing, horizAccM = acc, bearingAccDeg = bearingAcc)

    private val compass = 45f

    private fun HeadingSelector.feed(vararg f: Fix) = f.forEach(::update)

    @Test
    fun movingUsesLiveGps() {
        val s = HeadingSelector()
        s.feed(fix(speed = 10f, bearing = 90f))
        assertEquals(Heading(90f, HeadingSrc.GPS), s.select(SourceMode.GPS, false, null))
        assertEquals(Heading(90f, HeadingSrc.GPS), s.select(SourceMode.HYBRID, false, compass))
    }

    @Test
    fun slowingDownHoldsLastStableBearingNotTheWobblyOne() {
        val s = HeadingSelector()
        // 走行中 90°、減速中（3.0 未満）に 110° / 130° とふらつき、停止で 250° のような変な値
        s.feed(fix(10f, 90f), fix(5f, 92f), fix(2.5f, 110f), fix(2.2f, 130f), fix(1.0f, 200f), fix(0f, 250f, bearingAcc = 60f))
        val h = s.select(SourceMode.GPS, false, null)
        assertTrue(s.holding)
        assertEquals(Heading(92f, HeadingSrc.GPS, held = true), h)
    }

    @Test
    fun betweenThresholdsBeforeHoldingStillUsesLiveGps() {
        // 3.0 以上から 2.0〜3.0 に落ちただけでは保持しない（表示は今の GPS 方位、ただし安定値は更新しない）
        val s = HeadingSelector()
        s.feed(fix(10f, 90f), fix(2.5f, 100f))
        assertFalse(s.holding)
        assertEquals(Heading(100f, HeadingSrc.GPS), s.select(SourceMode.GPS, false, null))
        assertEquals(90f, s.stableGpsDeg)
    }

    @Test
    fun hysteresisDoesNotChatter() {
        val s = HeadingSelector()
        s.feed(fix(10f, 90f), fix(1.5f, 90f))
        assertTrue(s.holding)
        // 2.0〜3.0 の間を行ったり来たりしても、保持のまま
        repeat(20) { i -> s.update(fix(if (i % 2 == 0) 2.1f else 2.9f, 90f)) ; assertTrue(s.holding) }
        // 3.0 ちょうどでは解除しない（超えたら解除）
        s.update(fix(3.0f, 90f))
        assertTrue(s.holding)
        s.update(fix(3.1f, 95f))
        assertFalse(s.holding)
        // 解除後、2.0〜3.0 の間を行ったり来たりしても GPS のまま
        repeat(20) { i -> s.update(fix(if (i % 2 == 0) 2.1f else 2.9f, 95f)); assertFalse(s.holding) }
    }

    @Test
    fun holdDoesNotReleaseWithBadBearing() {
        val s = HeadingSelector()
        s.feed(fix(10f, 90f), fix(0f, null, bearingAcc = null))
        // 速いが方位の精度が悪い → 保持のまま
        s.update(fix(5f, 180f, bearingAcc = 45f))
        assertTrue(s.holding)
        assertEquals(Heading(90f, HeadingSrc.GPS, held = true), s.select(SourceMode.GPS, false, null))
    }

    @Test
    fun bearingAccuracyLimitOnlyWhenReported() {
        val s = HeadingSelector()
        assertTrue(s.isGpsUsable(fix(bearingAcc = 20f)))
        assertFalse(s.isGpsUsable(fix(bearingAcc = 20.1f)))
        assertTrue(s.isGpsUsable(fix(bearingAcc = null)))
        assertFalse(s.isGpsUsable(fix(acc = 16f)))
        assertFalse(s.isGpsUsable(fix(bearing = null)))
    }

    @Test
    fun movingWithBadBearingAccuracyKeepsLastLiveWithoutHld() {
        // 走行中に精度の悪い Fix が混ざっても、HLD にせず直前の使えた方位のまま（表示が入れ替わり続けない）
        val s = HeadingSelector()
        s.feed(fix(10f, 90f), fix(3.5f, 95f), fix(3.5f, 200f, bearingAcc = 40f))
        assertFalse(s.holding)
        assertEquals(Heading(95f, HeadingSrc.GPS), s.select(SourceMode.GPS, false, null))
        // 使える Fix が来たら、その値に戻る
        s.update(fix(3.5f, 100f))
        assertEquals(Heading(100f, HeadingSrc.GPS), s.select(SourceMode.GPS, false, null))
    }

    @Test
    fun hybridUsesCompassWhileHoldingUnlessCal() {
        val s = HeadingSelector()
        s.feed(fix(10f, 90f), fix(0f, null, bearingAcc = null))
        assertEquals(Heading(compass, HeadingSrc.COMPASS), s.select(SourceMode.HYBRID, false, compass, compassLowAccuracy = false))
        // コンパスが CAL なら保持した GPS 方位
        assertEquals(Heading(90f, HeadingSrc.GPS, held = true), s.select(SourceMode.HYBRID, false, compass, compassLowAccuracy = true))
        // 保持値もなければ CAL のコンパスでも使う
        val fresh = HeadingSelector()
        fresh.feed(fix(0f, null, bearingAcc = null))
        assertEquals(Heading(compass, HeadingSrc.COMPASS), fresh.select(SourceMode.HYBRID, false, compass, compassLowAccuracy = true))
    }

    @Test
    fun noFixDoesNotUseLiveGps() {
        val s = HeadingSelector()
        s.feed(fix(10f, 90f))
        assertEquals(Heading(90f, HeadingSrc.GPS, held = true), s.select(SourceMode.GPS, true, null))
        assertEquals(Heading(compass, HeadingSrc.COMPASS), s.select(SourceMode.HYBRID, true, compass))
    }

    @Test
    fun acceptanceHybridLowSpeedLowAccuracyOrNoBearingUsesCompass() {
        // §9: HYBRID で低速 / 低精度 / 方位 null → COMPASS（直前に使える GPS 方位がない場合）
        val cases = listOf(fix(speed = 1.0f), fix(acc = 20f), fix(speed = 0f, bearing = null, bearingAcc = null))
        for (f in cases) {
            val s = HeadingSelector()
            s.update(f)
            assertEquals(f.toString(), Heading(compass, HeadingSrc.COMPASS), s.select(SourceMode.HYBRID, false, compass))
        }
    }

    @Test
    fun compassModeAndNoInput() {
        val s = HeadingSelector()
        assertEquals(Heading(compass, HeadingSrc.COMPASS), s.select(SourceMode.COMPASS, false, compass))
        for (mode in SourceMode.entries) assertEquals(mode.name, Heading.NONE, s.select(mode, false, null))
    }

    @Test
    fun speedUnknownDeviceUsesBearing() {
        val s = HeadingSelector()
        s.feed(fix(speed = null, bearing = 45f, bearingAcc = null))
        assertFalse(s.holding)
        assertEquals(Heading(45f, HeadingSrc.GPS), s.select(SourceMode.GPS, false, null))
        assertEquals(45f, s.stableGpsDeg)
    }

    @Test
    fun selectDoesNotChangeState() {
        val s = HeadingSelector()
        s.feed(fix(10f, 90f), fix(2.5f, 100f))
        repeat(5) { s.select(SourceMode.HYBRID, false, compass) }
        assertFalse(s.holding)
        assertEquals(100f, s.liveGpsDeg)
    }
}
