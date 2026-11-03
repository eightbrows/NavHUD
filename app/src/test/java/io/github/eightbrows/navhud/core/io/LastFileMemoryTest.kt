package io.github.eightbrows.navhud.core.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 前回のファイルの覚えとクリア（20261102-D01）。クリアしたら、次の起動（同じ保存から作り直したもの）でも覚えていない。 */
class LastFileMemoryTest {

    /** 保存の代わり（Map）。アプリを開き直したことは、同じ Map から LastFileMemory を作り直すことで確かめる */
    private class MapStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(vararg keys: String) { keys.forEach { map.remove(it) } }
    }

    @Test
    fun waypointListIsForgottenByClear() {
        val kv = MapStore()
        val list = LastFileMemory(kv, "list_uri")
        assertNull(list.uri)
        list.remember("content://a/waypoints.csv")
        assertEquals("content://a/waypoints.csv", list.uri)
        // 次の起動でも覚えている（前回のリストを開く）
        assertEquals("content://a/waypoints.csv", LastFileMemory(kv, "list_uri").uri)
        // クリア: 次の起動では覚えていない（自動で開かない）
        list.forget()
        assertNull(list.uri)
        assertNull(LastFileMemory(kv, "list_uri").uri)
        assertEquals(emptyMap<String, String>(), kv.map)
    }

    @Test
    fun trackAndZipSessionAreForgottenTogether() {
        val kv = MapStore()
        val track = LastFileMemory(kv, "track_uri", "track_zip_entry")
        track.remember("content://a/log.zip")
        track.rememberEntry("session_20260814_075234/track.csv")
        val reopened = LastFileMemory(kv, "track_uri", "track_zip_entry")
        assertEquals("content://a/log.zip", reopened.uri)
        assertEquals("session_20260814_075234/track.csv", reopened.entry)
        // クリア: ファイルもセッションも忘れる
        track.forget()
        val next = LastFileMemory(kv, "track_uri", "track_zip_entry")
        assertNull(next.uri)
        assertNull(next.entry)
    }

    @Test
    fun choosingAnotherFileForgetsTheSession() {
        val kv = MapStore()
        val track = LastFileMemory(kv, "track_uri", "track_zip_entry")
        track.remember("content://a/log.zip")
        track.rememberEntry("s1/track.csv")
        track.remember("content://a/track.csv")
        assertEquals("content://a/track.csv", track.uri)
        assertNull(track.entry)
    }

    @Test
    fun theOtherFileIsNotTouched() {
        // WP リストと軌跡は別のキー（別の保存）: 片方をクリアしても、もう片方は覚えたまま
        val kv = MapStore()
        val list = LastFileMemory(kv, "list_uri")
        val track = LastFileMemory(kv, "track_uri", "track_zip_entry")
        list.remember("content://a/waypoints.csv")
        track.remember("content://a/track.csv")
        list.forget()
        assertNull(list.uri)
        assertEquals("content://a/track.csv", track.uri)
        // 覚えるキーがない（WP リスト）ときのセッションは何もしない
        list.rememberEntry("x")
        assertNull(list.entry)
    }
}
