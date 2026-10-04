package io.github.eightbrows.navhud.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.github.eightbrows.navhud.core.Tuning
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
 * 基本色は「UI の色」と「地図の色」の2組（それぞれ 白 / 緑 / 琥珀）。マゼンタ（次の WP）・黄と赤（警告）・グレー（無効・NO FIX）は固定。
 * - UI の色: 上部バー・数値・操作列・WP ボタン列・標高プロファイル・再生の帯（下の Scale・Caption などの値）
 * - 地図の色: 地図の Canvas に描くもの（ofMap）
 */
object HudColors {
    /** UI の基本色。Compose の状態なので、変えると画面が描き直される */
    var uiPalette: HudPalette by mutableStateOf(HudPalette.WHITE)

    /** 地図の基本色 */
    var mapPalette: HudPalette by mutableStateOf(HudPalette.GREEN)

    private val palette: HudPalette get() = uiPalette

    val Background = Color(0xFF000000)

    /** 目盛り・距離環・文字（テーマ） */
    val Scale: Color get() = palette.scale
    val ScaleDim: Color get() = palette.scaleDim

    /** 30° ごとの薄い方位線 */
    val BearingLine = Color(0xFF333333)

    /** 読み込んだ軌跡の明るさ [%]（設定 trackBrightnessPct）。Compose の状態 */
    var trackBrightnessPct: Int by mutableStateOf(Tuning.TRACK_BRIGHTNESS_DEFAULT_PCT)

    /** REPLAY のトラック全体（テーマによらずグレー。明るさは設定。100% = 白） */
    val Track: Color get() = gray(0xFF * trackBrightnessPct.coerceIn(0, 100) / 100)

    /** 次の WP とそこへの線 */
    val Active = Color(0xFFFF4FD8)

    /** 有効な WP（テーマ） */
    val Wp: Color get() = palette.wp

    /** 無効な WP（破線） */
    val WpDisabled = Color(0xFF7A7A7A)

    /** 到達済みの WP（WP ボタン列・押せないボタンなど UI のグレー） */
    val WpReached = Color(0xFF4A4A4A)

    /** 地図上の到達済みの WP（印・名前・線）。UI の WpReached より少し明るい（Tuning.WP_REACHED_MAP_GRAY） */
    val WpReachedMap = gray(Tuning.WP_REACHED_MAP_GRAY)

    /** 自機（テーマ） */
    val OwnShip: Color get() = palette.ownShip

    /** NO FIX 中の最後の値 */
    val Stale = Color(0xFF6E6E6E)

    /** 数値の見出し（テーマ） */
    val Caption: Color get() = palette.caption

    /** 注意（締切が近いなど）と警告（NO FIX、締切超過） */
    val Caution = Color(0xFFFFC107)
    val Warning = Color(0xFFFF5252)

    /** 上部バーなどの枠 */
    val Frame = Color(0xFF2A2A2A)

    /** 描く要素の色（UI の色。標高プロファイルなど） */
    fun of(ink: Ink): Color = of(ink, uiPalette, map = false)

    /** 描く要素の色（地図の色。地図の Canvas） */
    fun ofMap(ink: Ink): Color = of(ink, mapPalette, map = true)

    private fun gray(v: Int): Color = Color(red = v, green = v, blue = v)

    private fun of(ink: Ink, p: HudPalette, map: Boolean): Color = when (ink) {
        Ink.SCALE -> p.scale
        Ink.SCALE_DIM -> p.scaleDim
        // 距離環の数字は地図の上でも UI の色（ボタンと同じ）
        Ink.RING_LABEL -> uiPalette.scale
        Ink.BEARING_LINE -> BearingLine
        Ink.ACTIVE -> Active
        Ink.WP -> p.wp
        Ink.WP_DISABLED -> WpDisabled
        Ink.WP_REACHED -> if (map) WpReachedMap else WpReached
        Ink.OWNSHIP -> p.ownShip
        Ink.STALE -> Stale
        Ink.TRACK -> Track
        Ink.TRACK_DONE -> p.scaleDim
    }
}
