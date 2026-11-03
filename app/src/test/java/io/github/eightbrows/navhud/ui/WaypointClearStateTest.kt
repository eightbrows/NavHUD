package io.github.eightbrows.navhud.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** WP リストをクリアしたあとの画面の状態（20261102-D01）: リストを読み込んでいないとき（起動時の「リストなし」）と同じ。 */
class WaypointClearStateTest {

    @Test
    fun clearedStateIsTheSameAsNoList() {
        val loaded = WaypointUiState(
            listName = "wp_profile.csv", dirty = true, hasSavedList = true, startupPending = false, showButtons = false,
            message = UiText(1),
        )
        val msg = UiText(2)
        val cleared = loaded.cleared(msg)
        // 起動時に「リストなし」を選んだとき（startupPending = false）と同じ。メッセージ（クリアしました）だけ違う
        assertEquals(WaypointUiState(startupPending = false, showButtons = false, message = msg), cleared)
        assertNull(cleared.listName)
        assertFalse(cleared.dirty)
        // 前回のリストはない（起動時の選択の「前回のリスト」は押せない）
        assertFalse(cleared.hasSavedList)
        // WP ボタン列の表示 / 非表示は、そのまま
        assertFalse(cleared.showButtons)
        assertEquals(true, loaded.copy(showButtons = true).cleared(null).showButtons)
    }
}
