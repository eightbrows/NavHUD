package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Fix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import kotlin.math.abs

/** 20261122-D04: 位置から求める向きの距離を水平精度に合わせて伸ばす・軌跡のなめらかさ */
class TrackDistanceAndSmoothTest {

    private val t0 = 1_800_000_000_000L

    // ---------------------------------------------------------------- 1. 向きを求める距離

    @Test
    fun distanceFollowsTheAccuracy() {
        // 精度が良いときは設定の距離、悪いときは水平精度 × 1.0、15 m で止まる。精度がない点は設定の距離
        assertEquals(4.0, TrackHeading.distanceFor(3f, 4.0), 0.0)
        assertEquals(4.0, TrackHeading.distanceFor(4f, 4.0), 0.0)
        assertEquals(6.5, TrackHeading.distanceFor(6.5f, 4.0), 1e-6)
        assertEquals(12.0, TrackHeading.distanceFor(12f, 4.0), 0.0)
        assertEquals(15.0, TrackHeading.distanceFor(15f, 4.0), 0.0)
        assertEquals(15.0, TrackHeading.distanceFor(27f, 4.0), 0.0)
        assertEquals(4.0, TrackHeading.distanceFor(null, 4.0), 0.0)
        // 設定を変えると下限が変わる（自動車 8 m）。設定が 15 m より長ければ設定の距離
        assertEquals(8.0, TrackHeading.distanceFor(6f, 8.0), 0.0)
        assertEquals(12.0, TrackHeading.distanceFor(12f, 8.0), 0.0)
        assertEquals(20.0, TrackHeading.distanceFor(27f, 20.0), 0.0)
        assertEquals(1.0, Tuning.TRACK_HEADING_ACC_FACTOR, 0.0)
        assertEquals(15.0, Tuning.TRACK_HEADING_ACC_MAX_M, 0.0)
    }

    /**
     * 北へ 30 m 歩いてから東へ曲がる（1 m/s・1 秒ごと・水平精度 acc）。曲がってから、向きが東 ±5° に入るまでの秒数。
     * 結ぶ相手は D m 前の点なので、D 秒ほど
     */
    private fun secondsToEast(acc: Float, settingM: Double, maxAccM: Float = 15f): Int {
        val th = TrackHeading()
        var turnedAt: Int? = null
        for (i in 0..80) {
            val north = if (i <= 30) i.toDouble() else 30.0
            val east = if (i <= 30) 0.0 else (i - 30).toDouble()
            th.update(Fix(timeMs = t0 + i * 1000L, lat = TestGeo.lat(north), lon = TestGeo.lon(east), speedMps = 1f, horizAccM = acc), maxAccM, 10_000L, settingM)
            if (i > 30 && turnedAt == null && abs(th.deg!! - 90f) <= 5f) turnedAt = i - 30
        }
        return turnedAt!!
    }

    @Test
    fun theDirectionWaitsLongerWhenThePositionIsPoor() {
        // 精度 3 m: 設定の 4 m。精度 10 m: 10 m。精度 25 m（使う位置精度の上限を 30 m にしたとき）: 15 m で止まる
        val good = secondsToEast(3f, 4.0)
        val poor = secondsToEast(10f, 4.0)
        val worse = secondsToEast(25f, 4.0, maxAccM = 30f)
        // 結ぶ相手が角の 1 m 手前でも東 ±5° に入るので、D − 1 〜 D + 1 秒
        assertTrue("good $good", good in 3..5)
        assertTrue("poor $poor", poor in 9..11)
        assertTrue("worse $worse", worse in 14..16)
        // 設定を 8 m にすると、精度が良くても 8 m
        val car = secondsToEast(3f, 8.0)
        assertTrue("car $car", car in 7..9)
        assertEquals(poor, secondsToEast(10f, 8.0))
    }

    @Test
    fun everyPresetUsesTheSameRule() {
        // 歩行（位置だけ・4 m）で、精度 10 m の点ばかりなら、曲がってから 10 m ほど歩くまで向きは東にならない
        fun run(s: NavSettings, acc: Float): Int {
            val e = NavEngine(s, ZoneId.of("Asia/Tokyo"), SourceKind.REPLAY)
            var at: Int? = null
            for (i in 0..80) {
                val north = if (i <= 30) i.toDouble() else 30.0
                val east = if (i <= 30) 0.0 else (i - 30).toDouble()
                val f = Fix(timeMs = t0 + i * 1000L, lat = TestGeo.lat(north), lon = TestGeo.lon(east), speedMps = 1f, horizAccM = acc)
                val st = e.onFix(f, f.timeMs)
                if (i > 30 && at == null && abs(st.heading.deg!! - 90f) <= 5f) at = i - 30
            }
            return at!!
        }
        val walk = NavSettings().selectTravelMode(TravelMode.WALK)
        assertTrue(run(walk, 3f) in 3..5)
        assertTrue(run(walk, 10f) in 9..11)
        // 自動車を位置だけにしても同じ決まり（下限は自動車の 8 m）
        val car = NavSettings().editReach { it.copy(headingMethod = HeadingMethod.TRACK_ONLY) }
        assertTrue(run(car, 3f) in 7..9)
        assertTrue(run(car, 12f) in 11..13)
    }

