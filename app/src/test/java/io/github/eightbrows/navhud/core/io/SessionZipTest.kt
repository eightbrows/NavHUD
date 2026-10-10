package io.github.eightbrows.navhud.core.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** GpsLogger の zip（§6.7）: 中のセッションの一覧と、選んだセッションの track.csv の読み込み。 */
class SessionZipTest {

    /** GpsLogger の track.csv（22 列）の見出し */
    private val header = "utc_iso8601,epoch_ms,elapsed_realtime_ns,provider,latitude,longitude," +
        "altitude_ellipsoid_m,horizontal_acc_m,vertical_acc_m,speed_mps,speed_acc_mps," +
        "bearing_deg,bearing_acc_deg,gdop,pdop,hdop,vdop,tdop,is_mock,gap_before,interval_sec,pressure_hpa"

    /** startMs から 1 秒ごとに n 点の track.csv */
    private fun track(startMs: Long, n: Int, extra: String = ""): String = buildString {
        append(header).append('\n')
        for (i in 0 until n) {
            val t = startMs + i * 1_000L
            append("2026-08-13T00:00:00.000Z,$t,${t * 1_000_000},gps,${33.5 + i * 1e-4},${133.0 + i * 1e-4},")
            append("1300.5,4.5,4.8,12.0,1.8,62.4,4.1,1.5,1.3,0.7,1.1,0.8,false,false,1.0,870.1\n")
        }
        append(extra)
    }

