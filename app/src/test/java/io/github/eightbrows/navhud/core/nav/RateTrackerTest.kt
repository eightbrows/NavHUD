package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.model.Fix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RateTrackerTest {

    /** 北へ 10m/s、毎秒 1m 上昇、1秒間隔で seconds+1 点。 */
    private fun steady(seconds: Int): RateTracker {
        val t = RateTracker()
        for (s in 0..seconds) {
            t.add(
                Fix(
                    timeMs = 1_000_000L + s * 1000L,
                    lat = TestGeo.lat(10.0 * s, 33.0),
                    lon = 133.0,
                    altRawM = 500.0 + s,
                    speedMps = 10f,
                ),
            )
        }
        return t
    }

    @Test
    fun steadyMotion() {
        val t = steady(100)
        val r60 = t.rate(60)!!
        assertEquals(600.0, r60.distanceM, 1e-6)
        assertEquals(60.0, r60.altDiffM!!, 1e-9)
        assertEquals(10.0, r60.avgSpeedMps, 1e-9)
        assertEquals(100.0, t.rate(10)!!.distanceM, 1e-6)
    }

    @Test
    fun notEnoughHistory() {
        val t = steady(20)
        assertNull(t.rate(30))
        assertNotNull(t.rate(10))
    }

    @Test
    fun baseLagBoundary() {
        // 起点ちょうど 2 秒前の base は有効、2.001 秒前なら欠損とみなす
        fun tracker(baseLagMs: Long) = RateTracker().apply {
            add(Fix(timeMs = 100_000L - baseLagMs, lat = 33.0, lon = 133.0))
            add(Fix(timeMs = 110_000L, lat = 33.001, lon = 133.0))
        }
        assertNotNull(tracker(2_000).rate(10))
        assertNull(tracker(2_001).rate(10))
    }

    @Test
    fun gapSegmentUsesStraightDistance() {
        val t = RateTracker()
        t.add(Fix(timeMs = 0, lat = 33.0, lon = 133.0, speedMps = 0f))
        t.add(Fix(timeMs = 10_000, lat = TestGeo.lat(500.0, 33.0), lon = 133.0, speedMps = 0f))
        assertEquals(500.0, t.rate(10)!!.distanceM, 0.5)
    }

    @Test
    fun missingSpeedUsesStraightDistance() {
        val t = RateTracker()
        for (s in 0..10) t.add(Fix(timeMs = s * 1000L, lat = TestGeo.lat(5.0 * s, 33.0), lon = 133.0))
        val r = t.rate(10)!!
        assertEquals(50.0, r.distanceM, 0.05)
        assertNull(r.altDiffM)
    }

    @Test
    fun timeGoingBackwardsResetsHistory() {
        val t = steady(100)
        t.add(Fix(timeMs = 0, lat = 33.0, lon = 133.0))
        assertNull(t.rate(10))
    }

    @Test
    fun etaSpeedUsesAvailableHistoryWhenWindowIsShort() {
        // 窓 60 秒に足りない: 9 秒分では出さない、10 秒分ならある分の平均速度
        assertNull(steady(9).etaSpeed(60))
        assertEquals(10.0, steady(10).etaSpeed(60)!!, 1e-9)
        assertEquals(10.0, steady(30).etaSpeed(60)!!, 1e-9)
        // 窓に足りていれば RATE の平均速度そのもの
        val t = steady(100)
        assertEquals(t.rate(60)!!.avgSpeedMps, t.etaSpeed(60)!!, 1e-9)
        assertNull(RateTracker().etaSpeed(60))
    }

    @Test
    fun etaSpeedAfterGapUsesFixesInsideTheWindow() {
        // 0..100 秒のあと 50 秒欠損して、150..165 秒: 窓 60 秒の起点（105 秒）には Fix がない → RATE は null
        val t = steady(100)
        for (s in 150..165) {
            t.add(Fix(timeMs = 1_000_000L + s * 1000L, lat = TestGeo.lat(5.0 * (s - 150), 34.0), lon = 133.0, speedMps = 5f))
        }
        assertNull(t.rate(60))
        // 窓の中にある 150..165 秒（15 秒）の平均 = 5 m/s
        assertEquals(5.0, t.etaSpeed(60)!!, 1e-9)
    }
}