    // ---------------------------------------------------------------- 2. 軌跡のなめらかさ

    /** 東へ 60 m、北へ 60 m の L 字（1 m/s・1 秒ごと）。まっすぐな所は左右に ±zig m のジグザグ */
    private fun lShape(zig: Double): List<TrackPoint> {
        val out = mutableListOf<TrackPoint>()
        for (i in 0..120) {
            val side = if (i % 2 == 0) zig else -zig
            val (e, n) = if (i <= 60) Pair(i.toDouble(), if (i in 3..57) side else 0.0) else Pair(60.0 + if (i in 63..117) side else 0.0, (i - 60).toDouble())
            out += TrackPoint(TestGeo.lat(n), TestGeo.lon(e), t0 + i * 1000L, 1f, 4f)
        }
        return out
    }

    /** まっすぐな所（角から 15 m 以上離れた所）の線の折れの合計 [°] */
    private fun zigzag(pts: List<TrackPoint>): Double {
        val corner = pts[60]
        val far = pts.filter { Geo.distanceM(it.lat, it.lon, corner.lat, corner.lon) > 15.0 && Geo.distanceM(it.lat, it.lon, pts.first().lat, pts.first().lon) > 3 && Geo.distanceM(it.lat, it.lon, pts.last().lat, pts.last().lon) > 3 }
        var sum = 0.0
        for (k in 2 until far.size) {
            val a = Geo.bearingDeg(far[k - 2].lat, far[k - 2].lon, far[k - 1].lat, far[k - 1].lon)
            val b = Geo.bearingDeg(far[k - 1].lat, far[k - 1].lon, far[k].lat, far[k].lon)
            sum += abs(((b - a + 540) % 360) - 180)
        }
        return sum
    }

    @Test
    fun smoothingRemovesTheZigzagButKeepsTheCorner() {
        val raw = lShape(1.0)
        val sm = Trail.smooth(raw, 3.0, 10_000L)
        assertEquals(raw.size, sm.size)
        assertEquals(raw.map { it.timeMs }, sm.map { it.timeMs })
        // まっすぐな所のジグザグが 8 割以上減る
        assertTrue("${zigzag(sm)} vs ${zigzag(raw)}", zigzag(sm) < zigzag(raw) * 0.2)
        // 角はあまり削れない（直角の角の点から線まで 2 m 以内。σ 4 m より角に近い）
        fun cornerGap(p: List<TrackPoint>) = p.minOf { Geo.distanceM(it.lat, it.lon, TestGeo.lat(0.0), TestGeo.lon(60.0)) }
        assertTrue("σ3 ${cornerGap(sm)}", cornerGap(sm) < 2.0)
        assertTrue(cornerGap(Trail.smooth(raw, 4.0, 10_000L)) > cornerGap(sm))
        // なし（0）はそのまま
        assertEquals(raw, Trail.smooth(raw, 0.0, 10_000L))
        // 精度の悪い点（20 m）が 15 m 跳んでも、なめらかにした線は跳ばない（その点自身も前後の点から作る）
        val jump = raw.toMutableList()
        jump[30] = TrackPoint(TestGeo.lat(15.0), TestGeo.lon(30.0), jump[30].timeMs, 1f, 20f)
        val sj = Trail.smooth(jump, 3.0, 10_000L)
        assertTrue(Geo.distanceM(sj[30].lat, sj[30].lon, TestGeo.lat(0.0), TestGeo.lon(30.0)) < 1.5)
        // 欠け（10 秒を超える間）はまたがない: 欠けの前後の点は、反対側の点を使わない
        val gap = raw.mapIndexed { i, p -> if (i > 30) p.copy(timeMs = p.timeMs + 60_000L) else p }
        val sg = Trail.smooth(gap, 3.0, 10_000L)
        val before = Trail.smooth(raw.take(31), 3.0, 10_000L)
        assertEquals(before, sg.take(31))
    }

    @Test
    fun stoppedPointsCollapse() {
        // 歩いて、止まって（速さ 0・位置は ±2 m 揺れる）、また歩く: 止まっている間の点は 1 か所にまとまる
        val pts = mutableListOf<TrackPoint>()
        for (i in 0..20) pts += TrackPoint(TestGeo.lat(i.toDouble()), TestGeo.lon(0.0), t0 + i * 1000L, 1f, 4f)
        for (k in 1..30) pts += TrackPoint(TestGeo.lat(20.0 + if (k % 2 == 0) 2.0 else -2.0), TestGeo.lon(if (k % 3 == 0) 2.0 else -1.0), t0 + (20 + k) * 1000L, 0f, 4f)
        val sm = Trail.smooth(pts, 3.0, 10_000L)
        val stop = sm.subList(21, 51)
        val spread = stop.maxOf { a -> stop.maxOf { b -> Geo.distanceM(a.lat, a.lon, b.lat, b.lon) } }
        assertTrue("spread $spread", spread < 0.01)
    }

