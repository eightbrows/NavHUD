package io.github.eightbrows.navhud.core.view

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

    /** POSITION LOST 中の、最後の値（グレー） */
    STALE,
}

data class Segment(val a: P, val b: P, val ink: Ink, val dashed: Boolean = false, val bold: Boolean = false)

/** 円弧。角度は画面上の角度（上が 0、時計回り）。 */
data class Arc(val center: P, val radius: Float, val startDeg: Float, val sweepDeg: Float, val ink: Ink)

/** 文字。at は文字の中心。 */
data class Label(val text: String, val at: P, val ink: Ink, val small: Boolean = false)

/** 画面内の WP。 */
data class WpMark(val at: P, val name: String, val ink: Ink, val dashed: Boolean)

/** 画面外の WP を示す、表示枠の縁の矢印。angleDeg は矢印の向き（上が 0、時計回り）。 */
data class EdgeArrow(val at: P, val angleDeg: Float, val text: String, val textAt: P, val ink: Ink)

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
    val ownShip: OwnShip,
)

/** 描画の寸法 [px]。画面密度に合わせて ui 側で作る。 */
data class HudMetrics(
    val tickMinor: Float = 8f,
    val tickMajor: Float = 16f,
    val labelGap: Float = 12f,
    val edgeInset: Float = 22f,
    val arrowTextGap: Float = 34f,
    val arcOriginFromBottom: Float = 90f,
    val northUpMargin: Float = 48f,
    val pointerSize: Float = 14f,
    /** 矢印の文字の1行の高さ（重なったときにずらす量） */
    val arrowLabelLine: Float = 16f,
    /** 矢印の文字の1文字の幅の目安（等幅 11sp） */
    val labelCharWidth: Float = 7f,
) {
    fun scaled(k: Float) = HudMetrics(
        tickMinor * k, tickMajor * k, labelGap * k, edgeInset * k, arrowTextGap * k,
        arcOriginFromBottom * k, northUpMargin * k, pointerSize * k, arrowLabelLine * k, labelCharWidth * k,
    )
}

/** 地図の領域のうち、画面外の矢印を置かない帯の幅 [px]。 */
data class HudInsets(val left: Float = 0f, val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f)
