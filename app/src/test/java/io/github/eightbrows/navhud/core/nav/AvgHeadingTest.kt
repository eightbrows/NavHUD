package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 平均した方位（20261122-D03）: ベクトルで足す・動いた距離の重み・止まり・窓・欠け */
class AvgHeadingTest {

    private val t0 = 1_800_000_000_000L
    private val gap = 10_000L

    /** 北へ distM [m] ずつ進む点（速さは distM m/s、1 秒ごと）。位置は重みに使わない（速さの台形で数える） */
    private fun fix(i: Int, speed: Float) = Fix(timeMs = t0 + i * 1000L, lat = 35.0, lon = 135.0, speedMps = speed)

    private fun ang(a: Float, b: Double) = ((a - b + 540.0) % 360.0) - 180.0

    @Test
    fun crossesZeroWithVectors() {
        // 350° と 10° を同じ重みで → 0°（角度をそのまま足すと 180° になる）
        val a = AvgHeading()
        a.update(fix(0, 1.5f), 350f, 5, gap)
        a.update(fix(1, 1.5f), 350f, 5, gap)
        a.update(fix(2, 1.5f), 10f, 5, gap)
        assertEquals(0.0, ang(a.deg!!, 0.0), 0.01)
    }

    @Test
    fun weightsByDistance() {
        // 90° で 10 m（速さ 10 m/s で 1 秒）、0° で 2 m（2 m/s で 1 秒）→ atan2(10, 2) ≈ 78.7°
        val a = AvgHeading()
        a.update(fix(0, 10f), null, 10, gap)
        a.update(fix(1, 10f), 90f, 10, gap)
        a.update(fix(2, 2f), 0f, 10, gap)
        // 2 点目の区間の速さは (10 + 2) / 2 = 6 m → 90° 10 m・0° 6 m
        assertEquals(Math.toDegrees(Math.atan2(10.0, 6.0)), a.deg!!.toDouble(), 0.01)
    }

    @Test
    fun stopsDoNotPullTheAverage() {
        // 東へ歩いたあと止まって向きが北に変わっても（止まっている点は重み 0）、平均は東のまま。窓を過ぎても前の向きを保つ
        val a = AvgHeading()
        for (i in 0..5) a.update(fix(i, 1.5f), 90f, 5, gap)
        assertEquals(90.0, a.deg!!.toDouble(), 0.01)
        // 止まる点（i = 6）までは減速しながら東へ動いた分の重みがある。そのあとは止まっている（速さ 0・重み 0）ので、向きが北に変わっても数えない
        a.update(fix(6, 0f), 90f, 5, gap)
        for (i in 7..20) a.update(fix(i, 0f), 0f, 5, gap)
        assertEquals(90.0, a.deg!!.toDouble(), 0.01)
        // 歩き出すと、また今の向きの平均
        for (i in 21..30) a.update(fix(i, 1.5f), 180f, 5, gap)
        assertEquals(180.0, a.deg!!.toDouble(), 0.01)
    }

    @Test
    fun windowDropsOldPoints() {
        // 5 秒の窓: 東へ 10 秒歩いてから北へ 5 秒 → 北だけ
        val a = AvgHeading()
        for (i in 0..9) a.update(fix(i, 1.5f), 90f, 5, gap)
        for (i in 10..15) a.update(fix(i, 1.5f), 0f, 5, gap)
        assertEquals(0.0, ang(a.deg!!, 0.0), 0.01)
        // 10 秒の窓なら東と北の間
        val b = AvgHeading()
        for (i in 0..9) b.update(fix(i, 1.5f), 90f, 10, gap)
        for (i in 10..15) b.update(fix(i, 1.5f), 0f, 10, gap)
        assertEquals(true, b.deg!! in 10f..80f)
    }

    @Test
    fun tooLittleMovementOrBackAndForthKeepsThePreviousDirection() {
        val a = AvgHeading()
        // 最初から動いていない（合計 2 m 未満）→ まだない
        a.update(fix(0, 0.5f), 90f, 5, gap)
        a.update(fix(1, 0.5f), 90f, 5, gap)
        assertNull(a.deg)
        for (i in 2..6) a.update(fix(i, 1.5f), 90f, 5, gap)
        // 行ったり来たり（東と西を同じだけ）は打ち消し合うので、前の向き（東）のまま
        for (i in 7..12) a.update(fix(i, 1.5f), if (i % 2 == 0) 90f else 270f, 5, gap)
        assertEquals(90.0, a.deg!!.toDouble(), 0.01)
    }

    @Test
    fun gapsAndTimeGoingBackReset() {
        val a = AvgHeading()
        for (i in 0..5) a.update(fix(i, 1.5f), 90f, 5, gap)
        // 測位の欠け（10 秒を超える）→ 捨てる
        a.update(fix(20, 1.5f), 0f, 5, gap)
        assertNull(a.deg)
        // 時刻が戻ったら捨てる
        for (i in 21..25) a.update(fix(i, 1.5f), 0f, 5, gap)
        assertEquals(0.0, ang(a.deg!!, 0.0), 0.01)
        a.update(fix(3, 1.5f), 90f, 5, gap)
        assertNull(a.deg)
    }

    /** エンジン: 歩行（オン・5 秒）で東へ歩くと平均した方位は東。NO FIX では出さない。設定がオフでも計算はする（描くかは画面） */
    @Test
    fun engineAverageFollowsTheCurrentHeading() {
        val s = NavSettings().selectTravelMode(TravelMode.WALK)
        val e = NavEngine(s, java.time.ZoneId.of("Asia/Tokyo"), SourceKind.REPLAY)
        var st: NavState? = null
        for (i in 0..20) {
            val f = Fix(timeMs = t0 + i * 1000L, lat = 33.0, lon = TestGeo.lon(1.5 * i, 33.0, 133.0), speedMps = 1.5f, horizAccM = 4f)
            st = e.onFix(f, f.timeMs)
        }
        assertEquals(90.0, st!!.avgHeadingDeg!!.toDouble(), 1.0)
        assertEquals(HeadingSrc.TRACK, st.heading.src)
        // NO FIX（10 秒より先）では出さない
        assertNull(e.onTick(t0 + 40_000L).avgHeadingDeg)
    }
}
