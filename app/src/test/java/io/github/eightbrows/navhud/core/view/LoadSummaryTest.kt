package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.SampleTrack
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

/** 読み込んだ結果のひとことの中身（20261103-D01）。 */
class LoadSummaryTest {

    private val tokyo = ZoneId.of("Asia/Tokyo")

    @Test
    fun waypointCountAndDisabled() {
        val wps = listOf(
            Waypoint("峠の入口", 33.47, 133.00),
            Waypoint("展望台", 33.46, 132.96),
            Waypoint("旧道", 33.48, 132.87, enabled = false),
        )
        assertEquals(LoadSummary.Waypoints(3, 1), LoadSummary.waypoints(wps))
        assertEquals(LoadSummary.Waypoints(0, 0), LoadSummary.waypoints(emptyList()))
    }

    @Test
    fun trackPointsAndTimes() {
        // 2026-08-14 07:52:35 〜 11:01:55 JST、点の数は 3 桁ごとにカンマ
        val start = 1_786_661_555_000L
        val fixes = (0 until 11_172).map { Fix(timeMs = start + it * 1_017L, lat = 33.5, lon = 133.0) }
        val t = LoadSummary.track(fixes, tokyo)!!
        assertEquals("11,172", t.points)
        assertEquals("7:52", t.start)
        assertEquals("11:01", t.end)
        // 日をまたぐときは日付も
        val night = listOf(Fix(timeMs = start - 9 * 3_600_000L, lat = 33.5, lon = 133.0), Fix(timeMs = start, lat = 33.5, lon = 133.0))
        val n = LoadSummary.track(night, tokyo)!!
        assertEquals("8/13 22:52", n.start)
        assertEquals("8/14 7:52", n.end)
        // 点がなければ null
        assertNull(LoadSummary.track(emptyList(), tokyo))
    }

    @Test
    fun sampleTrack() {
        // track.csv（git 管理外。なければスキップ）: 11,172 点・7:52〜11:02（最後の点は 11:02:00 JST。分は切り捨て）
        val t = LoadSummary.track(SampleTrack.fixes(), tokyo)!!
        assertEquals("11,172", t.points)
        assertEquals("7:52", t.start)
        assertEquals("11:02", t.end)
    }
}
