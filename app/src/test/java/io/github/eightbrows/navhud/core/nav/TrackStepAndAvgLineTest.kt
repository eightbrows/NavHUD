package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.view.HudGeometry
import io.github.eightbrows.navhud.core.view.HudRect
import io.github.eightbrows.navhud.core.view.HudSceneBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** 20261122-D03: 軌跡の点の間隔と平均した方位線（移動のプリセットの値）、軌跡の間引き、画面の線 */
class TrackStepAndAvgLineTest {

    private val t0 = 1_800_000_000_000L

    @Test
    fun presetValues() {
        val car = ReachProfile.CAR
        val walk = ReachProfile.WALK
        assertEquals(false, car.avgHeadingLine)
        assertEquals(5, car.avgHeadingSec)
        assertEquals(10, car.trackStepM)
        assertEquals(true, walk.avgHeadingLine)
        assertEquals(5, walk.avgHeadingSec)
        assertEquals(3, walk.trackStepM)
        assertEquals(3..10, NavSettings.AVG_HEADING_SEC_RANGE)
        assertEquals(listOf(2, 3, 5, 10, 20), NavSettings.TRACK_STEP_CHOICES_M)
        // 今の値は選んでいるプリセットの値
        val w = NavSettings().selectTravelMode(TravelMode.WALK)
        assertEquals(true, w.avgHeadingLine)
        assertEquals(3, w.trackStepM)
        assertEquals(false, NavSettings().avgHeadingLine)
        // 初期値のままなら保存しない・変えたら変えた所（自動車でオンにする）
        assertEquals(null, SettingsCodec.encode(w)["walk.avgHeadingLine"])
        val c = NavSettings().editReach { it.copy(avgHeadingLine = true, avgHeadingSec = 8, trackStepM = 5) }
        assertEquals(3, c.changedCount(TravelMode.CAR))
        val enc = SettingsCodec.encode(c)
        assertEquals("true", enc["car.avgHeadingLine"])
        assertEquals("8", enc["car.avgHeadingSec"])
        assertEquals("5", enc["car.trackStepM"])
        assertEquals(c, SettingsCodec.decode(enc))
        // 範囲の外・選べない値は、そのプリセットの初期値
        val bad = SettingsCodec.decode(enc + mapOf("car.avgHeadingSec" to "30", "car.trackStepM" to "7"))
        assertEquals(5, bad.avgHeadingSec)
        assertEquals(10, bad.trackStepM)
        // 歩行でオフにした端末はオフのまま（変えた所）
        val off = w.editReach { it.copy(avgHeadingLine = false) }
        assertEquals("false", SettingsCodec.encode(off)["walk.avgHeadingLine"])
        assertEquals(false, SettingsCodec.decode(SettingsCodec.encode(off)).walkProfile.avgHeadingLine)
    }

    @Test
    fun thinningKeepsCorners() {
        // 東へ 31 m、北へ 30 m の L 字を 1 m ごと（角は東 31 m。10 の倍数でないので、10 m 間隔では角の点が落ちやすい）
        val pts = (0..31).map { TrackPoint(TestGeo.lat(0.0), TestGeo.lon(it.toDouble()), t0 + it * 1000L) } +
            (1..30).map { TrackPoint(TestGeo.lat(it.toDouble()), TestGeo.lon(31.0), t0 + (31 + it) * 1000L) }
        val corner = pts[31]
        fun cornerGap(step: Double): Double = Trail.thin(pts, step).minOf { Geo.distanceM(it.lat, it.lon, corner.lat, corner.lon) }
        // 残した点のうち角にいちばん近い点までの距離: 歩行の 3 m では 3 m 未満、自動車の 10 m ではそれより遠い
        assertTrue("3 m: ${cornerGap(3.0)}", cornerGap(3.0) < 3.0)
        assertTrue("10 m: ${cornerGap(10.0)}", cornerGap(10.0) > cornerGap(3.0))
        assertTrue(Trail.thin(pts, 3.0).size > Trail.thin(pts, 10.0).size * 2)
        // 間引いた結果を同じ間隔でもう一度間引いても同じ（最初と最後の点は残す）
        for (step in listOf(2.0, 3.0, 10.0)) {
            val once = Trail.thin(pts, step)
            assertEquals(once, Trail.thin(once, step))
            assertEquals(pts.first(), once.first())
            assertEquals(pts.last(), once.last())
        }
        assertEquals(Trail.decimate(emptyList(), 10.0), emptyList<TrackPoint>())
        // LIVE の軌跡はその半分
        assertEquals(5.0, Trail.liveStepM(10), 0.0)
        assertEquals(1.5, Trail.liveStepM(3), 0.0)
        assertEquals(Tuning.LIVE_TRAIL_MIN_STEP_M, Trail.liveStepM(Tuning.TRACK_DECIMATE_M.toInt()), 0.0)
    }

