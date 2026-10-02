package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.Tuning

/** 描く要素の役割。実際の色は ui 側（HudColors）で1か所で決める。 */
enum class Ink {
    /** 目盛り・距離環・文字（白〜グレー） */
    SCALE,

    /** 距離環の文字など控えめなもの */
    SCALE_DIM,

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
}

data class Segment(val a: P, val b: P, val ink: Ink, val dashed: Boolean = false, val bold: Boolean = false)

/** 円弧。角度は画面上の角度（上が 0、時計回り）。 */
data class Arc(val center: P, val radius: Float, val startDeg: Float, val sweepDeg: Float, val ink: Ink)

/** 文字。at は文字の中心。 */
data class Label(val text: String, val at: P, val ink: Ink, val small: Boolean = false)

/** 画面内の WP。nameAt は名前の中心（自機の記号と重なるなら null で、名前を描かない）。 */
data class WpMark(val at: P, val name: String, val ink: Ink, val dashed: Boolean, val nameAt: P? = null)

/** 画面外の WP を示す、表示枠の縁の矢印。angleDeg は矢印の向き（上が 0、時計回り）。 */
data class EdgeArrow(val at: P, val angleDeg: Float, val text: String, val textAt: P, val ink: Ink)

/** 折れ線（軌跡）。widthDp は線の太さ [dp]。 */
data class Polyline(val points: List<P>, val ink: Ink, val widthDp: Float)

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
)

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
    /** North Up: 方位サークルと画面の端・回避枠の上下の間の余白 */
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
    /** 距離環の文字の位置（距離環から外側へ） */
    val ringLabelOffset: Float = Tuning.RING_LABEL_OFFSET_DP,
) {
    fun scaled(k: Float) = HudMetrics(
        tickMinor * k, tickMajor * k, labelGap * k, edgeInset * k, arrowTextGap * k,
        arcOriginFromBottom * k, arcOriginFromBottomHigh * k, northUpEdgeMargin * k, pointerSize * k,
        arrowLabelLine * k, labelCharWidth * k, fitMargin * k, ownShipClear * k, wpNameOffset * k, compassLabelHalf * k,
        ringLabelOffset * k,
    )
}

/**
 * 地図の上に重ねた表示の大きさ [px]。上: 上部バー・数値、右: 操作列の幅、下: WP ボタン列から下（プロファイル・再生の帯・
 * ナビゲーションバー）。rightSpan は操作列のある高さの範囲（描画の枠の座標）。null なら上の表示の下端から下の表示の上端まで。
 */
data class HudInsets(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
    val rightSpan: ClosedFloatingPointRange<Float>? = null,
)
