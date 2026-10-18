package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.Tuning

/** 描く要素の役割。実際の色は ui 側（HudColors）で1か所で決める。 */
enum class Ink {
    /** 目盛り・距離環・文字（白〜グレー） */
    SCALE,

    /** 控えめなもの（標高プロファイルの標高の数字など） */
    SCALE_DIM,

    /** 距離環の数字（UI の色。不透明度はボタンと同じ） */
    RING_LABEL,

    /** 30° ごとの薄い方位線（暗いグレー） */
    BEARING_LINE,

    /** 次の WP とそこへの線（マゼンタ） */
    ACTIVE,

    /** 有効な WP（白） */
    WP,

    /** 無効な WP（グレーの破線） */
    WP_DISABLED,

    /** 到達済みの WP（暗め） */
    WP_REACHED,

    /** 自機・ラバーライン・機首方位の三角（白） */
    OWNSHIP,

    /** NO FIX 中の、最後の値（グレー） */
    STALE,

    /** REPLAY のトラック全体（暗いグレーの細線） */
    TRACK,

    /** REPLAY の再生済みの部分・LIVE の軌跡（テーマの薄い色） */
    TRACK_DONE,

    /** 背景地図の海岸線（暗い灰色の実線。§8） */
    COASTLINE,

    /** 背景地図の県境（海岸線よりさらに暗い点線。§8） */
    PREF_BOUNDARY,

    /** 背景地図の市区町村境（県境よりさらに暗い細かい点線） */
    MUNI_BOUNDARY,

    /** 背景地図の河川・湖沼の輪郭（暗い青） */
    MAP_WATER,

    /** 背景地図の道路: 県道（暗い茶色）・国道（落ち着いた赤）・高速道路（青）・その他の自動車専用道路（暗めの青） */
    MAP_ROAD,
    MAP_NATIONAL_ROAD,
    MAP_MOTORWAY,
    MAP_OTHER_MOTORWAY,

    /** 背景地図の鉄道（暗い紫がかった灰色の破線） */
    MAP_RAIL,

    /** 背景地図のフェリーの航路（暗い青緑の長い破線） */
    MAP_FERRY,

    /** 背景地図のダム・堰（線と点。暗い水色） */
    MAP_DAM,

    /** 背景地図の市街地の輪郭（暗いオリーブ色） */
    MAP_BUILT_UP,

    /** 背景地図の点: 都市・駅・港・空港（ダムは MAP_DAM） */
    MAP_CITY,
    MAP_STATION,
    MAP_PORT,
    MAP_AIRPORT,
}

/** 折れ線の線の種類（背景地図のレイヤの描き分け）。長さは Tuning.BASEMAP_*_DP */
enum class LineDash { SOLID, DOT, FINE_DOT, DASH, LONG_DASH }

/** 背景地図の点の記号の形。PLANE は上から見た飛行機（機首が上。向きは変えない） */
enum class MarkShape { CIRCLE, SQUARE, DIAMOND, TRIANGLE, PLANE }

/**
 * 背景地図の点（小さな記号。名前は描かない）。sizeDp は記号の半分の大きさ [dp]、filled は塗るか、
 * brightness は色の RGB に掛ける数（設定の明るさ。Tuning.BASEMAP_BRIGHTNESS_FACTORS）
 */
data class MapMark(val at: P, val ink: Ink, val shape: MarkShape, val sizeDp: Float, val filled: Boolean, val brightness: Float = 1f)

/** 線。dashed は無効 WP の破線、longDash は次の WP への方位線の長い破線（Tuning.ACTIVE_DASH_*）。 */
data class Segment(val a: P, val b: P, val ink: Ink, val dashed: Boolean = false, val bold: Boolean = false, val longDash: Boolean = false)

/** 円弧。角度は画面上の角度（上が 0、時計回り）。 */
data class Arc(val center: P, val radius: Float, val startDeg: Float, val sweepDeg: Float, val ink: Ink)

