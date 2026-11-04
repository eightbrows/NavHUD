package io.github.eightbrows.navhud.core.io

import io.github.eightbrows.navhud.core.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 最近のファイル（20261104-D03。ファイルの画面）: 新しい順・5 件まで・✕ で消す・WP リストと軌跡で別・違う種類のファイルは入れない・
 * 権限を返してよいか。開けないファイルは履歴に残す（画面でグレーにする）。
 */
class RecentFilesTest {

    private class MapStore : KeyValueStore {
        val map = mutableMapOf<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(vararg keys: String) { keys.forEach { map.remove(it) } }
    }

    private fun f(n: Int, t: Long = n * 1000L) = RecentFile("content://doc/$n", "file$n.csv", t)

    @Test
    fun newestFirstAndAtMostFive() {
        assertEquals(5, Tuning.RECENT_FILES_MAX)
        val r = RecentFiles(MapStore(), "k")
        assertTrue(r.list().isEmpty())
        for (i in 1..5) assertTrue(r.add(f(i)).isEmpty())
        assertEquals(listOf(5, 4, 3, 2, 1).map { "file$it.csv" }, r.list().map { it.name })
        // 6 件目: いちばん古い 1 が外れる（外れたものを返す。権限を返すか確かめるため）
        assertEquals(listOf(f(1)), r.add(f(6)))
        assertEquals(listOf(6, 5, 4, 3, 2).map { "content://doc/$it" }, r.list().map { it.uri })
    }

    @Test
    fun openingAgainMovesToTheTopWithTheNewTime() {
        val r = RecentFiles(MapStore(), "k")
        for (i in 1..3) r.add(f(i))
        assertTrue(r.add(f(1, t = 99_000)).isEmpty())
        assertEquals(listOf(1, 3, 2).map { "content://doc/$it" }, r.list().map { it.uri })
        assertEquals(99_000L, r.list().first().openedAtMs)
        assertEquals(3, r.list().size)
    }

    @Test
    fun removeWithTheCross() {
        val r = RecentFiles(MapStore(), "k")
        for (i in 1..3) r.add(f(i))
        assertEquals(f(2), r.remove("content://doc/2"))
        assertEquals(listOf(3, 1).map { "content://doc/$it" }, r.list().map { it.uri })
        assertNull(r.remove("content://doc/2"))
        r.remove("content://doc/3")
        r.remove("content://doc/1")
        assertTrue(r.list().isEmpty())
    }

    @Test
    fun waypointsAndTracksAreSeparate() {
        // 同じ保存の場所でも、名前（key）が違えば別の履歴（WP リスト "list_recent" と軌跡 "track_recent"）
        val kv = MapStore()
        val wp = RecentFiles(kv, "list_recent")
        val track = RecentFiles(kv, "track_recent")
        wp.add(f(1))
        track.add(f(2))
        track.add(f(3))
        assertEquals(listOf("content://doc/1"), wp.list().map { it.uri })
        assertEquals(listOf("content://doc/3", "content://doc/2"), track.list().map { it.uri })
        track.remove("content://doc/2")
        assertEquals(1, wp.list().size)
        // 片方を 5 件より多くしても、もう片方は減らない
        for (i in 10..20) track.add(f(i))
        assertEquals(listOf("content://doc/1"), wp.list().map { it.uri })
    }

    @Test
    fun otherKindIsNotAdded() {
        val r = RecentFiles(MapStore(), "k")
        // 違う種類のファイル（WP リストとして軌跡を選んだ・その逆）: 入れない
        assertTrue(r.recordOpen(f(1), FileKindException(FileKind.TRACK, "file1.csv")).isEmpty())
        assertTrue(r.list().isEmpty())
        // 読めたら入れる
        r.recordOpen(f(2), null)
        r.recordOpen(f(3), null)
        assertEquals(listOf("content://doc/3", "content://doc/2"), r.list().map { it.uri })
        // 前に入っていたファイルの中身が違う種類になった: 消す（外れたものとして返す）
        assertEquals(listOf("content://doc/2"), r.recordOpen(f(2), FileKindException(FileKind.WAYPOINTS, "file2.csv")))
        assertEquals(listOf("content://doc/3"), r.list().map { it.uri })
        // ほかの理由で読めなかった（ファイルが消えた・権限がない）: 変えない（画面では「開けません」）
        assertTrue(r.recordOpen(f(3, t = 50_000), IllegalStateException("gone")).isEmpty())
        assertEquals(listOf(f(3)), r.list())
    }

    @Test
    fun savedTextRoundTripsAndSkipsBrokenLines() {
        val files = listOf(RecentFile("content://a/1", "名前\tタブ\n改行.csv", 1L), RecentFile("content://a/2", "", 2L))
        val back = RecentFiles.decode(RecentFiles.encode(files))
        assertEquals(listOf("content://a/1", "content://a/2"), back.map { it.uri })
        assertEquals("名前 タブ 改行.csv", back[0].name)
        assertEquals("", back[1].name)
        assertEquals(listOf(1L, 2L), back.map { it.openedAtMs })
        assertTrue(RecentFiles.decode(null).isEmpty())
        assertTrue(RecentFiles.decode("").isEmpty())
        assertEquals(listOf("content://ok"), RecentFiles.decode("x\tcontent://bad\tn\n5\t\tn\n7\tcontent://ok\tok").map { it.uri })
        // 空になったら保存の値も消す
        val kv = MapStore()
        val r = RecentFiles(kv, "k")
        r.add(f(1))
        assertTrue("k" in kv.map)
        r.remove("content://doc/1")
        assertFalse("k" in kv.map)
    }

    @Test
    fun permissionIsReleasedOnlyWhenNothingUsesTheFile() {
        // WP リストと軌跡の、最近のファイル・前回のファイル（読み込み中）のどれかにあれば返さない
        val inUse = setOf("content://wp/last", "content://wp/recent", "content://track/recent", null)
        assertFalse(RecentFiles.shouldRelease("content://wp/last", inUse))
        assertFalse(RecentFiles.shouldRelease("content://track/recent", inUse))
        assertTrue(RecentFiles.shouldRelease("content://gone", inUse))
        assertTrue(RecentFiles.shouldRelease("content://x", emptySet()))
    }
}
