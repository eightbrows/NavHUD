package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.model.Fix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 位置から求める向き（TRK。20261121-D01）: 直前に 8 m 歩いた所と今を結ぶ向き */
class TrackHeadingTest {

    private val t0 = 1_800_000_000_000L
    private val gap = 10_000L

    /** 1 秒ごとの点。東へ e [m]・北へ n [m]、速さ v [m/s] */
    private fun fix(sec: Int, e: Double, n: Double, v: Float? = 1.5f, acc: Float? = 4f) =
        Fix(timeMs = t0 + sec * 1000L, lat = TestGeo.lat(n), lon = TestGeo.lon(e), speedMps = v, horizAccM = acc)

    private fun TrackHeading.walk(fixes: List<Fix>) = fixes.forEach { update(it, 15f, gap) }

    @Test
    fun walkingNorthGivesNorthAfter8m() {
        val h = TrackHeading()
        // 1.5 m/s で北へ。8 m 歩くまで（5 点目まで）は向きがない
        val pts = (0..10).map { fix(it, 0.0, 1.5 * it) }
        h.walk(pts.take(6))
        assertNull(h.deg)
        h.walk(pts.drop(6))
        assertEquals(0.0, h.deg!!.toDouble(), 0.01)
        // 東へ曲がると、8 m 歩いたあとは東
        val east = (11..20).map { fix(it, 1.5 * (it - 10), 15.0) }
        h.walk(east)
        assertEquals(90.0, h.deg!!.toDouble(), 0.5)
    }

    @Test
    fun doesNotChangeWhileStoppedOrSlow() {
        val h = TrackHeading()
        h.walk((0..10).map { fix(it, 0.0, 1.5 * it) })
        val before = h.deg
        assertNotNull(before)
        // 止まっている（速さ 0）・1 km/h 未満・速さがない点では、位置が揺れても変えない
        h.walk(listOf(fix(11, 6.0, 15.0, v = 0f), fix(12, -6.0, 18.0, v = 0.2f), fix(13, 6.0, 12.0, v = null)))
        assertEquals(before, h.deg)
    }

    @Test
    fun badAccuracyPointsAreNotUsed() {
        val h = TrackHeading()
        h.walk((0..10).map { fix(it, 0.0, 1.5 * it) })
        // 水平精度が上限（15 m）を超えた点は、今の点にも結ぶ相手にもしない
        h.walk(listOf(fix(11, 20.0, 16.5, acc = 30f), fix(12, 20.0, 18.0, acc = 16f)))
        assertEquals(0.0, h.deg!!.toDouble(), 0.01)
        // 精度が戻れば、悪かった点は飛ばして結ぶ（北のまま）
        h.update(fix(13, 0.0, 19.5), 15f, gap)
        assertEquals(0.0, h.deg!!.toDouble(), 0.01)
    }

    @Test
    fun gapClearsHistory() {
        val h = TrackHeading()
        h.walk((0..10).map { fix(it, 0.0, 1.5 * it) })
        assertNotNull(h.deg)
        // 測位の欠け（10 秒を超える）のあとは、向きを捨てて測り直す（欠けをまたいで結ばない）
        h.update(fix(30, 100.0, 15.0), 15f, gap)
        assertNull(h.deg)
        h.walk((31..40).map { fix(it, 100.0 + 1.5 * (it - 30), 15.0) })
        assertEquals(90.0, h.deg!!.toDouble(), 0.5)
    }

    @Test
    fun backAndForthIsNotUsed() {
        val h = TrackHeading()
        // その場で行ったり来たり（歩いた距離は 8 m を超えるが、2 点の間は 4.8 m（8 m × 0.6）より短い）
        val pts = (0..12).map { fix(it, 0.0, if (it % 4 < 2) 0.0 + it % 2 else 2.0 - it % 2) }
        h.walk(pts)
        assertNull(h.deg)
    }

    @Test
    fun resetClears() {
        val h = TrackHeading()
        h.walk((0..10).map { fix(it, 0.0, 1.5 * it) })
        h.reset()
        assertNull(h.deg)
    }
}
