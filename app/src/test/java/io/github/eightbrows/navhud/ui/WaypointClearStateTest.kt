package io.github.eightbrows.navhud.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** WP リストをクリアしたあとの画面の状態（20261102-D01）: リストを読み込んでいないとき（起動時の「リストなし」）と同じ。 */
class WaypointClearStateTest {

    @Test
    fun clearedStateIsTheSameAsNoList() {
        val loaded = WaypointUiState(
            listName = "wp_profile.csv", dirty = true, hasSavedList = true, startupPending = false,
            message = UiText(1),
        )
        val msg = UiText(2)
        val cleared = loaded.cleared(msg)
        // 起動時に「リストなし」を選んだとき（startupPending = false）と同じ。メッセージ（クリアしました）だけ違う
        assertEquals(WaypointUiState(startupPending = false, message = msg), cleared)
        assertNull(cleared.listName)
        assertFalse(cleared.dirty)
        // 前回のリストはない（起動時の選択の「前回のリスト」は押せない）
        assertFalse(cleared.hasSavedList)
    }

    /** WP ボタン列はいつも出す（20261108-D02）: 出す・出さないの状態を持たない */
    @Test
    fun wpStripHasNoOnOff() {
        assertTrue(WaypointUiState::class.java.declaredFields.none { it.name == "showButtons" })
        assertTrue(NavViewModel::class.java.declaredMethods.none { it.name == "toggleWaypointButtons" })
    }
}
