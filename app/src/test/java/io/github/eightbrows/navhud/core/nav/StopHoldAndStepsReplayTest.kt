package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.io.StepsCsv
import io.github.eightbrows.navhud.core.io.TrackCsv
import io.github.eightbrows.navhud.core.model.Fix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import kotlin.math.abs
import kotlin.random.Random

/** 20261124-D01: 止まっている間に向きが回らない（TurnGate。調べ 20261122-STOPHOLD の 5節のテスト）と、歩数を REPLAY で流す */
class StopHoldAndStepsReplayTest {

    private val t0 = 1_800_000_000_000L

    private fun fix(i: Int, north: Double, east: Double, v: Float, acc: Float = 4f) =
        Fix(timeMs = t0 + i * 1000L, lat = TestGeo.lat(north), lon = TestGeo.lon(east), speedMps = v, horizAccM = acc)

    private fun d(a: Float, b: Float) = abs(((a - b + 540f) % 360f) - 180f)

    @Test
    fun theValuesAreInTuning() {
        assertEquals(3.0, Tuning.TRACK_HEADING_MOVE_KMH, 0.0)
        assertEquals(5, Tuning.TRACK_HEADING_MOVE_SEC)
    }

    /** 1. 止まって 1〜2 km/h で揺れる点が続いても、向きが変わらない */
    @Test
    fun wobblingWhileStoppedDoesNotTurn() {
        val th = TrackHeading()
        var i = 0
        // 北へ 30 秒（1.4 m/s）
        for (k in 0..30) th.update(fix(i++, 1.4 * k, 0.0, 1.4f), 15f, 10_000L, 4.0)
        val before = th.deg!!
        assertTrue("北 $before", d(before, 0f) < 2f)
        // 止まる: 端末の速さ 1.5 km/h、位置は ±1.5 m を行ったり来たり（2 m ほど動く点もある）を 60 秒。
        // 止まって 5 秒までは、5 秒前の位置がまだ歩いていた所なので変わることがある（北から 30° 以内）。そのあとは変わらない
        val rnd = Random(3)
        var held: Float? = null
        for (k in 0..60) {
            th.update(fix(i++, 42.0 + rnd.nextDouble(-1.5, 1.5), rnd.nextDouble(-1.5, 1.5), 0.42f), 15f, 10_000L, 4.0)
            if (k < 5) {
                assertTrue("止まり $k ${th.deg}", d(th.deg!!, before) < 30f)
            } else {
                if (held == null) held = th.deg
                assertEquals("止まり $k", held!!, th.deg!!, 0f)
            }
        }
        // 前の形（速さ 1 km/h 以上だけ）なら回る: 同じ点で、5 秒前の位置からの決まりを外すと向きが変わる
        val old = TrackHeading(moveSec = 0)
        i = 0
        for (k in 0..30) old.update(fix(i++, 1.4 * k, 0.0, 1.4f), 15f, 10_000L, 4.0)
        val rnd2 = Random(3)
        var turned = false
        for (k in 0..60) {
            old.update(fix(i++, 42.0 + rnd2.nextDouble(-1.5, 1.5), rnd2.nextDouble(-1.5, 1.5), 0.42f), 15f, 10_000L, 4.0)
            if (d(old.deg!!, 0f) > 30f) turned = true
        }
        assertTrue(turned)
    }

    /** 2. 止まりのあと歩き出すと、5 秒以内に向きが変わる */
    @Test
    fun walkingOffTurnsWithinFiveSeconds() {
        val th = TrackHeading()
        var i = 0
        for (k in 0..30) th.update(fix(i++, 1.4 * k, 0.0, 1.4f), 15f, 10_000L, 4.0)
        for (k in 0..20) th.update(fix(i++, 42.0, 0.0, 0f), 15f, 10_000L, 4.0)
        // 東へ歩き出す（1.4 m/s）
        var at: Int? = null
        for (k in 1..15) {
            th.update(fix(i++, 42.0, 1.4 * k, 1.4f), 15f, 10_000L, 4.0)
            if (at == null && d(th.deg!!, 90f) <= 10f) at = k
        }
        assertTrue("歩き出し $at", at != null && at <= 5)
    }

    /** 3. 4 km/h で曲がると、今まで（5 秒前の位置の決まりなし）と同じ遅れ */
    @Test
    fun turningAtFourKmhHasTheSameDelay() {
        fun delay(moveSec: Int): Int {
            val th = TrackHeading(moveSec = moveSec)
            val v = 4.0 / 3.6
            var at: Int? = null
            for (k in 0..60) {
                val north = if (k <= 30) v * k else v * 30
                val east = if (k <= 30) 0.0 else v * (k - 30)
                th.update(fix(k, north, east, v.toFloat()), 15f, 10_000L, 4.0)
                if (k > 30 && at == null && d(th.deg!!, 90f) <= 5f) at = k - 30
            }
            return at!!
        }
        assertEquals(delay(0), delay(Tuning.TRACK_HEADING_MOVE_SEC))
    }

