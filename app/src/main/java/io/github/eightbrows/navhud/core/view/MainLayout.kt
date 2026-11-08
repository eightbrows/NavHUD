package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.model.SourceMode
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.SourceKind

/**
 * メイン画面のボタンの置き場所（20261108-D02）。Android に依存しない。
 * 上部バー = 「HDG ○○ ｜（まん中: NO FIX のときだけ NO FIX）｜ ARC ｜ ⚙」、
 * 左の列（軌跡の操作。数値の欄の下）= LIVE は ⇄ だけ、REPLAY は ⇄ / FILE / スライダー / ▶ / ＋ / ×N / −、
 * WP ボタン列 = いつも出す（左端に「WP」）。
 */
object MainLayout {

    /** 上部バーの左のボタン（方位ソースの選択） */
    fun headingLabel(mode: SourceMode): String = "HDG ${mode.name}"

    /** 上部バーの右のボタン（表示モード） */
    fun displayLabel(mode: DisplayMode): String = if (mode == DisplayMode.ARC) "ARC" else "N-UP"

    const val SETTINGS_LABEL = "⚙"
    const val NO_FIX_LABEL = "NO FIX"

    /** 方位ソースのボタンの文字のうち、いちばん長いもの（上部バーの幅を見積もるとき） */
    val LONGEST_HEADING: String = SourceMode.entries.map(::headingLabel).maxBy { it.length }
    val LONGEST_DISPLAY: String = DisplayMode.entries.map(::displayLabel).maxBy { it.length }

    /**
     * 上部バーの文字（左から）: 方位ソース・まん中の NO FIX・表示モード・⚙。
     * 文字の倍率（TopBarFit）は NO FIX があるものとして決める（NO FIX が出ても消えても、ボタンの大きさを変えない）。
     */
    fun topBarLabels(mode: SourceMode, display: DisplayMode): List<String> =
        listOf(headingLabel(mode), NO_FIX_LABEL, displayLabel(display), SETTINGS_LABEL)

    /**
     * まん中の NO FIX の左端 [dp か px]: 上部バーのまん中に置く。左右のボタンにかかるときは、その間のまん中に寄せる。
     * @param barW 上部バーの幅
     * @param leftEnd 左のボタン（HDG）の右端
     * @param rightStart 右のボタン（表示モード）の左端
     * @param itemW NO FIX の枠の幅
     * @param gap ボタンとの間にあけるすき間
     */
    fun centerStart(barW: Float, leftEnd: Float, rightStart: Float, itemW: Float, gap: Float): Float {
        val lo = leftEnd + gap
        val hi = rightStart - gap - itemW
        val centered = (barW - itemW) / 2
        return if (lo <= hi) centered.coerceIn(lo, hi) else (lo + rightStart - gap - itemW) / 2
    }

    /** 左の列（軌跡の操作）のボタンなど */
    enum class TrackControl {
        /** ⇄ LIVE / ⇄ REPLAY（タップで切り替え） */
        SOURCE,
        /** ファイルの画面を開く */
        FILE,
        /** 縦の再生位置のスライダー（残りの高さいっぱい） */
        SEEK,
        /** 再生 / 一時停止（▶ / ❚❚ / END） */
        PLAY,
        /** 倍速を 1 段速く */
        FASTER,
        /** 今の倍速（×1 など。表示だけ） */
        SPEED,
        /** 倍速を 1 段遅く */
        SLOWER,
    }

    /** 左の列の上からの並び。LIVE は ⇄ だけ、REPLAY は ⇄ の下に FILE、その下は前の再生の操作列と同じ */
    fun trackControls(kind: SourceKind): List<TrackControl> = when (kind) {
        SourceKind.LIVE -> listOf(TrackControl.SOURCE)
        SourceKind.REPLAY -> TrackControl.entries.toList()
    }

    /** 左の列が数値の下から WP ボタン列の上までの高さを使うか（REPLAY。スライダーで残りの高さを使う） */
    fun trackColumnFillsHeight(kind: SourceKind): Boolean = TrackControl.SEEK in trackControls(kind)

    /**
     * 地図の操作（PAN・ピンチ・ダブルタップ・2 本指のタップ）を受け付ける所。
     * area の中で、blocked のどれかで指を置いたタッチは地図の操作にしない。
     */
    data class Gestures(val area: HudRect, val blocked: List<HudRect>) {
        /** この所で指を置いたら、地図の操作になるか */
        fun accepts(x: Float, y: Float): Boolean {
            val p = P(x, y)
            return area.contains(p) && blocked.none { it.contains(p) }
        }
    }

    /**
     * 地図の操作を受け付ける所: 上部バーと数値の欄より下、WP ボタン列より上。その中で、左の列（軌跡の操作）と
     * 右の操作列（− / 縮尺 / ＋）の所では始めない（ボタンの間のすき間で地図が動かないように）。
     * @param width 画面の幅 [px]
     * @param top 数値の欄の下端 [px]
     * @param stripTop WP ボタン列の上端 [px]
     * @param trackColumn 左の列の矩形 [px]（LIVE は ⇄ だけの高さ、REPLAY は WP ボタン列の上まで）
     * @param sideColumn 右の操作列の矩形 [px]
     */
    fun gestures(width: Float, top: Float, stripTop: Float, trackColumn: HudRect, sideColumn: HudRect): Gestures =
        Gestures(HudRect(0f, top, width, stripTop), listOf(trackColumn, sideColumn))

    /**
     * 読み込みの知らせの右端 [px]: 知らせは WP ボタン列のすぐ上に、左の列の右から画面の右端までの幅で出す。
     * その幅で折り返した高さで、右の操作列（− / 縮尺 / ＋）の下端より上まで届くなら、右の操作列の左までにする（重ねない）。
     * @param heightAt 幅 [px] で折り返したときの知らせの高さ [px]
     */
    fun noticeRight(
        left: Float,
        width: Float,
        bottom: Float,
        sideColumn: HudRect,
        heightAt: (Float) -> Float,
    ): Float {
        val full = width
        return if (bottom - heightAt(full - left) >= sideColumn.bottom) full else minOf(full, sideColumn.left)
    }
}