/** 文字。at は文字の中心。 */
data class Label(val text: String, val at: P, val ink: Ink, val small: Boolean = false)

/**
 * 画面内の WP。lines は印の近くに出す文字（次の WP は 名前・方位 の2行、ほかは名前だけ）。nameAt は文字の塊の中心
 * （自機の記号などと重なるなら null で、文字を描かない）。linePx は1行の高さ [px]。
 */
data class WpMark(
    val at: P,
    val name: String,
    val ink: Ink,
    val dashed: Boolean,
    val nameAt: P? = null,
    val lines: List<String> = listOf(name),
    val linePx: Float = 0f,
    /** 次の WP か（描く順番に使う。NO FIX 中は色がグレーになるので、色では見分けない） */
    val next: Boolean = false,
)

/**
 * 画面外の次の WP の文字（三角は描かない）。at は自機から WP への線が矢印の枠の縁に当たる点（文字の位置の基準）。
 * lines は上から 距離・名前・方位、textAt は文字の塊の中心、linePx は1行の高さ [px]。
 */
data class EdgeArrow(val at: P, val lines: List<String>, val textAt: P, val ink: Ink, val linePx: Float = 0f) {
    /** 1行にまとめた文字（テスト・ログ用） */
    val text: String get() = lines.joinToString(" ")
}

/**
 * 折れ線（軌跡・背景地図）。widthDp は線の太さ [dp]。dash は線の種類（背景地図のレイヤの描き分け）。
 * brightness は色の RGB に掛ける数（背景地図の設定の明るさ。Tuning.BASEMAP_BRIGHTNESS_FACTORS）。
 */
data class Polyline(val points: List<P>, val ink: Ink, val widthDp: Float, val dash: LineDash = LineDash.SOLID, val brightness: Float = 1f)

/** 三角形の印。tip の向きが angleDeg。 */
data class Pointer(val tip: P, val angleDeg: Float, val sizePx: Float, val ink: Ink)

/** 自機。angleDeg が null なら方位なし（丸で描く）。 */
data class OwnShip(val at: P, val angleDeg: Float?, val ink: Ink)

/** Canvas が描くものすべて（座標は計算済み）。 */
data class HudScene(
    val rect: HudRect,
    val arcs: List<Arc>,
    val segments: List<Segment>,
    val labels: List<Label>,
    val wpMarks: List<WpMark>,
    val arrows: List<EdgeArrow>,
    val pointers: List<Pointer>,
    /** 自機。PAN 中で Fix がなければ null */
    val ownShip: OwnShip?,
    /** 軌跡（WP より下に描く） */
    val trails: List<Polyline> = emptyList(),
    /** 背景地図の線（いちばん下に描く。レイヤの描く順に並んでいる。§8） */
    val baseMap: List<Polyline> = emptyList(),
    /** 背景地図の点（線の上、距離環より下に描く） */
    val baseMapMarks: List<MapMark> = emptyList(),
) {
    // 描く順番（§6.1。下から）: 背景地図（線 → 点）→ 距離環 → 軌跡 → baseSegments → labels（方位目盛りの文字・距離環の数字）→ otherWpMarks
    // → pointers（方位の三角）→ nextWpSegments → nextWpMarks → arrows（画面外の次の WP の文字）→ 自機。
    // 次の WP の情報（線・印・文字）は、方位の三角・方位目盛りの文字・距離環の数字より上に描く

    /** 自機から次の WP への線（長い破線） */
    val nextWpSegments: List<Segment> get() = segments.filter { it.longDash }

    /** 次の WP への線を除いた線（方位目盛り・方位線・ラバーライン・WP を結ぶ線） */
    val baseSegments: List<Segment> get() = segments.filter { !it.longDash }

    /** 次の WP の印と文字 */
    val nextWpMarks: List<WpMark> get() = wpMarks.filter { it.next }

    /** 次の WP を除いた WP の印と名前 */
    val otherWpMarks: List<WpMark> get() = wpMarks.filter { !it.next }
}

