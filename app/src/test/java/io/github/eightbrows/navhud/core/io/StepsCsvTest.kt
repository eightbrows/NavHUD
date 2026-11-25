package io.github.eightbrows.navhud.core.io

import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.nav.StepsTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 20261124-D01: GpsLogger の歩数（steps.csv）の読み込みと、軌跡との時刻合わせ（経過時間） */
class StepsCsvTest {

    /** 仕様の例 */
    private val example = """
        type,event_elapsed_ns,recv_elapsed_ns,epoch_ms,count
        counter,1854188012000000,1854209331000000,1795004456610,240885
        detector,1854210331000000,1854210931000000,1795004478929,
        detector,1854210768000000,1854211368000000,1795004479366,
        detector,1854211205000000,1854211805000000,1795004479803,
    """.trimIndent() + "\n"

    @Test
    fun readsTheExample() {
        val r = StepsCsv.parse(example)
        assertEquals(3, r.detector.size)
        assertEquals(1, r.counter.size)
        assertEquals(0, r.skippedLines)
        assertEquals(StepEvent(StepType.COUNTER, 1854188012000000, 1854209331000000, 1795004456610, 240885), r.counter.single())
        assertEquals(listOf(1854210331000000, 1854210768000000, 1854211205000000), r.detector.map { it.eventElapsedNs })
        assertNull(r.detector.first().count)
        // CRLF でも同じ
        assertEquals(r, StepsCsv.parse(example.replace("\n", "\r\n")))
    }

    @Test
    fun headerOnlyAndEmptyAreNotErrors() {
        val h = StepsCsv.parse("type,event_elapsed_ns,recv_elapsed_ns,epoch_ms,count\n")
        assertTrue(h.isEmpty)
        assertEquals(0, h.skippedLines)
        assertTrue(StepsCsv.parse("").isEmpty)
        assertEquals(StepsStatus.EMPTY, StepsLoad.of("type,event_elapsed_ns,recv_elapsed_ns,epoch_ms,count\r\n", null, listOf(1L)).status)
        assertEquals(StepsStatus.NONE, StepsLoad.of(null, null, listOf(1L)).status)
    }

    @Test
    fun columnsByNameAndSortedByEventTime() {
        // 列の順が違う・知らない列がある・届いた順と時刻の順が違う・知らない type と読めない行は飛ばす
        val text = """
            count,extra,epoch_ms,event_elapsed_ns,type,recv_elapsed_ns
            ,x,1000,3000,detector,9000
            ,x,1000,1000,detector,9000
            5,x,1000,2500,counter,9000
            3,x,1000,500,counter,9000
            ,x,1000,2000,detector,9100
            ,x,1000,7,walking,1
            ,x,1000,abc,detector,1
            ,x,1000,600,counter,1
        """.trimIndent()
        val r = StepsCsv.parse(text)
        assertEquals(listOf(1000L, 2000L, 3000L), r.detector.map { it.eventElapsedNs })
        assertEquals(listOf(500L to 3L, 2500L to 5L), r.counter.map { it.eventElapsedNs to it.count })
        assertEquals(3, r.skippedLines)
    }

    @Test
    fun metaTimebaseAnomalies() {
        val ok = """{"schemaVersion":1,"steps":{"status":"recorded","detector":{"present":true,"timebaseAnomalies":0},"counter":{"timebaseAnomalies":0}}}"""
        val bad = """{"steps":{"status":"recorded","detector":{"timebaseAnomalies":2},"counter":{"name":"x \"y\"","timebaseAnomalies":1}}, "tags":["a",1.5,null,true]}"""
        assertEquals(StepsMeta("recorded", 0, 0), StepsMeta.parse(ok))
        assertEquals(StepsMeta("recorded", 2, 1), StepsMeta.parse(bad))
        assertEquals(3L, StepsMeta.parse(bad).timebaseAnomalies)
        // steps がない古い meta.json・JSON でない
        assertEquals(StepsMeta.NONE, StepsMeta.parse("""{"comment":"歩行"}"""))
        assertEquals(StepsMeta.NONE, StepsMeta.parse("not json"))
        // 異常が 0 より大きければ使わない。軌跡に経過時間がなければ使わない
        assertEquals(StepsStatus.TIMEBASE, StepsLoad.of(example, bad, listOf(1L, 2L)).status)
        assertEquals(StepsStatus.OK, StepsLoad.of(example, ok, listOf(1L, 2L)).status)
        assertEquals(StepsStatus.NO_ELAPSED, StepsLoad.of(example, ok, listOf(1L, null)).status)
        // 合計の歩数（20261125-D01）: counter があれば区間の「最後の値 − 基準値」。この例の counter は基準値の 1 行だけなので 0
        assertEquals(0, StepsLoad.of(example, ok, listOf(1L)).steps)
        assertEquals(3, StepsLoad.of(example.lines().filter { !it.startsWith("counter") }.joinToString("\n"), ok, listOf(1L)).steps)
        assertNull(StepsLoad.of(example, bad, listOf(1L)).steps)
    }

    // ---------------------------------------------------------------- zip

