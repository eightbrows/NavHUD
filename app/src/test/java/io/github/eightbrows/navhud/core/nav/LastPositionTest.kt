package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.io.BaseMap
import io.github.eightbrows.navhud.core.io.MapLayer
import io.github.eightbrows.navhud.core.io.MapLevel
import io.github.eightbrows.navhud.core.io.MapLine
import io.github.eightbrows.navhud.core.io.MapPoint
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.view.HudRect
import io.github.eightbrows.navhud.core.view.HudSceneBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** 位置がまだないときの地図の中心（前回の位置か皇居）と、前回の位置を覚え直す決まり（20261024-D04）。 */
class LastPositionTest {

    private fun fix(t: Long, northM: Double = 0.0) = Fix(timeMs = t, lat = TestGeo.lat(northM), lon = TestGeo.LON0)

    @Test
    fun saverSavesTheFirstFixThenAfterTimeOrDistance() {
        val s = LastPositionSaver(minMs = 30_000, minM = 100.0)
        assertTrue(s.shouldSave(fix(0)))
        // 30 秒たたず、100 m 動いていなければ覚えない
        assertFalse(s.shouldSave(fix(10_000, 50.0)))
        // 100 m 以上動いたら覚える
        assertTrue(s.shouldSave(fix(20_000, 150.0)))
        // 動かなくても 30 秒たったら覚える
        assertFalse(s.shouldSave(fix(40_000, 150.0)))
        assertTrue(s.shouldSave(fix(50_000, 150.0)))
        // 時刻が戻ったら（端末の時計を直したなど）覚える
        assertTrue(s.shouldSave(fix(1_000, 150.0)))
    }

    @Test
    fun idleCenterIsTheRememberedPositionOrTheImperialPalace() {
        assertEquals(35.6852 to 139.7528, LastPositionSaver.idleCenter(null))
        assertEquals(Tuning.DEFAULT_MAP_CENTER_LAT to Tuning.DEFAULT_MAP_CENTER_LON, LastPositionSaver.idleCenter(null))
        assertEquals(34.0 to 135.0, LastPositionSaver.idleCenter(34.0 to 135.0))
    }

    @Test
    fun mapIsDrawnAroundTheIdleCenterUntilAFixArrives() {
        val e = NavEngine(NavSettings(), ZoneId.of("Asia/Tokyo"), SourceKind.LIVE)
        assertNull(e.state.idleCenter)
        // 皇居のすぐ近くの空港の点（皇居から北へ 200 m）
        val near = MapPoint(MapLayer.AIRPORT, 0, Tuning.DEFAULT_MAP_CENTER_LAT + 200 / TestGeo.M_PER_DEG_LAT, Tuning.DEFAULT_MAP_CENTER_LON, "RJXX")
        val map = BaseMap(listOf(MapLevel(0.0, emptyList<MapLine>())), listOf(near))
        val rect = HudRect(0f, 0f, 720f, 1200f)
        // 中心がなければ、今までどおり地図を描かない
        assertTrue(HudSceneBuilder.build(e.state, rect, baseMap = map).baseMapMarks.isEmpty())
        // 位置がないときは、中心（皇居）のまわりの地図を描く
        val (lat, lon) = LastPositionSaver.idleCenter(null)
        val s = e.setIdleCenter(lat, lon)
        assertEquals(lat to lon, s.idleCenter)
        assertEquals(1, HudSceneBuilder.build(s, rect, baseMap = map).baseMapMarks.size)
        // 位置が来たら、自機のまわり（皇居から遠い所）に移る
        e.onFix(Fix(timeMs = 0, lat = 33.0, lon = 133.0), 0)
        assertTrue(HudSceneBuilder.build(e.state, rect, baseMap = map).baseMapMarks.isEmpty())
    }
}
