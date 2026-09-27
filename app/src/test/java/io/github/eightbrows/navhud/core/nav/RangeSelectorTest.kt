package io.github.eightbrows.navhud.core.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeSelectorTest {

    private val stepsM = listOf(500.0, 1_000.0, 2_000.0, 5_000.0, 10_000.0)

    @Test
    fun desiredIsSmallestStepThatFits() {
        // 縮尺 × 0.9 以内に収まる最小の段
        assertEquals(500.0, RangeAuto.desired(stepsM, 450.0, null)!!, 0.0)
        assertEquals(1_000.0, RangeAuto.desired(stepsM, 451.0, null)!!, 0.0)
        assertEquals(1_000.0, RangeAuto.desired(stepsM, 900.0, 10f)!!, 0.0)
        assertEquals(2_000.0, RangeAuto.desired(stepsM, 901.0, 10f)!!, 0.0)
        assertEquals(5_000.0, RangeAuto.desired(stepsM, 3_900.0, 10f)!!, 0.0)
        // 最大の段より遠ければ最大の段
        assertEquals(10_000.0, RangeAuto.desired(stepsM, 50_000.0, 10f)!!, 0.0)
        // 次の WP がなければ判定しない
        assertNull(RangeAuto.desired(stepsM, null, 10f))
    }

    @Test
    fun speedIsAcceptedButNotUsedYet() {
        assertEquals(RangeAuto.desired(stepsM, 700.0, 0f), RangeAuto.desired(stepsM, 700.0, 40f))
    }

    @Test
    fun initialStepAndSnapping() {
        assertEquals(1_000.0, RangeSelector(listOf(0.5, 1.0, 2.0), 1.0, auto = true).rangeM, 0.0)
        // 有効リストにない起動時の縮尺は近い段へ
        assertEquals(2_000.0, RangeSelector(listOf(0.5, 2.0, 5.0), 1.5, auto = true).rangeM, 0.0)
    }

    @Test
    fun zoomOutIsImmediateZoomInWaitsFiveSeconds() {
        val r = RangeSelector(listOf(0.5, 1.0, 2.0, 5.0, 10.0), 1.0, auto = true)
        // 遠くなった → すぐ広げる
        assertEquals(5_000.0, r.update(3_900.0, 10f, 0), 0.0)
        // 近くなった → 5 秒続くまで狭めない
        assertEquals(5_000.0, r.update(400.0, 10f, 1_000), 0.0)
        assertEquals(5_000.0, r.update(400.0, 10f, 5_999), 0.0)
        assertEquals(500.0, r.update(400.0, 10f, 6_000), 0.0)
    }

    @Test
    fun zoomInWaitIsCancelledWhenConditionBreaks() {
        val r = RangeSelector(listOf(0.5, 1.0, 2.0, 5.0), 5.0, auto = true)
        r.update(400.0, 10f, 0)
        r.update(400.0, 10f, 4_000)
        // 一度でも今の段が必要になったら、待ちはやり直し
        r.update(4_000.0, 10f, 4_500)
        assertEquals(5_000.0, r.update(400.0, 10f, 5_000), 0.0)
        assertEquals(5_000.0, r.update(400.0, 10f, 9_999), 0.0)
        assertEquals(500.0, r.update(400.0, 10f, 10_000), 0.0)
    }

    @Test
    fun noChatterAtBoundary() {
        // 境目（1km 段の 0.9 = 900m）付近を行き来しても、狭める方向は 5 秒待つので頻繁に切り替わらない
        val r = RangeSelector(listOf(0.5, 1.0, 2.0), 1.0, auto = true)
        var changes = 0
        var last = r.rangeM
        for (s in 0 until 60) {
            val d = if (s % 2 == 0) 899.0 else 901.0
            val now = r.update(d, 5f, s * 1_000L)
            if (now != last) changes++
            last = now
        }
        // 901m で 2km に広げたあとは、899m が 5 秒続かないので 1km に戻らない
        assertEquals(1, changes)
        assertEquals(2_000.0, last, 0.0)
    }

    @Test
    fun manualZoomTurnsAutoOff() {
        val r = RangeSelector(listOf(0.5, 1.0, 2.0, 5.0, 10.0), 1.0, auto = true)
        r.zoomOut()
        assertFalse(r.auto)
        assertEquals(2_000.0, r.rangeM, 0.0)
        // AUTO OFF では距離が変わっても縮尺はそのまま
        assertEquals(2_000.0, r.update(8_000.0, 10f, 0), 0.0)
        r.zoomIn()
        r.zoomIn()
        assertEquals(500.0, r.rangeM, 0.0)
        // 端の段より先へは行かない
        r.zoomIn()
        assertEquals(500.0, r.rangeM, 0.0)
        r.setAuto(true)
        assertTrue(r.auto)
        assertEquals(10_000.0, r.update(20_000.0, 10f, 0), 0.0)
        r.zoomOut()
        assertEquals(10_000.0, r.rangeM, 0.0)
    }

    @Test
    fun noWaypointKeepsCurrent() {
        val r = RangeSelector(listOf(0.5, 1.0, 2.0), 1.0, auto = true)
        assertEquals(1_000.0, r.update(null, 10f, 0), 0.0)
        assertEquals(1_000.0, r.update(null, 10f, 60_000), 0.0)
    }

    @Test
    fun timeGoingBackwardsRestartsTheWait() {
        val r = RangeSelector(listOf(0.5, 1.0, 5.0), 5.0, auto = true)
        r.update(400.0, 10f, 100_000)
        // リプレイの巻き戻しなどで時刻が戻っても、すぐには狭めない
        assertEquals(5_000.0, r.update(400.0, 10f, 1_000), 0.0)
        assertEquals(500.0, r.update(400.0, 10f, 6_000), 0.0)
    }

    @Test
    fun allStepsList() {
        assertEquals(listOf(0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0), RangeAuto.ALL_STEPS_KM)
    }
}
