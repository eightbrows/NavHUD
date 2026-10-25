package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.view.HudInsets
import io.github.eightbrows.navhud.core.view.HudRect
import io.github.eightbrows.navhud.core.view.HudViewport
import io.github.eightbrows.navhud.core.view.TouchPoint
import io.github.eightbrows.navhud.core.view.TwoFingerTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * 地図の 2 本指のタップ（20261024-D09）: − を 1 回押したのと同じ。いちばん広域の段では何もしない。
 * タップの見分け方は core.view.TwoFingerTap（指がちょうど 2 本・どちらもほとんど動かない・300 ms 以内・ピンチで段が動いていない）。
 */
class TwoFingerTapZoomTest {

    private val lat0 = TestGeo.LAT0
    private val lon0 = TestGeo.LON0
    private val viewport = HudViewport(HudRect(0f, 0f, 720f, 690f), reserved = HudInsets(right = 102f, bottom = 81.6f))

    private fun engine(s: NavSettings = NavSettings(initialRangeKm = 2.0, autoRange = false)) =
        NavEngine(s, ZoneId.of("Asia/Tokyo"), SourceKind.LIVE).apply { setViewport(viewport) }

    private fun fix(t: Long) = Fix(timeMs = t, lat = lat0, lon = lon0)

    // ---- 縮尺（NavEngine） ----

    @Test
    fun sameAsTheMinusButton() {
        // AUTO が ON・PAN でない: − と同じに 1 段広域になり、AUTO は OFF。PAN にはしない
        val tap = engine(NavSettings(initialRangeKm = 2.0)).apply { onFix(fix(0), 0) }
        val minus = engine(NavSettings(initialRangeKm = 2.0)).apply { onFix(fix(0), 0) }
        assertEquals(true, tap.state.rangeAuto)
        val a = tap.zoomOutByTwoFingerTap()
        val b = minus.zoomOut()
        assertEquals(b.rangeM, a.rangeM, 0.0)
        assertTrue(a.rangeM > 200.0)
        assertEquals(false, a.rangeAuto)
        assertEquals(b.rangeAuto, a.rangeAuto)
        assertNull(a.pan)
    }

    @Test
    fun keepsAutoAndThePanWhilePanning() {
        // PAN 中: − と同じに AUTO はそのまま、PAN のまま（中心も変えない）
        val e = engine(NavSettings(initialRangeKm = 2.0)).apply { onFix(fix(0), 0) }
        e.panBy(-80f, 30f)
        val pan = e.state.pan
        assertNotNull(pan)
        val before = e.state.rangeM
        val s = e.zoomOutByTwoFingerTap()
        assertTrue(s.rangeM > before)
        assertEquals(true, s.rangeAuto)
        assertEquals(pan!!.lat, s.pan!!.lat, 0.0)
        assertEquals(pan.lon, s.pan!!.lon, 0.0)
    }

    @Test
    fun doesNothingAtTheWidestStep() {
        // 使う段が R1 250 m・500 m・1 km で、起動時の縮尺がいちばん広域（2 km = R1 1 km）。AUTO は ON
        val e = engine(NavSettings(initialRangeKm = 2.0, rangeStepsKm = listOf(0.5, 1.0, 2.0)))
        val top = e.state
        assertEquals(2_000.0, top.rangeM, 0.0)
        assertEquals(true, top.rangeAuto)
        // いちばん広域の段: 何もしない（同じ NavState を返し、AUTO も OFF にしない）
        assertSame(top, e.zoomOutByTwoFingerTap())
        assertEquals(true, e.state.rangeAuto)
        // − はいちばん広域の段でも AUTO を OFF にする（2 本指のタップとの違い）
        assertEquals(false, e.zoomOut().rangeAuto)
        assertEquals(2_000.0, e.state.rangeM, 0.0)
        // PAN 中でも、いちばん広域では何もしない
        val pan = engine(NavSettings(initialRangeKm = 2.0, rangeStepsKm = listOf(0.5, 1.0, 2.0))).apply { onFix(fix(0), 0); panBy(-10f, 0f) }
        repeat(3) { pan.zoomOut() }
        val panTop = pan.state
        assertSame(panTop, pan.zoomOutByTwoFingerTap())
    }
    @Test
    fun countsAsAChangedRangeWhileIdle() {
        // 位置がないあいだ: ほかの地図の操作と同じに使える。AUTO が OFF なら、変えた段は位置が来てもそのまま（D08）
        val e = engine()
        assertEquals(20_000.0, e.setIdleCenter(lat0, lon0).rangeM, 0.0)
        assertEquals(50_000.0, e.zoomOutByTwoFingerTap().rangeM, 0.0)
        assertEquals(50_000.0, e.onFix(fix(0), 0).rangeM, 0.0)
        // 変えていなければ起動したときの段（2 km）
        val none = engine().apply { setIdleCenter(lat0, lon0) }
        assertEquals(2_000.0, none.onFix(fix(0), 0).rangeM, 0.0)
    }

