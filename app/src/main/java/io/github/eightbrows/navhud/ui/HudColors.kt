package io.github.eightbrows.navhud.ui

import androidx.compose.ui.graphics.Color
import io.github.eightbrows.navhud.core.view.Ink

/** HUD の色（航空機の ND の慣習に合わせる）。色はここ1か所で決める。 */
object HudColors {
    val Background = Color(0xFF000000)

    /** 目盛り・距離環・文字 */
    val Scale = Color(0xFFE6E6E6)
    val ScaleDim = Color(0xFF9E9E9E)

    /** 30° ごとの薄い方位線 */
    val BearingLine = Color(0xFF333333)

    /** 次の WP とそこへの線 */
    val Active = Color(0xFFFF4FD8)

    /** 有効な WP */
    val Wp = Color(0xFFFFFFFF)

    /** 無効な WP（破線） */
    val WpDisabled = Color(0xFF7A7A7A)

    /** 到達済みの WP */
    val WpReached = Color(0xFF4A4A4A)

    /** 自機 */
    val OwnShip = Color(0xFFFFFFFF)

    /** LOST 中の最後の値 */
    val Stale = Color(0xFF6E6E6E)

    /** 数値の見出し */
    val Caption = Color(0xFF8FA3B8)

    /** 注意（締切が近いなど）と警告（POSITION LOST、締切超過） */
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
    }
}