    @Test
    fun theLiveTrailIsDecidedLater() {
        val raw = lShape(1.0)
        val live = LiveTrail(minStepM = 1.5).apply { smoothM = 3.0 }
        val win = Tuning.TRACK_SMOOTH_WINDOW_SIGMA * 3.0
        for (p in raw) live.add(Fix(timeMs = p.timeMs, lat = p.lat, lon = p.lon, speedMps = p.speedMps, horizAccM = p.horizAccM))
        // 自機のすぐ後ろ（9 m 歩いたぶん）は今の線（来た点のまま）、それより前はなめらかにした点（読み込んだ軌跡と同じ平均）
        val head = raw.last()
        val pts = live.points
        // いちばん後ろは来た点（今と同じく 1.5 m ごとなので、最後の点か 1 つ前。自機まではその先の線でつなぐ）
        assertTrue(pts.last() in raw.takeLast(2))
        val full = Trail.thin(Trail.smooth(raw, 3.0, 10_000L), 1.5)
        val decided = pts.takeWhile { it !in raw.takeLast(win.toInt() + 1) }
        assertTrue("decided ${decided.size} / ${pts.size}", decided.size > 50)
        assertEquals(full.take(decided.size), decided)
        val tail = pts.drop(decided.size)
        assertTrue("tail raw", tail.all { it in raw })
        assertTrue("tail ${tail.size}", tail.size in 3..10)
        // 欠けのあとは、それまでの点を前の点だけで決める（読み込んだ軌跡と同じ）
        val after = raw.last().let { Fix(timeMs = it.timeMs + 60_000L, lat = TestGeo.lat(200.0), lon = TestGeo.lon(60.0), speedMps = 1f, horizAccM = 4f) }
        live.add(after)
        assertEquals(full.dropLast(1), live.points.dropLast(1).take(full.size - 1))
        // なし（0）は今までと同じ（来た点を 1.5 m ごと）
        val plain = LiveTrail(minStepM = 1.5)
        for (p in raw) plain.add(Fix(timeMs = p.timeMs, lat = p.lat, lon = p.lon, speedMps = p.speedMps, horizAccM = p.horizAccM))
        assertTrue("plain raw", plain.points.all { it in raw })
        assertEquals(Trail.thin(raw, 1.5).dropLast(1), plain.points.take(Trail.thin(raw, 1.5).size - 1))
    }

    @Test
    fun presetValuesAndTheEngine() {
        assertEquals(0, ReachProfile.CAR.trackSmoothM)
        assertEquals(3, ReachProfile.WALK.trackSmoothM)
        assertEquals(listOf(0, 2, 3, 4), NavSettings.TRACK_SMOOTH_CHOICES_M)
        assertEquals(0, NavSettings().trackSmoothM)
        val walk = NavSettings().selectTravelMode(TravelMode.WALK)
        assertEquals(3, walk.trackSmoothM)
        // 保存: 初期値のままなら書かない・変えたら変えた所。選べない値はプリセットの初期値
        assertEquals(null, SettingsCodec.encode(walk)["walk.trackSmoothM"])
        val c = NavSettings().editReach { it.copy(trackSmoothM = 2) }
        assertEquals(1, c.changedCount(TravelMode.CAR))
        assertEquals("2", SettingsCodec.encode(c)["car.trackSmoothM"])
        assertEquals(c, SettingsCodec.decode(SettingsCodec.encode(c)))
        assertEquals(0, SettingsCodec.decode(SettingsCodec.encode(c) + mapOf("car.trackSmoothM" to "5")).trackSmoothM)
        // エンジン: 歩行は読み込んだ軌跡をなめらかにしてから間引く。自動車（なし）は間引くだけ。プリセットを変えると作り直す
        val raw = lShape(1.0)
        val e = NavEngine(NavSettings(), ZoneId.of("Asia/Tokyo"), SourceKind.REPLAY)
        assertEquals(Trail.thin(raw, 10.0), e.setReplayTrack(raw).replayTrack)
        assertEquals(Trail.thin(Trail.smooth(raw, 3.0, 10_000L), 3.0), e.updateSettings(walk).replayTrack)
        assertEquals(Trail.thin(raw, 3.0), e.updateSettings(walk.editReach { it.copy(trackSmoothM = 0) }).replayTrack)
        assertEquals(Trail.thin(raw, 10.0), e.updateSettings(NavSettings()).replayTrack)
    }

    @Test
    fun smoothingIsOnlyForTheLine() {
        // なめらかさを変えても、HDG・GS・到達の判定の元になる値は変わらない（線だけ）
        val raw = lShape(1.0)
        fun run(sm: Int): List<String> {
            val s = NavSettings().selectTravelMode(TravelMode.WALK).editReach { it.copy(trackSmoothM = sm) }
            val e = NavEngine(s, ZoneId.of("Asia/Tokyo"), SourceKind.LIVE)
            return raw.map { p ->
                val st = e.onFix(Fix(timeMs = p.timeMs, lat = p.lat, lon = p.lon, speedMps = 1f, horizAccM = 4f), p.timeMs)
                "${st.heading.deg} ${st.groundSpeedMps} ${st.avgHeadingDeg}"
            }
        }
        assertEquals(run(0), run(3))
    }
}