    @Test
    fun engineRethinsTheLoadedTrackWhenTheStepChanges() {
        val pts = (0..200).map { TrackPoint(TestGeo.lat(it.toDouble()), TestGeo.lon(0.0), t0 + it * 1000L) }
        val e = NavEngine(NavSettings(), ZoneId.of("Asia/Tokyo"), SourceKind.REPLAY)
        val car = e.setReplayTrack(pts).replayTrack
        assertEquals(Trail.thin(pts, 10.0), car)
        val walk = NavSettings().selectTravelMode(TravelMode.WALK)
        assertEquals(Trail.thin(pts, 3.0), e.updateSettings(walk).replayTrack)
        assertTrue(Trail.thin(pts, 3.0).size > car.size * 2)
        assertEquals(car, e.updateSettings(NavSettings()).replayTrack)
        // LIVE の軌跡: 歩行は 1.5 m ごと、自動車は 5 m ごと
        val live = NavEngine(walk, ZoneId.of("Asia/Tokyo"), SourceKind.LIVE)
        var st: NavState? = null
        for (i in 0..20) {
            val f = Fix(timeMs = t0 + i * 1000L, lat = TestGeo.lat(i.toDouble()), lon = TestGeo.lon(0.0), speedMps = 1f)
            st = live.onFix(f, f.timeMs)
        }
        // 1 m ずつ北へ 20 m: 1.5 m ごとなら 10 か 11 点（自動車の 5 m ごとなら 4〜5 点）
        assertTrue("${st!!.liveTrail.size}", st.liveTrail.size in 10..11)
    }

    private fun walkState(mode: DisplayMode, on: Boolean = true): NavState {
        val s = NavSettings(displayMode = mode).selectTravelMode(TravelMode.WALK).editReach { it.copy(avgHeadingLine = on) }
        val e = NavEngine(s, ZoneId.of("Asia/Tokyo"), SourceKind.REPLAY)
        var st: NavState? = null
        // 北へ 10 秒、そのあと東へ 3 秒（今の HDG は東、平均（5 秒）は北と東の間）
        for (i in 0..12) {
            val north = if (i <= 9) 1.5 * i else 13.5
            val east = if (i <= 9) 0.0 else 1.5 * (i - 9)
            val f = Fix(timeMs = t0 + i * 1000L, lat = TestGeo.lat(north), lon = TestGeo.lon(east), speedMps = 1.5f, horizAccM = 4f)
            st = e.onFix(f, f.timeMs)
        }
        return st!!
    }

    @Test
    fun averageLineOnTheScreen() {
        val rect = HudRect(0f, 0f, 720f, 1280f)
        for (mode in DisplayMode.entries) {
            val st = walkState(mode)
            val hdg = st.heading.deg!!.toDouble()
            val avg = st.avgHeadingDeg!!.toDouble()
            assertTrue("今の HDG は東寄り $hdg", hdg in 45.0..100.0)
            assertTrue("平均は北と東の間で HDG と違う $avg", avg in 5.0..85.0 && kotlin.math.abs(avg - hdg) > 5)
            val sc = HudSceneBuilder.build(st, rect)
            val line = assertNotNull(sc.avgLine).let { sc.avgLine!! }
            val o = line.a
            // 向き: ARC は画面の上が今の HDG なので avg − hdg、North Up は avg
            val screenDeg = Math.toDegrees(Math.atan2((line.b.x - o.x).toDouble(), (o.y - line.b.y).toDouble()))
            val want = if (mode == DisplayMode.ARC) avg - hdg else avg
            assertEquals(((want + 540) % 360) - 180, ((screenDeg + 540) % 360) - 180, 0.5)
            // 長さ: 今の方位線（ラバーライン）の半分
            val rubber = sc.segments.first { it.ink == io.github.eightbrows.navhud.core.view.Ink.OWNSHIP && it.a == o }
            assertEquals(HudGeometry.dist(rubber.a, rubber.b) * Tuning.AVG_HEADING_LINE_RATIO, HudGeometry.dist(line.a, line.b), 1f)
            // オフなら描かない（自動車の初期値もオフ）
            assertNull(HudSceneBuilder.build(walkState(mode, on = false), rect).avgLine)
        }
        assertFalse(NavSettings().avgHeadingLine)
    }
}