    private val track = """
        utc_iso8601,epoch_ms,elapsed_realtime_ns,latitude,longitude,speed_mps
        x,1795004478000,1854210000000000,35.0,135.0,1.2
        x,1795004479000,1854211000000000,35.00001,135.0,1.2
        x,1795004480000,1854212000000000,35.00002,135.0,1.2
    """.trimIndent() + "\n"

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val bo = ByteArrayOutputStream()
        ZipOutputStream(bo).use { z ->
            for ((n, t) in entries) {
                z.putNextEntry(ZipEntry(n))
                z.write(t.toByteArray())
                z.closeEntry()
            }
        }
        return bo.toByteArray()
    }

    @Test
    fun zipReadsOnlyTheSessionStepsFile() {
        val s = "session_20261124_100000"
        // steps.csv が track.csv より前にあっても読む。sats.csv・知らないファイルは読み飛ばす
        val z = zip("$s/steps.csv" to example, "$s/sats.csv" to "a,b\n1,2\n", "$s/x.txt" to "?", "$s/track.csv" to track, "$s/meta.json" to """{"steps":{"status":"recorded"}}""")
        val d = SessionZip.readSession(ByteArrayInputStream(z), "$s/track.csv")!!
        assertEquals(3, d.track.fixes.size)
        assertEquals(1854211000000000, d.track.fixes[1].elapsedNs)
        assertEquals(StepsStatus.OK, d.steps.status)
        assertEquals(0, d.steps.steps)
        assertEquals(3, d.steps.csv!!.detector.size)
        // 今までの read は軌跡だけ（同じ結果）
        assertEquals(d.track, SessionZip.read(ByteArrayInputStream(z), "$s/track.csv"))
        // steps.csv がないセッションは「歩数なし」（エラーにしない）
        assertEquals(StepsStatus.NONE, SessionZip.readSession(ByteArrayInputStream(zip("$s/track.csv" to track)), "$s/track.csv")!!.steps.status)
        // 決まりに完全一致しない場所の steps.csv は読まない（zip の直下・深い所・名前の形が違う）
        for (bad in listOf("steps.csv" to "track.csv", "a/$s/steps.csv" to "a/$s/track.csv", "session_2026112_100000/steps.csv" to "session_2026112_100000/track.csv",
                           "session_20261124_100000x/steps.csv" to "session_20261124_100000x/track.csv")) {
            val zz = zip(bad.first to example, bad.second to track)
            assertEquals(bad.first, StepsStatus.NONE, SessionZip.readSession(ByteArrayInputStream(zz), bad.second)!!.steps.status)
        }
        // 別のセッションの steps.csv は使わない
        val other = zip("session_20261124_110000/steps.csv" to example, "$s/track.csv" to track)
        assertEquals(StepsStatus.NONE, SessionZip.readSession(ByteArrayInputStream(other), "$s/track.csv")!!.steps.status)
        // 軌跡に経過時間の列がなければ使わない
        val noEl = zip("$s/steps.csv" to example, "$s/track.csv" to track.replace("elapsed_realtime_ns", "elapsed_other"))
        assertEquals(StepsStatus.NO_ELAPSED, SessionZip.readSession(ByteArrayInputStream(noEl), "$s/track.csv")!!.steps.status)
        // フォルダから読むとき（track.csv と同じフォルダ）も同じ決まり
        val t = TrackCsv.parse(track)
        assertEquals(StepsStatus.OK, SessionZip.stepsForFolder(s, example, null, t).status)
        assertEquals(StepsStatus.NONE, SessionZip.stepsForFolder("walk", example, null, t).status)
        assertEquals(StepsStatus.NONE, SessionZip.stepsForFolder(s, null, null, t).status)
    }

    // ---------------------------------------------------------------- 時刻合わせ

    @Test
    fun alignsByElapsedTimeNotEpoch() {
        val fixes = TrackCsv.parse(track).fixes
        // steps.csv の epoch_ms は 1.3 秒ずれている（端末の時計）。経過時間で合わせるので影響しない
        val csv = StepsCsv.parse(
            """
            type,event_elapsed_ns,recv_elapsed_ns,epoch_ms,count
            detector,1854210500000000,0,1795004479800,
            detector,1854211000000000,0,1795004480300,
            detector,1854209000000000,0,1795004478300,
            detector,1854211250000000,0,1795004480550,
            detector,1854213000000000,0,1795004482300,
            counter,1854205000000000,0,1795004474300,100
            """.trimIndent(),
        )
        val tl = StepsTimeline.align(fixes, csv)!!
        assertEquals(5, tl.total)
        // 測位点 0 と 1 の間（el0 < e ≤ el1）は 2 歩、1 と 2 の間は 1 歩
        assertEquals(2, tl.detectorsBetween(fixes[0].elapsedNs!!, fixes[1].elapsedNs!!))
        assertEquals(1, tl.detectorsBetween(fixes[1].elapsedNs!!, fixes[2].elapsedNs!!))
        // 軌跡の時刻に置くと: 1 秒前（最初の点より前）・+0.5 秒・+1.0 秒・+1.25 秒・+3 秒（最後の点より後）
        val t0 = fixes[0].timeMs
        assertEquals(listOf(-1000.0, 500.0, 1000.0, 1250.0, 3000.0), tl.detectorMs.map { it - t0 })
        assertEquals(1, tl.countUntil(t0))
        assertEquals(3, tl.countUntil(t0 + 1000))
        assertEquals(t0 + 1000, tl.lastStepMs(t0 + 1100))
        // counter の 1 行は基準値なので、時刻は使わない（20261125-D01）
        assertEquals(emptyList<Long>(), tl.counterCount.toList())
        // 経過時間のない点があれば合わせない
        assertNull(StepsTimeline.align(fixes.mapIndexed { i, f -> if (i == 1) f.copy(elapsedNs = null) else f }, csv))
        assertNull(StepsTimeline.align(emptyList<Fix>(), csv))
        assertNotNull(StepsTimeline.align(fixes, StepsCsv.parse("type,event_elapsed_ns\n")))
    }
}
