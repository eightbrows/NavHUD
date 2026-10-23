package io.github.eightbrows.navhud.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.nav.ColorTheme
import io.github.eightbrows.navhud.core.nav.TrackColor
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
 * - UI の色: 上部バー・数値・操作列・WP ボタン列・標高プロファイル・再生の操作列（下の Scale・Caption などの値）
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

    /** 背景地図（§8）: 海岸線（暗い灰色）と県境（明るめの灰色の太い点線）。HUD を邪魔しないよう、どのテーマでも同じ灰色 */
    val Coastline = Color(0xFF5C5C5C)
    /** 県境: 市区町村境（#787878 の細かい点線）より明るくし、太さ（2.0dp）と合わせて境の重さの順が分かるようにした（20261021-D01。前は #4A4A4A） */
    val PrefBoundary = Color(0xFF8A8A8A)

    /**
     * 背景地図のほかのレイヤ（暗い色。レイヤどうしは色と線の種類で見分ける）。河川・湖沼だけは見やすいよう明るい水色
     * （20261018-D05。読み込んだ軌跡の色の水色・青と紛れないよう、その間の色にした）
     */
    /**
     * 市区町村境: 細かい点線で描くので、線より明るい灰色にした（20261020-D02。前の #383838 は黒の背景とほとんど同じで、見えなかった）。
     * 県境（#8A8A8A の太い点線）とは、明るさと点の細かさ・太さで見分ける
     */
    val MuniBoundary = Color(0xFF787878)
    val MapWater = Color(0xFF6AB0E8)
    val MapRoad = Color(0xFF5A4530)
    /** 道路の色分け（20261018-D06）: 高速道路は濃くはっきりした青、その他の自動車専用道路は同じ青の系統で暗め、国道は落ち着いた赤 */
    val MapMotorway = Color(0xFF2E56F0)
    val MapOtherMotorway = Color(0xFF203CA8)
    val MapNationalRoad = Color(0xFFA8443C)
    val MapRail = Color(0xFF4F4A66)

    /** 新幹線: 在来線（くすんだ紫）と同じ系統の、明るい紫（仮。ほかの地図・HUD の色との ΔE は 24.9 以上） */
    val MapShinkansen = Color(0xFF9B7BC8)

    /**
     * 設定画面: 選ぶボタンの、選んでいる値の印（太い枠と右上の点）。水色（仮。白・緑・琥珀のどのテーマの塗り（初期値）と枠とも ΔE 40 以上離れる。
     * 20261020-D03 は初期値の印、20261021-D01 から選んでいる値の印）
     */
    val SettingsSelected = Color(0xFF4FC3F7)

    /** 押せないボタンの、選んでいる値の印（SettingsSelected の RGB に 0.55 を掛けた暗い水色。押せないボタンのグレーの文字・塗りと同じくらい暗い） */
    val SettingsSelectedDim = Color(0xFF2B6B88)
    val MapFerry = Color(0xFF22504F)
    /**
     * 空港の記号: 目立つ明るい黄緑（仮。20261021-D01）。距離環の緑・WP のマゼンタ・琥珀・白・警告の赤や、道路・鉄道・河川の色と ΔE 38 以上離れる
     * （いちばん近いのは軌跡の黄で 38.6、距離環の緑で 48.7）。線の明るさの段では変えない
     */
    val MapAirport = Color(0xFFB8FF00)

    /** 空港の敷地: 空港と同じ黄緑の半透明（不透明度 30%。黒の上では #374D00。仮。20261022-D01。20261023-D01 で 20% から濃くした） */
    val MapAirportArea = Color(0x4DB8FF00)

    /** 空港の ICAO の略号の文字: 空港と同じ黄緑（黒のふちを付けて描く。20261022-D01） */
    val MapAirportCode = Color(0xFFB8FF00)

    /**
     * 山頂（20261021-D02。仮）: 淡い桃色の三角（20261022-D01 で 3 段とも塗った三角にし、丸をなくした）。
     * 高（目印になる山、1,500 m 以上）#FFA8C6、中（目印になる山、1,500 m 未満）は RGB × 0.7（#B3768B）、低（目印になりにくい山）は × 0.5（#805463）。
     * 大きさも 12・10・8dp で変える。高は白のテーマの距離環と ΔE 38.4、新幹線と 40.2。高と中は ΔE 23.4、中と低は 16.8
     */
    val MapPeakHigh = Color(0xFFFFA8C6)
    val MapPeakMid = Color(0xFFB3768B)
    val MapPeakLow = Color(0xFF805463)

    /** 読み込んだ軌跡の色（設定 trackColor）。Compose の状態 */
    var trackColor: TrackColor by mutableStateOf(TrackColor.WHITE)

    /** 読み込んだ軌跡の明るさ [%]（設定 trackBrightnessPct）。Compose の状態 */
    var trackBrightnessPct: Int by mutableIntStateOf(Tuning.TRACK_BRIGHTNESS_DEFAULT_PCT)

    /** REPLAY のトラック全体（テーマによらない。色と明るさは設定。色の RGB に明るさ p / 100 を掛ける。白の 50% = 0x80 のグレー） */
    val Track: Color get() = trackFull(trackColor).let { c ->
        val k = trackBrightnessPct.coerceIn(0, 100) / 100f
        Color(red = c.red * k, green = c.green * k, blue = c.blue * k)
    }

    /**
     * 読み込んだ軌跡の色（明るさ 100%）。黒い地で見分けやすく、次の WP のマゼンタ・警告の赤（・黄）・地図の緑と紛れない色にする。
     * 白・緑・琥珀は基本色（HudPalette の自機・WP の色）と同じ。シアン・黄・青は軌跡だけの色
     */
    fun trackFull(c: TrackColor): Color = when (c) {
        TrackColor.WHITE -> Color(0xFFFFFFFF)
        TrackColor.GREEN -> HudPalette.GREEN.wp
        TrackColor.AMBER -> HudPalette.AMBER.wp
        // シアン: 色相 190°（地図の緑 140° から 50° 離す）
        TrackColor.CYAN -> Color(0xFF40E0FF)
        // 黄: 色相 55°（琥珀 40°・注意の黄 45° より緑寄りで明るい）
        TrackColor.YELLOW -> Color(0xFFFFF04A)
        // 明るめの青: 色相 220°（シアンから 30°、マゼンタ 315° から 95° 離す）
        TrackColor.BLUE -> Color(0xFF6E9BFF)
    }

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

    /** 背景地図の色に明るさ（RGB に掛ける数。1 を超えた分は 255 で止まる）を掛けた色 */
    fun ofMap(ink: Ink, brightness: Float): Color {
        val c = ofMap(ink)
        if (brightness == 1f) return c
        return Color(red = (c.red * brightness).coerceIn(0f, 1f), green = (c.green * brightness).coerceIn(0f, 1f), blue = (c.blue * brightness).coerceIn(0f, 1f), alpha = c.alpha)
    }

    /**
     * ランドマーク（空港・山頂）の色に、ランドマークの明るさの段の掛ける数 k（Tuning.BASEMAP_BRIGHTNESS_FACTORS）を掛けた色（20261022-D01）。
     * k ≤ 1 は RGB に k を掛ける（暗くする）。k > 1 は白に近づける（1 − (1 − c) ÷ k）。RGB に掛けると明るい色が 255 で止まり、
     * 山頂の 3 段の差がなくなるため。どちらも明るさの順（高 > 中 > 低）は変わらない。不透明度はそのまま
     */
    fun landmark(ink: Ink, k: Float): Color {
        val c = ofMap(ink)
        if (k == 1f) return c
        fun f(v: Float) = if (k <= 1f) v * k else 1f - (1f - v) / k
        return Color(red = f(c.red).coerceIn(0f, 1f), green = f(c.green).coerceIn(0f, 1f), blue = f(c.blue).coerceIn(0f, 1f), alpha = c.alpha)
    }

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
        Ink.COASTLINE -> Coastline
        Ink.PREF_BOUNDARY -> PrefBoundary
        Ink.MUNI_BOUNDARY -> MuniBoundary
        Ink.MAP_WATER -> MapWater
        Ink.MAP_ROAD -> MapRoad
        Ink.MAP_NATIONAL_ROAD -> MapNationalRoad
        Ink.MAP_MOTORWAY -> MapMotorway
        Ink.MAP_OTHER_MOTORWAY -> MapOtherMotorway
        Ink.MAP_RAIL -> MapRail
        Ink.MAP_SHINKANSEN -> MapShinkansen
        Ink.MAP_FERRY -> MapFerry
        Ink.MAP_AIRPORT -> MapAirport
        Ink.MAP_AIRPORT_AREA -> MapAirportArea
        Ink.MAP_AIRPORT_CODE -> MapAirportCode
        Ink.MAP_PEAK_LOW -> MapPeakLow
        Ink.MAP_PEAK_MID -> MapPeakMid
        Ink.MAP_PEAK_HIGH -> MapPeakHigh
    }
}
