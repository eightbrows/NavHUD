package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.model.Fix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RateTrackerTest {

    private val mPerDegLat = 111_195.0

    /** 北へ 10m/s、毎秒 1m 上昇、1秒間隔で seconds+1 点。 */
    private fun steady(seconds: Int): RateTracker {
        val t = RateTracker()
        for (s in 0..seconds) {
            t.add(
                Fix(
                    timeMs = 1_000_000L + s * 1000L,
                    lat = 33.0 + 10.0 * s / mPerDegLat,
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
        t.add(Fix(timeMs = 10_000, lat = 33.0 + 500 / mPerDegLat, lon = 133.0, speedMps = 0f))
        assertEquals(500.0, t.rate(10)!!.distanceM, 0.5)
    }

    @Test
    fun missingSpeedUsesStraightDistance() {
        val t = RateTracker()
        for (s in 0..10) t.add(Fix(timeMs = s * 1000L, lat = 33.0 + 5.0 * s / mPerDegLat, lon = 133.0))
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
}
