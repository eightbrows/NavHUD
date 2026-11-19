package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.io.KeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 20261119-D02: 前の初期値（方位を止める / 戻す速度 7 / 11 km/h）を一度だけ 4 / 6 km/h に移す。 */
class HoldDefaultsMigrationTest {

    private class MapStore(init: Map<String, String> = emptyMap()) : KeyValueStore {
        val m = init.toMutableMap()
        override fun get(key: String): String? = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun remove(vararg keys: String) { keys.forEach { m.remove(it) } }
    }

    private fun kmh(store: MapStore): Pair<Int, Int> {
        val s = SettingsCodec.decode(store.m)
        return NavSettings.holdSpeedKmh(s.holdEnterSpeedMps) to NavSettings.holdSpeedKmh(s.holdExitSpeedMps)
    }

    /** 前の版が保存していた形（設定を全部書く。方位の速さは 7 / 11 km/h） */
    private fun oldSave(enter: Int = 7, exit: Int = 11): MapStore =
        MapStore(SettingsCodec.encode(NavSettings(holdEnterSpeedMps = NavSettings.kmhToMps(enter), holdExitSpeedMps = NavSettings.kmhToMps(exit))))

    @Test
    fun keysAreTheSameAsTheCodec() {
        val keys = SettingsCodec.encode(NavSettings()).keys
        assertTrue(HoldDefaultsMigration.KEY_ENTER in keys)
        assertTrue(HoldDefaultsMigration.KEY_EXIT in keys)
        // 印は設定の値ではない（「設定を初期値に戻す」で書き換えられない・消えない）
        assertFalse(HoldDefaultsMigration.KEY_DONE in keys)
        assertFalse(HoldDefaultsMigration.KEY_DONE in SettingsCodec.OBSOLETE_KEYS)
    }

    @Test
    fun oldDefaultsMoveToNewDefaults() {
        val st = oldSave()
        assertEquals(7 to 11, kmh(st))
        assertTrue(HoldDefaultsMigration.run(st))
        assertEquals(4 to 6, kmh(st))
        assertEquals(HoldDefaultsMigration.DONE_VALUE, st.get(HoldDefaultsMigration.KEY_DONE))
        // ほかの設定は変わらない
        val before = SettingsCodec.decode(oldSave().m)
        assertEquals(before.copy(holdEnterSpeedMps = NavSettings.kmhToMps(4), holdExitSpeedMps = NavSettings.kmhToMps(6)), SettingsCodec.decode(st.m))
        // D03 までの初期値 2.0 / 3.0 m/s（7.2 / 10.8 km/h。読むと 7 / 11 km/h）も前の初期値として移す
        val older = MapStore(mapOf(HoldDefaultsMigration.KEY_ENTER to "2.0", HoldDefaultsMigration.KEY_EXIT to "3.0"))
        assertTrue(HoldDefaultsMigration.run(older))
        assertEquals(4 to 6, kmh(older))
    }

    @Test
    fun otherValuesStay() {
        for ((enter, exit) in listOf(7 to 12, 6 to 11, 5 to 15, 4 to 6, 2 to 4, 10 to 20)) {
            val st = oldSave(enter, exit)
            assertFalse("$enter / $exit", HoldDefaultsMigration.run(st))
            assertEquals("$enter / $exit", enter to exit, kmh(st))
            assertEquals(HoldDefaultsMigration.DONE_VALUE, st.get(HoldDefaultsMigration.KEY_DONE))
        }
        // 片方しか保存がない・数でない値も、移さない（印だけ）
        val one = MapStore(mapOf(HoldDefaultsMigration.KEY_ENTER to NavSettings.kmhToMps(7).toString()))
        assertFalse(HoldDefaultsMigration.run(one))
        assertNull(one.get(HoldDefaultsMigration.KEY_EXIT))
        val bad = MapStore(mapOf(HoldDefaultsMigration.KEY_ENTER to "x", HoldDefaultsMigration.KEY_EXIT to "y"))
        assertFalse(HoldDefaultsMigration.run(bad))
        assertEquals("x", bad.get(HoldDefaultsMigration.KEY_ENTER))
    }

    @Test
    fun secondRunDoesNothing() {
        val st = oldSave()
        assertTrue(HoldDefaultsMigration.run(st))
        val after = st.m.toMap()
        assertFalse(HoldDefaultsMigration.run(st))
        assertEquals(after, st.m)
    }

    @Test
    fun reselectedOldDefaultsStay() {
        val st = oldSave()
        HoldDefaultsMigration.run(st)
        // 移したあとに、設定画面で 7 / 11 km/h を選び直して保存した
        SettingsCodec.encode(SettingsCodec.decode(st.m).copy(holdEnterSpeedMps = NavSettings.kmhToMps(7), holdExitSpeedMps = NavSettings.kmhToMps(11)))
            .forEach { (k, v) -> st.put(k, v) }
        assertFalse(HoldDefaultsMigration.run(st))
        assertEquals(7 to 11, kmh(st))
    }

    @Test
    fun newInstallOnlySavesTheMark() {
        val st = MapStore()
        assertFalse(HoldDefaultsMigration.run(st))
        assertEquals(mapOf(HoldDefaultsMigration.KEY_DONE to HoldDefaultsMigration.DONE_VALUE), st.m)
        assertEquals(4 to 6, kmh(st))
        // 印だけの保存は「前の版の設定」とはみなさない（到達判定のプリセットは自動車のまま）
        assertEquals(NavSettings(), SettingsCodec.decode(st.m))
    }

    @Test
    fun markSurvivesResetToDefaults() {
        val st = oldSave(5, 15)
        HoldDefaultsMigration.run(st)
        // 「設定を初期値に戻す」: 初期値を全部書く（SettingsStore.save と同じく、設定の値だけ書き換える）
        SettingsCodec.encode(SettingsCodec.decode(st.m).resetKeepingCustomReach()).forEach { (k, v) -> st.put(k, v) }
        assertEquals(HoldDefaultsMigration.DONE_VALUE, st.get(HoldDefaultsMigration.KEY_DONE))
        assertEquals(4 to 6, kmh(st))
        // 戻したあとに 7 / 11 km/h を選んでも、もう移さない
        SettingsCodec.encode(SettingsCodec.decode(st.m).copy(holdEnterSpeedMps = NavSettings.kmhToMps(7), holdExitSpeedMps = NavSettings.kmhToMps(11)))
            .forEach { (k, v) -> st.put(k, v) }
        assertFalse(HoldDefaultsMigration.run(st))
        assertEquals(7 to 11, kmh(st))
    }
}
