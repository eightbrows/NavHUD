package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.model.SourceMode

enum class DisplayMode { ARC, NORTH_UP }

enum class ScreenSide { LEFT, RIGHT }

enum class ProfileSize { SMALL, MEDIUM, LARGE }

/** 仕様の設定値（既定値つき）。保存はまだしない。 */
data class NavSettings(
    /** 方位ソース（§5.2） */
    val sourceMode: SourceMode = SourceMode.HYBRID,
    /** GPS 方位を使う最低速度 [m/s]（§5.2） */
    val minGpsSpeedMps: Float = 1.4f,
    /** GPS 方位を使う最大の水平精度 [m]（§5.2） */
    val maxGpsAccM: Float = 15f,
    /** 到達半径 [m]。50 / 100 / 200 / 500 から選ぶ（§5.4） */
    val reachRadiusM: Double = 100.0,
    /** 通過判定（§5.4 のオプション）。最接近後に離れていったら到達とみなす */
    val passDetection: Boolean = true,
    /** 通過判定: 最接近距離の上限 [m] */
    val passMaxApproachM: Double = 300.0,
    /** 通過判定: 最接近距離からこれだけ離れたら「離れた」[m] */
    val passDepartM: Double = 50.0,
    /** 通過判定: 離れた状態がこれだけ続いたら到達 [秒] */
    val passHoldSec: Int = 5,
    /** RATE の窓 [秒]。10 / 30 / 60 から選ぶ（§5.3） */
    val rateWindowSec: Int = 60,
    /** NO FIX とみなす秒数（§5.5） */
    val noFixTimeoutSec: Int = 10,
    /** 標高オフセット [m]。標高 = 楕円体高 − これ（§6.8） */
    val altOffsetM: Double = 36.0,
    /** 表示モード（§6.1） */
    val displayMode: DisplayMode = DisplayMode.ARC,
    /** 距離環の間隔 [m]（§6.1） */
    val ringIntervalM: Double = 1_000.0,
    /** ARC モードの基準距離環 [m]（§6.2） */
    val arcRangeM: Double = 2_000.0,
    /** WP ボタン列を置く側（§6.4） */
    val wpButtonsSide: ScreenSide = ScreenSide.RIGHT,
    /** 標高プロファイルの表示サイズ（§6.6） */
    val profileSize: ProfileSize = ProfileSize.MEDIUM,
    /** 画面常時点灯（§6.8） */
    val keepScreenOn: Boolean = true,
) {
    companion object {
        val REACH_RADIUS_CHOICES_M = listOf(50.0, 100.0, 200.0, 500.0)
        val RATE_WINDOW_CHOICES_SEC = listOf(10, 30, 60)
    }
}
