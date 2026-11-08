package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.model.SourceMode
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.view.MainLayout.TrackControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** メイン画面のボタンの置き場所（20261108-D02）: 上部バー・左の列・地図の操作を受け付けない所・WP ボタン列。 */
class MainLayoutTest {

    // ---- 上部バー ----

    @Test
    fun topBarIsHeadingNoFixDisplayAndSettings() {
        // 左から HDG ○○・（まん中）NO FIX・表示モード・⚙。WP・⇄・FILE は上部バーにない
        assertEquals(listOf("HDG GPS", "NO FIX", "ARC", "⚙"), MainLayout.topBarLabels(SourceMode.GPS, DisplayMode.ARC))
        assertEquals(listOf("HDG HYBRID", "NO FIX", "N-UP", "⚙"), MainLayout.topBarLabels(SourceMode.HYBRID, DisplayMode.NORTH_UP))
        for (m in SourceMode.entries) for (d in DisplayMode.entries) {
            val labels = MainLayout.topBarLabels(m, d)
            assertTrue(labels.none { it == "WP" || it.startsWith("⇄") || it == "FILE" })
        }
        assertEquals("HDG COMPASS", MainLayout.LONGEST_HEADING)
        assertEquals("N-UP", MainLayout.LONGEST_DISPLAY)
    }

    /** 幅 360dp の画面: 文字を縮めずに入り、まん中の NO FIX は左右のボタンにかからない */
    @Test
    fun topBarFitsIn360dpWithoutShrinking() {
        // 等幅の文字 13sp の 1 文字 ≒ 7.8dp（0.6em）。ボタンの内側の左右の余白 10dp × 0.6、枠と丸めの分 3dp（MainScreen と同じ見積もり）
        val charDp = 7.8f
        val labels = listOf(MainLayout.LONGEST_HEADING, MainLayout.NO_FIX_LABEL, MainLayout.LONGEST_DISPLAY, MainLayout.SETTINGS_LABEL)
        fun w(s: String) = s.length * charDp
        val pad = 10f * 0.6f * 2 + 3f
        val textDp = labels.sumOf { w(it).toDouble() }.toFloat()
        val fixedDp = labels.size * pad + 2f * 2 + 4f * (labels.size - 1) + 8f * 2
        assertEquals(1f, TopBarFit.textScale(360f, textDp, fixedDp), 0f)
        // 置き方: 左右の余白 8dp を除いた 344dp の中で
        val barW = 360f - 8f * 2
        val left = w(MainLayout.LONGEST_HEADING) + pad
        val right = w(MainLayout.LONGEST_DISPLAY) + pad + 4f + w(MainLayout.SETTINGS_LABEL) + pad
        val center = w(MainLayout.NO_FIX_LABEL) + pad + 2f * 2
        val x = MainLayout.centerStart(barW, left, barW - right, center, 4f)
        // 上部バーのまん中
        assertEquals(barW / 2, x + center / 2, 0.01f)
        assertTrue(x >= left + 4f)
        assertTrue(x + center <= barW - right - 4f)
        // 前の上部バー（HDG・WP・⇄ REPLAY・FILE・表示モード・⚙）より、文字の幅が短い
        val before = listOf(MainLayout.LONGEST_HEADING, "WP", "⇄ REPLAY", "FILE", MainLayout.LONGEST_DISPLAY, "⚙")
        assertTrue(labels.sumOf { it.length } - MainLayout.NO_FIX_LABEL.length < before.sumOf { it.length })
    }

    @Test
    fun noFixMovesBetweenTheButtonsWhenTheMiddleIsTaken() {
        // まん中に置くと左のボタンにかかる → 左のボタンの右（すき間 4）から
        assertEquals(124f, MainLayout.centerStart(barW = 300f, leftEnd = 120f, rightStart = 260f, itemW = 70f, gap = 4f), 0f)
        // 右のボタンにかかる → 右のボタンの左に寄せる
        assertEquals(106f, MainLayout.centerStart(barW = 300f, leftEnd = 40f, rightStart = 180f, itemW = 70f, gap = 4f), 0f)
        // 間が足りない（縮めても入らない画面）→ 間のまん中
        assertEquals(100f, MainLayout.centerStart(barW = 300f, leftEnd = 120f, rightStart = 150f, itemW = 70f, gap = 4f), 0f)
    }

