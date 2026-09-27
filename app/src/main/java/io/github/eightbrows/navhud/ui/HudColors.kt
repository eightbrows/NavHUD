package io.github.eightbrows.navhud.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.github.eightbrows.navhud.core.nav.ColorTheme
import io.github.eightbrows.navhud.core.view.Ink

/** テーマで変わる基本色（目盛り・距離環・文字・自機・有効な WP・見出し）。 */
data class HudPalette(
    val scale: Color,
    val scaleDim: Color,
    val caption: Color,
    val ownShip: Color,
    val wp: Color,
) {
    companion object {
        val WHITE = HudPalette(Color(0xFFE6E6E6), Color(0xFF9E9E9E), Color(0xFF8FA3B8), Color(0xFFFFFFFF), Color(0xFFFFFFFF))
        val GREEN = HudPalette(Color(0xFF3DF27A), Color(0xFF26A652), Color(0xFF6FBF8A), Color(0xFF7CFF9F), Color(0xFF7CFF9F))
        val AMBER = HudPalette(Color(0xFFFFB627), Color(0xFFB07A10), Color(0xFFC9A36A), Color(0xFFFFCB5C), Color(0xFFFFCB5C))

        fun of(theme: ColorTheme) = when (theme) {
            ColorTheme.WHITE -> WHITE
            ColorTheme.GREEN -> GREEN
            ColorTheme.AMBER -> AMBER
        }
    }
}

/**
 * HUD の色（航空機の ND の慣習に合わせる）。色はここ1か所で決める。
 * 基本色はテーマ（白 / 緑 / 琥珀）で切り替える。マゼンタ（次の WP）・黄と赤（警告）・グレー（無効・NO FIX）は固定。
 */
object HudColors {
    /** 今のテーマの基本色。Compose の状態なので、変えると画面が描き直される */
    var palette: HudPalette by mutableStateOf(HudPalette.WHITE)

    val Background = Color(0xFF000000)

    /** 目盛り・距離環・文字（テーマ） */
    val Scale: Color get() = palette.scale
    val ScaleDim: Color get() = palette.scaleDim

    /** 30° ごとの薄い方位線 */
    val BearingLine = Color(0xFF333333)

    /** REPLAY のトラック全体（テーマによらず暗いグレー） */
    val Track = Color(0xFF3A3A3A)

    /** 次の WP とそこへの線 */
    val Active = Color(0xFFFF4FD8)

    /** 有効な WP（テーマ） */
    val Wp: Color get() = palette.wp

    /** 無効な WP（破線） */
    val WpDisabled = Color(0xFF7A7A7A)

    /** 到達済みの WP */
    val WpReached = Color(0xFF4A4A4A)

    /** 自機（テーマ） */
    val OwnShip: Color get() = palette.ownShip

    /** NO FIX 中の最後の値 */
    val Stale = Color(0xFF6E6E6E)

    /** 数値の見出し（テーマ） */
    val Caption: Color get() = palette.caption

    /** 注意（締切が近いなど）と警告（NO FIX、締切超過） */
    val Caution = Color(0xFFFFC107)
    val Warning = Color(0xFFFF5252)

    /** 上部バー・下部パネルの枠 */
    val Frame = Color(0xFF2A2A2A)

    fun of(ink: Ink): Color = when (ink) {
        Ink.SCALE -> Scale
        Ink.SCALE_DIM -> ScaleDim
        Ink.BEARING_LINE -> BearingLine
        Ink.ACTIVE -> Active
        Ink.WP -> Wp
        Ink.WP_DISABLED -> WpDisabled
        Ink.WP_REACHED -> WpReached
        Ink.OWNSHIP -> OwnShip
        Ink.STALE -> Stale
        Ink.TRACK -> Track
        Ink.TRACK_DONE -> ScaleDim
    }
}
