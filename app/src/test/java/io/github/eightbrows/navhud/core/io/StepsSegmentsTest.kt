package io.github.eightbrows.navhud.core.io

import io.github.eightbrows.navhud.core.nav.NavEngine
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.nav.StepsTimeline
import io.github.eightbrows.navhud.core.nav.Trail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** 20261125-D01: 歩数の仕様の新しい版（合計は counter の区間ごと・基準値の行・gap_before・届いた時刻） */
class StepsSegmentsTest {

    /** 新しい版の仕様の例（counter は約 3 秒遅れて届くので、ファイルの中で detector のあと） */
    private val example = """
        type,event_elapsed_ns,recv_elapsed_ns,epoch_ms,count
        counter,1854188012000000,1854209331000000,1795004456610,240885
        detector,1854210331000000,1854210931000000,1795004478929,
        detector,1854210768000000,1854211368000000,1795004479366,
        counter,1854210400000000,1854213700000000,1795004478998,240886
    """.trimIndent() + "\n"

    @Test
    fun theNewExample() {
        val r = StepsCsv.parse(example)
        assertEquals(2, r.detector.size)
        assertEquals(2, r.counter.size)
        // 合計は counter: 240886 − 240885（基準値）= 1 歩
        val seg = CounterSegments.of(r.counter, emptyList())
        assertEquals(1, seg.size)
        assertEquals(240885L, seg.single().baseline.count)
        assertEquals(1L, seg.single().steps)
        assertEquals(1, StepsLoad.totalSteps(r, emptyList()))
        // counter がなければ detector の数
        assertEquals(2, StepsLoad.totalSteps(r.copy(counter = emptyList()), emptyList()))
    }

    /**
     * 一時停止をはさむ記録: 区間 1（基準値 1000 → 1100）、一時停止中に 50 歩、区間 2（基準値 1150 → 1200）。
     * 区間ごとの合計は 100 + 50 = 150 歩（全体の「最後 − 最初」だと 200 歩で、一時停止中の歩が入ってしまう）
     */
    private val pausedSteps = """
        type,event_elapsed_ns,recv_elapsed_ns,epoch_ms,count
        counter,900000000000,1001000000000,0,1000
        detector,1010000000000,1011000000000,0,
        counter,1010000000000,1013000000000,0,1001
        counter,1090000000000,1093000000000,0,1100
        counter,1150000000000,2001000000000,0,1150
        detector,2010000000000,2011000000000,0,
        counter,2010000000000,2013000000000,0,1151
        counter,2050000000000,2053000000000,0,1200
    """.trimIndent() + "\n"

    /** 区間 1: 経過時間 1000〜1100 秒、区間 2: 2000〜2100 秒（2000 秒の点が gap_before=true） */
    private val pausedTrack: String = buildString {
        append("utc_iso8601,epoch_ms,elapsed_realtime_ns,latitude,longitude,speed_mps,gap_before\n")
        for (k in 0..100) append("x,${1795000000000L + k * 1000},${(1000L + k) * 1_000_000_000L},35.0,135.0,1.2,false\n")
        for (k in 0..100) append("x,${1795001000000L + k * 1000},${(2000L + k) * 1_000_000_000L},35.0,135.0,1.2,${k == 0}\n")
    }

    @Test
    fun sumsBySegmentSplitByGapBefore() {
        val track = TrackCsv.parse(pausedTrack)
        assertEquals(202, track.fixes.size)
        assertEquals(listOf(2000L * 1_000_000_000L), track.resumeElapsedNs)
        val csv = StepsCsv.parse(pausedSteps)
        val seg = CounterSegments.of(csv.counter, track.resumeElapsedNs)
        assertEquals(2, seg.size)
        // 区間 2 の基準値（1150）は、歩の時刻（1150 秒）が一時停止中でも、届いた時刻（2001 秒）で区間 2 に入る
        assertEquals(listOf(1000L, 1150L), seg.map { it.baseline.count })
        assertEquals(listOf(100L, 50L), seg.map { it.steps })
        val load = StepsLoad.of(pausedSteps, null, track.fixes.map { it.elapsedNs }, track.resumeElapsedNs)
        assertEquals(StepsStatus.OK, load.status)
        assertEquals(150, load.steps)
        // gap_before がない（区切らない）と 200 歩になる（一時停止中の 50 歩が入る）
        assertEquals(200, StepsLoad.totalSteps(csv, emptyList()))
    }

    @Test
    fun baselineRowTimesAreNotUsed() {
        val track = TrackCsv.parse(pausedTrack)
        val tl = StepsTimeline.align(track.fixes, StepsCsv.parse(pausedSteps), track.resumeElapsedNs)!!
        // counter の時刻は基準値の 2 行（900 秒・1150 秒）を除いた 4 行
        assertEquals(listOf(1001L, 1100L, 1151L, 1200L), tl.counterCount.toList())
        assertEquals(4, tl.counterMs.size)
    }

    @Test
    fun detectorReceivedTimesCanBeReplayed() {
        val track = TrackCsv.parse(pausedTrack)
        val tl = StepsTimeline.align(track.fixes, StepsCsv.parse(pausedSteps), track.resumeElapsedNs)!!
        val e = NavEngine(NavSettings(), ZoneId.of("Asia/Tokyo"), SourceKind.REPLAY)
        e.setReplayTrack(Trail.decimate(track.fixes, 0.0))
        e.setReplaySteps(tl)
        // 1010 秒の歩は 1011 秒に届く。1010.5 秒（点 10 と 11 の間）では、歩の時刻で 1 歩・届いた時刻で 0 歩
        val f10 = track.fixes[10]
        val st = e.onFix(f10, f10.timeMs + 500).steps!!
        assertEquals(1, st.soFar)
        assertEquals(0, st.receivedSoFar)
        val f11 = track.fixes[11]
        val st2 = e.onFix(f11, f11.timeMs).steps!!
        assertEquals(1, st2.receivedSoFar)
    }

    @Test
    fun timebaseAnomaliesAndOldFiles() {
        val track = TrackCsv.parse(pausedTrack)
        val el = track.fixes.map { it.elapsedNs }
        fun meta(d: Int, c: Int) = """{"steps":{"status":"recorded","detector":{"timebaseAnomalies":$d},"counter":{"timebaseAnomalies":$c}}}"""
        // counter の 1 は基準値が数えられていることが多い（前の版の GpsLogger）ので使う。2 以上・detector の 1 以上は使わない
        assertEquals(StepsStatus.OK, StepsLoad.of(pausedSteps, meta(0, 1), el).status)
        assertEquals(StepsStatus.TIMEBASE, StepsLoad.of(pausedSteps, meta(0, 2), el).status)
        assertEquals(StepsStatus.TIMEBASE, StepsLoad.of(pausedSteps, meta(1, 0), el).status)
        // 前の版の steps.csv（recv_elapsed_ns の列がない）も読める。区間分けは届いた時刻がなければ歩の時刻
        val old = "type,event_elapsed_ns,count\ncounter,1000000000000,10\ndetector,1001000000000,\ncounter,1001000000000,11\n"
        val r = StepsCsv.parse(old)
        assertEquals(1, r.detector.size)
        assertEquals(1, StepsLoad.totalSteps(r, emptyList()))
        // gap_before の列がない track.csv は区切りなし（今までどおり）
        assertTrue(TrackCsv.parse(pausedTrack.replace(",gap_before", ",other")).resumeElapsedNs.isEmpty())
    }
}