    private fun zip(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, text) in files) {
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun errorOf(block: () -> Unit): SessionZipError? =
        try {
            block()
            null
        } catch (e: SessionZipException) {
            e.error
        }

    private val t1 = 1_786_661_555_000L // 2026-08-13T22:52:35Z
    private val t2 = t1 + 86_400_000L

    @Test
    fun oneSession() {
        // GpsLogger の形: セッションのフォルダの中に track.csv・sats.csv・meta.json・track.gpx・track.kmz
        val bytes = zip(
            "session_20260814_075235/track.csv" to track(t1, 120),
            "session_20260814_075235/sats.csv" to "utc_iso8601,epoch_ms,svid\n" + "x,1,2\n".repeat(5_000),
            "session_20260814_075235/meta.json" to "{}",
            "session_20260814_075235/track.gpx" to "<gpx/>",
            "session_20260814_075235/track.kmz" to "PK",
        )
        assertTrue(SessionZip.isZip(bytes.copyOf(4)))
        // セッションが1つでも一覧にする
        val list = SessionZip.list(bytes.inputStream())
        assertEquals(1, list.size)
        val s = list.single()
        assertEquals("session_20260814_075235/track.csv", s.entryName)
        assertEquals("session_20260814_075235", s.name)
        // 開始時刻・長さ（最後 − 最初）・点の数
        assertEquals(120, s.summary.points)
        assertEquals(t1, s.summary.startMs)
        assertEquals(t1 + 119_000L, s.summary.endMs)
        assertEquals(0, s.summary.skippedLines)
        // 選んだセッションの track.csv だけを読む（sats.csv などは読み飛ばす）
        val result = SessionZip.read(bytes.inputStream(), s.entryName)!!
        assertEquals(120, result.fixes.size)
        assertEquals(t1, result.fixes.first().timeMs)
        assertEquals(33.5, result.fixes.first().lat, 1e-9)
        assertEquals(12.0f, result.fixes.first().speedMps)
    }

    @Test
    fun multipleSessions() {
        // 3 セッション（zip の中の順番は時刻の順ではない）。一覧は開始時刻の順で、つながない
        val bytes = zip(
            "session_20260815_075235/track.csv" to track(t2, 30),
            "session_20260815_075235/sats.csv" to "a,b\n1,2\n",
            "session_20260814_075235/sats.csv" to "a,b\n1,2\n",
            "session_20260814_075235/track.csv" to track(t1, 10, extra = "broken,line\n\n"),
            "session_20260816_120000/track.csv" to track(t2 + 86_400_000L, 5),
        )
        val list = SessionZip.list(bytes.inputStream())
        assertEquals(
            listOf("session_20260814_075235", "session_20260815_075235", "session_20260816_120000"),
            list.map { it.name },
        )
        assertEquals(listOf(10, 30, 5), list.map { it.summary.points })
        assertEquals(listOf(t1, t2, t2 + 86_400_000L), list.map { it.summary.startMs })
        // 読めない行は数える（空行は数えない）。概要と、実際に読んだ結果は同じ数
        assertEquals(1, list[0].summary.skippedLines)
        for (s in list) {
            val r = SessionZip.read(bytes.inputStream(), s.entryName)!!
            assertEquals(s.summary.points, r.fixes.size)
            assertEquals(s.summary.skippedLines, r.skippedLines)
            assertEquals(s.summary.startMs, r.fixes.first().timeMs)
            assertEquals(s.summary.endMs, r.fixes.last().timeMs)
        }
        // 覚えておいたセッションが zip にない（別の zip に置き換わった）: null → 一覧を出す
        assertNull(SessionZip.read(bytes.inputStream(), "session_20200101_000000/track.csv"))
    }

    @Test
    fun trackCsvAtTheRootOrInADeeperFolder() {
        // フォルダなし（zip の直下）や、もう1段深いフォルダの track.csv も読む。名前は track.csv だけ（my_track.csv は読まない）
        val bytes = zip(
            "track.csv" to track(t1, 3),
            "export/session_20260815_075235/track.csv" to track(t2, 4),
            "session_x/my_track.csv" to track(t2, 9),
            "session_y/" to "",
        )
        val list = SessionZip.list(bytes.inputStream())
        assertEquals(listOf("" to 3, "session_20260815_075235" to 4), list.map { it.name to it.summary.points })
        assertEquals(3, SessionZip.read(bytes.inputStream(), "track.csv")!!.fixes.size)
    }

    @Test
    fun sessionWithoutReadableRowsIsListedLast() {
        // 見出しだけの track.csv: 点 0・時刻なし。一覧には出す（最後）
        val bytes = zip(
            "session_b/track.csv" to "$header\n",
            "session_a/track.csv" to track(t1, 2),
        )
        val list = SessionZip.list(bytes.inputStream())
        assertEquals(listOf("session_a", "session_b"), list.map { it.name })
        assertEquals(0, list[1].summary.points)
        assertNull(list[1].summary.startMs)
        assertNull(list[1].summary.endMs)
    }

    @Test
    fun noTrackCsv() {
        // track.csv が入っていない zip
        val bytes = zip("session_20260814_075235/sats.csv" to "a,b\n1,2\n", "readme.txt" to "hello")
        assertEquals(SessionZipError.NO_TRACK, errorOf { SessionZip.list(bytes.inputStream()) })
        assertNull(SessionZip.read(bytes.inputStream(), "session_20260814_075235/track.csv"))
        // 項目が1つもない空の zip も「track.csv がない」
        val empty = zip()
        assertTrue(SessionZip.isZip(empty.copyOf(4)))
        assertEquals(SessionZipError.NO_TRACK, errorOf { SessionZip.list(empty.inputStream()) })
    }

    @Test
    fun brokenZip() {
        val good = zip(
            "session_20260814_075235/sats.csv" to "a,b\n" + "1,2\n".repeat(20_000),
            "session_20260814_075235/track.csv" to track(t1, 2_000),
        )
        // 途中で切れている（track.csv の途中まで）
        val cut = good.copyOf(good.size * 2 / 3)
        assertEquals(SessionZipError.BROKEN, errorOf { SessionZip.list(cut.inputStream()) })
        assertEquals(SessionZipError.BROKEN, errorOf { SessionZip.read(cut.inputStream(), "session_20260814_075235/track.csv") })
        // 中身が書き換わっている（圧縮したデータの途中をでたらめにする）
        val garbled = good.copyOf().also { b -> for (i in 200 until 400) b[i] = (i * 31).toByte() }
        assertEquals(SessionZipError.BROKEN, errorOf { SessionZip.list(garbled.inputStream()) })
        // zip ではない（CSV の文字、0 バイト、PK で始まるだけのでたらめ）
        assertFalse(SessionZip.isZip("utc_".toByteArray()))
        assertFalse(SessionZip.isZip(ByteArray(0)))
        assertEquals(SessionZipError.BROKEN, errorOf { SessionZip.list(track(t1, 5).byteInputStream()) })
        assertEquals(SessionZipError.BROKEN, errorOf { SessionZip.list(ByteArray(0).inputStream()) })
        val fake = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4) + ByteArray(64) { (it * 7).toByte() }
        assertTrue(SessionZip.isZip(fake.copyOf(4)))
        assertEquals(SessionZipError.BROKEN, errorOf { SessionZip.list(fake.inputStream()) })
        // 壊れた zip で例外が外に漏れない（SessionZipException だけ）
        try {
            SessionZip.list(cut.inputStream())
            fail()
        } catch (e: SessionZipException) {
            assertEquals(SessionZipError.BROKEN, e.error)
        }
    }

    @Test
    fun summaryCountsTheSameRowsAsParse() {
        // 概要（一覧用）と parse は、同じ行を数える: 緯度・経度・時刻が読める行だけ
        val text = track(t1, 4) +
            "2026-08-13T00:00:10.000Z,,1,gps,33.5,133.0,1300,4,4,0,1,0,0\n" + // epoch_ms が空 → utc_iso8601 を使う
            ",,1,gps,33.5,133.0\n" + // 時刻なし
            "x,${t1 + 99_000},1,gps,,133.0\n" + // 緯度なし
            "\n"
        val sum = TrackCsv.summarize(text.lineSequence())
        val parsed = TrackCsv.parse(text)
        assertEquals(5, sum.points)
        assertEquals(parsed.fixes.size, sum.points)
        assertEquals(2, sum.skippedLines)
        assertEquals(parsed.skippedLines, sum.skippedLines)
        assertEquals(parsed.fixes.minOf { it.timeMs }, sum.startMs)
        assertEquals(parsed.fixes.maxOf { it.timeMs }, sum.endMs)
        // BOM 付きの見出しでも同じ
        val bom = "\uFEFF" + track(t1, 3)
        assertEquals(3, TrackCsv.summarize(bom.lineSequence()).points)
        assertEquals(3, TrackCsv.parse(bom).fixes.size)
        // 空のファイル・見出しだけ
        assertEquals(TrackSummary(0, 0, null, null), TrackCsv.summarize(emptySequence()))
        assertEquals(TrackSummary(0, 0, null, null), TrackCsv.summarize(sequenceOf(header)))
    }
}
