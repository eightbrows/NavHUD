package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingSelectorTest {

    private var t = 0L

    private fun fix(speed: Float? = 10f, bearing: Float? = 90f, acc: Float? = 5f, bearingAcc: Float? = 3f) =
        Fix(timeMs = (t++) * 1000, lat = 33.5, lon = 133.0, speedMps = speed, bearingDeg = bearing, horizAccM = acc, bearingAccDeg = bearingAcc)

    /** 位置から求めた向き（TrackHeading.deg の代わり） */
    private val track = 45f

    private fun HeadingSelector.feed(vararg f: Fix) = f.forEach(::update)

    @Test
    fun movingUsesLiveGps() {
        val s = HeadingSelector()
        s.feed(fix(speed = 10f, bearing = 90f))
        assertEquals(Heading(90f, HeadingSrc.GPS), s.select(HeadingMethod.GPS, false, null))
        // GPS ＋ 位置でも、GPS の方位がそのまま使えるときは GPS
        assertEquals(Heading(90f, HeadingSrc.GPS), s.select(HeadingMethod.GPS_TRACK, false, track))
    }

    @Test
    fun slowingDownHoldsLastStableBearingNotTheWobblyOne() {
        val s = HeadingSelector()
        // 走行中 90°、減速中（3.0 未満）に 110° / 130° とふらつき、停止で 250° のような変な値
        s.feed(fix(10f, 90f), fix(5f, 92f), fix(2.5f, 110f), fix(2.2f, 130f), fix(1.0f, 200f), fix(0f, 250f, bearingAcc = 60f))
        val h = s.select(HeadingMethod.GPS, false, null)
        assertTrue(s.holding)
        assertEquals(Heading(92f, HeadingSrc.GPS, held = true), h)
    }

    @Test
    fun betweenThresholdsBeforeHoldingStillUsesLiveGps() {
        // 3.0 以上から 2.0〜3.0 に落ちただけでは保持しない（表示は今の GPS 方位、ただし安定値は更新しない）
        val s = HeadingSelector()
        s.feed(fix(10f, 90f), fix(2.5f, 100f))
        assertFalse(s.holding)
        assertEquals(Heading(100f, HeadingSrc.GPS), s.select(HeadingMethod.GPS, false, null))
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
        assertEquals(Heading(90f, HeadingSrc.GPS, held = true), s.select(HeadingMethod.GPS, false, null))
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
        assertEquals(Heading(95f, HeadingSrc.GPS), s.select(HeadingMethod.GPS, false, null))
        // 使える Fix が来たら、その値に戻る
        s.update(fix(3.5f, 100f))
        assertEquals(Heading(100f, HeadingSrc.GPS), s.select(HeadingMethod.GPS, false, null))
    }

    @Test
    fun noFixDoesNotUseLiveGps() {
        val s = HeadingSelector()
        s.feed(fix(10f, 90f))
        assertEquals(Heading(90f, HeadingSrc.GPS, held = true), s.select(HeadingMethod.GPS, true, null))
        // GPS ＋ 位置: NO FIX 中は GPS の方位を「そのまま使える」としないので、位置から求めた向き
        assertEquals(Heading(track, HeadingSrc.TRACK), s.select(HeadingMethod.GPS_TRACK, true, track))
    }

    @Test
    fun noInput() {
        val s = HeadingSelector()
        for (m in HeadingMethod.entries) assertEquals(m.name, Heading.NONE, s.select(m, false, null))
        // GPS だけなら、位置から求めた向きがあっても使わない
        assertEquals(Heading.NONE, s.select(HeadingMethod.GPS, false, track))
        assertEquals(Heading(track, HeadingSrc.TRACK), s.select(HeadingMethod.GPS_TRACK, false, track))
    }

    /** 20261121-D01: GPS ＋ 位置。GPS の方位が使えないとき（保持の間・方位精度が上限を超えて前の値のまま）は、位置から求めた向き（TRK） */
    @Test
    fun gpsTrackUsesTrackWhenGpsIsNotFresh() {
        // 保持の間
        val held = HeadingSelector()
        held.feed(fix(10f, 90f), fix(0f, null, bearingAcc = null))
        assertTrue(held.holding)
        assertFalse(held.gpsFresh)
        assertEquals(Heading(track, HeadingSrc.TRACK), held.select(HeadingMethod.GPS_TRACK, false, track))
        assertEquals(Heading(90f, HeadingSrc.GPS, held = true), held.select(HeadingMethod.GPS, false, track))
        // 位置から求めた向きがまだなければ、GPS と同じ（HLD）
        assertEquals(Heading(90f, HeadingSrc.GPS, held = true), held.select(HeadingMethod.GPS_TRACK, false, null))
        // 走っているが方位精度が上限を超えた（前の値のまま）
        val stale = HeadingSelector()
        stale.feed(fix(10f, 90f), fix(10f, 120f, bearingAcc = 40f))
        assertFalse(stale.holding)
        assertFalse(stale.gpsFresh)
        assertEquals(Heading(90f, HeadingSrc.GPS), stale.select(HeadingMethod.GPS, false, track))
        assertEquals(Heading(track, HeadingSrc.TRACK), stale.select(HeadingMethod.GPS_TRACK, false, track))
        // 次の点で方位が使えれば、すぐ GPS に戻る
        stale.feed(fix(10f, 100f))
        assertTrue(stale.gpsFresh)
        assertEquals(Heading(100f, HeadingSrc.GPS), stale.select(HeadingMethod.GPS_TRACK, false, track))
        // 保持を解く速度を超え、方位が使える点が来るまでは TRK のまま（保持を解く決まりは GPS と同じ）
        held.feed(fix(10f, 80f, bearingAcc = 40f))
        assertEquals(Heading(track, HeadingSrc.TRACK), held.select(HeadingMethod.GPS_TRACK, false, track))
        held.feed(fix(10f, 80f))
        assertEquals(Heading(80f, HeadingSrc.GPS), held.select(HeadingMethod.GPS_TRACK, false, track))
        // reset で GPS の方位をそのまま使えた印も消える
        held.reset()
        assertFalse(held.gpsFresh)
    }

    @Test
    fun speedUnknownDeviceUsesBearing() {
        val s = HeadingSelector()
        s.feed(fix(speed = null, bearing = 45f, bearingAcc = null))
        assertFalse(s.holding)
        assertEquals(Heading(45f, HeadingSrc.GPS), s.select(HeadingMethod.GPS, false, null))
        assertEquals(45f, s.stableGpsDeg)
    }

    @Test
    fun selectDoesNotChangeState() {
        val s = HeadingSelector()
        s.feed(fix(10f, 90f), fix(2.5f, 100f))
        repeat(5) { s.select(HeadingMethod.GPS_TRACK, false, track) }
        assertFalse(s.holding)
        assertEquals(100f, s.liveGpsDeg)
    }

    /** 20261121-D04: 位置だけ。GPS の方位は使わず、いつも位置から求めた向き。まだなければ GPS ＋ 位置で向きがまだないときと同じ */
    @Test
    fun trackOnlyAlwaysUsesTrack() {
        val s = HeadingSelector()
        // GPS の方位がそのまま使えるときも TRK
        s.feed(fix(10f, 90f))
        assertTrue(s.gpsFresh)
        assertEquals(Heading(track, HeadingSrc.TRACK), s.select(HeadingMethod.TRACK_ONLY, false, track))
        // 位置から求めた向きがまだなければ、GPS ＋ 位置と同じ（GPS の値・保持の値）
        assertEquals(s.select(HeadingMethod.GPS_TRACK, false, null), s.select(HeadingMethod.TRACK_ONLY, false, null))
        assertEquals(Heading(90f, HeadingSrc.GPS), s.select(HeadingMethod.TRACK_ONLY, false, null))
        s.feed(fix(0f, null, bearingAcc = null))
        assertEquals(Heading(90f, HeadingSrc.GPS, held = true), s.select(HeadingMethod.TRACK_ONLY, false, null))
        assertEquals(Heading(track, HeadingSrc.TRACK), s.select(HeadingMethod.TRACK_ONLY, true, track))
        assertEquals(Heading.NONE, HeadingSelector().select(HeadingMethod.TRACK_ONLY, false, null))
    }
}