/** 描画の寸法 [px]。画面密度に合わせて ui 側で作る。 */
data class HudMetrics(
    val tickMinor: Float = Tuning.HUD_TICK_MINOR_DP,
    val tickMajor: Float = Tuning.HUD_TICK_MAJOR_DP,
    val labelGap: Float = Tuning.HUD_LABEL_GAP_DP,
    val edgeInset: Float = Tuning.HUD_EDGE_INSET_DP,
    val arrowTextGap: Float = Tuning.HUD_ARROW_TEXT_GAP_DP,
    /** ARC の自機の位置（回避枠の下端 = WP ボタン列の上端からの距離）: 標準 */
    val arcOriginFromBottom: Float = Tuning.HUD_ARC_ORIGIN_DP,
    /** ARC の自機の位置: 高め。後方の WP・矢印に余裕を持たせる */
    val arcOriginFromBottomHigh: Float = Tuning.HUD_ARC_ORIGIN_HIGH_DP,
    /** ARC の自機の位置: さらに高め */
    val arcOriginFromBottomHigher: Float = Tuning.HUD_ARC_ORIGIN_HIGHER_DP,
    /** North Up: 縮尺の距離環と画面の端・回避枠の上下の間の余白 */
    val northUpEdgeMargin: Float = Tuning.HUD_NORTH_UP_EDGE_MARGIN_DP,
    val pointerSize: Float = Tuning.HUD_POINTER_DP,
    /** 矢印の文字の1行の高さ（重なったときにずらす量） */
    val arrowLabelLine: Float = Tuning.HUD_ARROW_LABEL_LINE_DP,
    /** 矢印の文字の1文字の幅の目安（等幅 11sp） */
    val labelCharWidth: Float = Tuning.HUD_LABEL_CHAR_WIDTH_DP,
    /** AUTO 縮尺: 次の WP を、矢印の枠からさらにこれだけ内側に収める（名前の文字の分） */
    val fitMargin: Float = Tuning.HUD_FIT_MARGIN_DP,
    /** 自機の記号の大きさの目安（半幅・半高）。WP の名前・矢印の文字はここを避ける */
    val ownShipClear: Float = Tuning.HUD_OWN_SHIP_CLEAR_DP,
    /** WP の印の中心から名前の中心まで（上、入らなければ下） */
    val wpNameOffset: Float = Tuning.HUD_WP_NAME_OFFSET_DP,
    /** 方位目盛りの文字の大きさの目安（矢印の文字を避けるときに使う）: 半幅・半高 */
    val compassLabelHalf: Float = Tuning.HUD_COMPASS_LABEL_HALF_DP,
    /** AUTO: 見えている隣り合う目標どうしの、画面上の最小の間隔 */
    val wpMinSep: Float = Tuning.AUTO_WP_MIN_SEP_DP,
) {
    fun scaled(k: Float) = HudMetrics(
        tickMinor * k, tickMajor * k, labelGap * k, edgeInset * k, arrowTextGap * k,
        arcOriginFromBottom * k, arcOriginFromBottomHigh * k, arcOriginFromBottomHigher * k, northUpEdgeMargin * k, pointerSize * k,
        arrowLabelLine * k, labelCharWidth * k, fitMargin * k, ownShipClear * k, wpNameOffset * k, compassLabelHalf * k,
        wpMinSep * k,
    )
}

/**
 * 地図の上に重ねた表示の大きさ [px]。上: 上部バー・数値、右: 操作列の幅、下: WP ボタン列から下（プロファイル・
 * ナビゲーションバー）、左: 再生の操作列の右端（REPLAY のときだけ）。rightSpan は操作列のある高さの範囲（描画の枠の座標）。null なら上の表示の下端から下の表示の上端まで。
 */
data class HudInsets(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
    val rightSpan: ClosedFloatingPointRange<Float>? = null,
)