    /** 4. 歩きの記録 3 本: 5 秒前の位置から 4.2 m 離れていない点では向きを変えない。B の 86 秒の止まりでは回らない（ファイルがなければスキップ） */
    @Test
    fun walkRecordsDoNotTurnWhileStopped() {
        val dir = listOf(File("sample/walk"), File("../sample/walk")).firstOrNull { it.isDirectory }
        val names = listOf("session_20261121_132427", "session_20261122_121430", "session_20261122_132237")
        assumeTrue("歩きの記録がないためスキップ", dir != null && names.all { File(dir, "$it/track.csv").exists() })
        val walk = NavSettings().selectTravelMode(TravelMode.WALK)
        for (n in names) {
            val fixes = File(dir, "$n/track.csv").inputStream().use { TrackCsv.parse(it) }.fixes
            val e = NavEngine(walk, ZoneId.of("Asia/Tokyo"), SourceKind.REPLAY)
            val hdg = fixes.map { e.onFix(it, it.timeMs).heading }
            for (k in 1 until fixes.size) {
                val a = hdg[k - 1]
                val b = hdg[k]
                if (a.src != io.github.eightbrows.navhud.core.model.HeadingSrc.TRACK || b.src != io.github.eightbrows.navhud.core.model.HeadingSrc.TRACK || a.deg == b.deg) continue
                // 向きが変わった点: 5 秒以上前の点のうちいちばん新しい点から 4.2 m 以上離れている
                val q = fixes.subList(0, k).lastOrNull { fixes[k].timeMs - it.timeMs >= 5000 }!!
                assertTrue("$n $k", Geo.distanceM(q.lat, q.lon, fixes[k].lat, fixes[k].lon) >= 3.0 / 3.6 * 5 - 1e-9)
            }
            if (n == "session_20261122_121430") {
                // 86 秒の止まり（記録の始めから 10:51〜12:27 の止まりの区間）: 向きの変わりは 5° 以内
                val t0 = fixes.first().timeMs
                val inStop = fixes.indices.filter { (fixes[it].timeMs - t0) / 1000 in 651..746 }.mapNotNull { hdg[it].deg }
                val spread = inStop.maxOf { a -> inStop.maxOf { b -> d(a, b) } }
                assertTrue("止まりの間の向きの幅 $spread", spread <= 5f)
            }
        }
    }

    // ---------------------------------------------------------------- 歩数を REPLAY で流す

    @Test
    fun stepsFlowWithTheReplayClock() {
        val fixes = (0..20).map { k ->
            Fix(timeMs = t0 + k * 1000L, lat = TestGeo.lat(1.2 * k), lon = TestGeo.lon(0.0), speedMps = 1.2f, horizAccM = 4f, elapsedNs = 5_000_000_000_000L + k * 1_000_000_000L + 7)
        }
        // 0.5 秒ごとに 1 歩（経過時間）。届いた順は崩してある
        val det = (1..40).map { 5_000_000_000_000L + it * 500_000_000L }.shuffled(Random(1))
        val csv = StepsCsv.parse("type,event_elapsed_ns\n" + det.joinToString("\n") { "detector,$it" })
        val tl = StepsTimeline.align(fixes, csv)!!
        val e = NavEngine(NavSettings(), ZoneId.of("Asia/Tokyo"), SourceKind.REPLAY)
        e.setReplayTrack(Trail.decimate(fixes, 0.0))
        e.setReplaySteps(tl)
        // 再生: 点 k の時刻までの歩数（経過時間で数えた点 0〜k の間の歩数と同じ）
        for (k in fixes.indices) {
            val st = e.onFix(fixes[k], fixes[k].timeMs).steps!!
            assertEquals(40, st.total)
            assertEquals("k=$k", tl.detectorsBetween(0, fixes[k].elapsedNs!!), st.soFar)
        }
        // 点の間の時刻（倍速の刻み・一時停止）でも、その時刻までの数
        assertEquals(tl.countUntil(t0 + 20_500), e.onTick(t0 + 20_500).steps!!.soFar)
        // シークで戻す（時刻が戻る）と、その時刻までの数に戻る
        assertEquals(tl.detectorsBetween(0, fixes[5].elapsedNs!!), e.onFix(fixes[5], fixes[5].timeMs).steps!!.soFar)
        // LIVE・歩数なし・クリアでは null
        e.setSource(SourceKind.LIVE, playing = true)
        assertNull(e.onTick(t0 + 6000).steps)
        e.setSource(SourceKind.REPLAY, playing = false)
        assertEquals(tl.countUntil(t0 + 6000), e.onTick(t0 + 6000).steps!!.soFar)
        assertNull(e.setReplaySteps(null).steps)
        e.setReplaySteps(tl)
        assertNull(e.clearReplayTrack().steps)
        // 歩数は向き・GS には使わない（あってもなくても同じ）
        val a = NavEngine(NavSettings(), ZoneId.of("Asia/Tokyo"), SourceKind.REPLAY)
        val b = NavEngine(NavSettings(), ZoneId.of("Asia/Tokyo"), SourceKind.REPLAY).apply { setReplaySteps(tl) }
        for (f in fixes) {
            val sa = a.onFix(f, f.timeMs)
            val sb = b.onFix(f, f.timeMs)
            assertEquals(sa.heading, sb.heading)
            assertEquals(sa.groundSpeedMps, sb.groundSpeedMps)
        }
    }
}