    // ---- 左の列（軌跡の操作） ----

    @Test
    fun trackColumnOrder() {
        assertEquals(listOf(TrackControl.SOURCE), MainLayout.trackControls(SourceKind.LIVE))
        assertEquals(
            listOf(
                TrackControl.SOURCE, TrackControl.FILE, TrackControl.SEEK, TrackControl.PLAY,
                TrackControl.FASTER, TrackControl.SPEED, TrackControl.SLOWER,
            ),
            MainLayout.trackControls(SourceKind.REPLAY),
        )
        // スライダーで残りの高さを使うのは REPLAY だけ（WP ボタン列の上まで）
        assertFalse(MainLayout.trackColumnFillsHeight(SourceKind.LIVE))
        assertTrue(MainLayout.trackColumnFillsHeight(SourceKind.REPLAY))
        // FILE は ⇄ のすぐ下、再生の操作は前のまま（スライダー・▶・＋・×N・−）
        val r = MainLayout.trackControls(SourceKind.REPLAY)
        assertEquals(r.indexOf(TrackControl.SOURCE) + 1, r.indexOf(TrackControl.FILE))
    }

    // ---- 地図の操作を受け付けない所 ----

    @Test
    fun mapGesturesAreNotAcceptedOnTheColumns() {
        // 幅 720px・数値の下端 280px・WP ボタン列の上端 1150px。左の列 x 16〜136、右の操作列 x 600〜720・y 560〜920
        val side = HudRect(600f, 560f, 720f, 920f)
        // REPLAY: 左の列は WP ボタン列の上まで
        val replay = MainLayout.gestures(720f, 280f, 1150f, HudRect(16f, 280f, 136f, 1150f), side)
        assertFalse(replay.accepts(70f, 300f)) // ⇄
        assertFalse(replay.accepts(70f, 600f)) // スライダー
        assertFalse(replay.accepts(70f, 1140f)) // −
        assertFalse(replay.accepts(650f, 700f)) // 右の操作列
        assertTrue(replay.accepts(360f, 700f)) // 地図のまん中
        assertTrue(replay.accepts(140f, 700f)) // 左の列のすぐ右
        assertTrue(replay.accepts(650f, 1000f)) // 右の操作列の下
        // 上部バー・数値の欄・WP ボタン列の上では、地図の操作はしない
        assertFalse(replay.accepts(360f, 100f))
        assertFalse(replay.accepts(360f, 1200f))
        // LIVE: 左の列は ⇄ だけ（数値の下の 1 つ）。その下は地図
        val live = MainLayout.gestures(720f, 280f, 1150f, HudRect(16f, 280f, 136f, 380f), side)
        assertFalse(live.accepts(70f, 330f))
        assertTrue(live.accepts(70f, 600f))
    }

    // ---- 読み込みの知らせ ----

    @Test
    fun noticeAvoidsTheTrackColumnAndTheRangeButtons() {
        val side = HudRect(600f, 560f, 720f, 920f)
        // 1 行（高さ 60）なら、右の操作列より下に収まる → 画面の右端まで
        assertEquals(704f, MainLayout.noticeRight(left = 152f, width = 704f, bottom = 1134f, sideColumn = side) { 60f }, 0f)
        // 長くて折り返し、右の操作列の高さまで届く → 右の操作列の左まで
        assertEquals(600f, MainLayout.noticeRight(left = 152f, width = 704f, bottom = 1134f, sideColumn = side) { 300f }, 0f)
        // 折り返しの幅は「左の列の右から右端まで」で見積もる
        var asked = 0f
        MainLayout.noticeRight(left = 152f, width = 704f, bottom = 1134f, sideColumn = side) { asked = it; 10f }
        assertEquals(552f, asked, 0f)
    }

    // ---- WP ボタン列 ----

    @Test
    fun wpStripIsAlwaysShown() {
        // WP リストがないときは「WP」だけ
        assertEquals(listOf(WpStripLayout.Item.Settings), WpStripLayout.items(0))
        assertEquals(
            listOf(WpStripLayout.Item.Settings, WpStripLayout.Item.Wp(0), WpStripLayout.Item.Wp(1), WpStripLayout.Item.Wp(2)),
            WpStripLayout.items(3),
        )
    }
}