    // ---- 2 本指のタップの見分け方（TwoFingerTap） ----

    private fun p(id: Long, x: Float, y: Float, down: Boolean = true) = TouchPoint(id, x, y, down)

    /** 2 本の指を置いて、少し動かして（dx）、t [ms] で離す */
    private fun twoFinger(tap: TwoFingerTap, dx: Float = 0f, upMs: Long = 150) {
        tap.onEvent(0, listOf(p(1, 100f, 100f)))
        tap.onEvent(30, listOf(p(1, 100f, 100f), p(2, 300f, 120f)))
        tap.onEvent(80, listOf(p(1, 100f + dx, 100f), p(2, 300f, 120f)))
        tap.onEvent(upMs, listOf(p(1, 100f + dx, 100f, false), p(2, 300f, 120f, false)))
    }

    @Test
    fun twoFingersThatBarelyMoveAndLiftQuicklyAreATap() {
        val tap = TwoFingerTap(slopPx = 16f)
        twoFinger(tap, dx = 10f)
        assertTrue(tap.isTap)
        // 1 本ずつ離しても同じ（300 ms 以内）
        val one = TwoFingerTap(slopPx = 16f)
        one.onEvent(0, listOf(p(1, 100f, 100f), p(2, 300f, 120f)))
        one.onEvent(120, listOf(p(1, 100f, 100f, false), p(2, 300f, 120f)))
        assertFalse(one.isTap)   // まだ 1 本置いている
        one.onEvent(200, listOf(p(2, 300f, 120f, false)))
        assertTrue(one.isTap)
    }

    @Test
    fun movedFingersAreNotATap() {
        // どちらかの指が、タッチのずれの許し方より動いた（ドラッグ・ピンチの始まり）
        val tap = TwoFingerTap(slopPx = 16f)
        twoFinger(tap, dx = 40f)
        assertFalse(tap.isTap)
    }

    @Test
    fun slowOrPinchedOrOtherFingerCountsAreNotATap() {
        // 300 ms より長く置いていた
        val slow = TwoFingerTap(slopPx = 16f)
        twoFinger(slow, upMs = 301)
        assertFalse(slow.isTap)
        val justIn = TwoFingerTap(slopPx = 16f)
        twoFinger(justIn, upMs = 300)
        assertTrue(justIn.isTap)
        // ピンチで段が動いた
        val pinched = TwoFingerTap(slopPx = 16f)
        pinched.onEvent(0, listOf(p(1, 100f, 100f), p(2, 300f, 120f)))
        pinched.onPinchStep()
        pinched.onEvent(100, listOf(p(1, 100f, 100f, false), p(2, 300f, 120f, false)))
        assertFalse(pinched.isTap)
        // 1 本指のタップ
        val one = TwoFingerTap(slopPx = 16f)
        one.onEvent(0, listOf(p(1, 100f, 100f)))
        one.onEvent(100, listOf(p(1, 100f, 100f, false)))
        assertFalse(one.isTap)
        // 3 本になった
        val three = TwoFingerTap(slopPx = 16f)
        three.onEvent(0, listOf(p(1, 100f, 100f), p(2, 300f, 120f)))
        three.onEvent(40, listOf(p(1, 100f, 100f), p(2, 300f, 120f), p(3, 200f, 300f)))
        three.onEvent(100, listOf(p(1, 100f, 100f, false), p(2, 300f, 120f, false), p(3, 200f, 300f, false)))
        assertFalse(three.isTap)
        // 2 本置いて 1 本離し、別の指を置いた（同時に 2 本だが、指は 3 本）
        val swap = TwoFingerTap(slopPx = 16f)
        swap.onEvent(0, listOf(p(1, 100f, 100f), p(2, 300f, 120f)))
        swap.onEvent(40, listOf(p(1, 100f, 100f), p(2, 300f, 120f, false)))
        swap.onEvent(60, listOf(p(1, 100f, 100f), p(3, 250f, 200f)))
        swap.onEvent(120, listOf(p(1, 100f, 100f, false), p(3, 250f, 200f, false)))
        assertFalse(swap.isTap)
        // 終わる前（指を置いたまま）はタップでない
        val holding = TwoFingerTap(slopPx = 16f)
        holding.onEvent(0, listOf(p(1, 100f, 100f), p(2, 300f, 120f)))
        assertFalse(holding.isTap)
    }
}
