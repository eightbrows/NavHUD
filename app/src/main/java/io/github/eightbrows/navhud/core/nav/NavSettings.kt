package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.model.SourceMode

enum class DisplayMode { ARC, NORTH_UP }

/** 基本色（UI の色・地図の色それぞれに選ぶ）。マゼンタ・警告色・グレーは固定 */
enum class ColorTheme { WHITE, GREEN, AMBER }

/** ARC の自機の位置（WP ボタン列の上端からの距離）。高めは後方の WP・矢印に余裕を持たせる */
enum class OwnshipPosition { STANDARD, HIGH }

/** 標高プロファイルの表示サイズ（§6.6） */
enum class ProfileSize { OFF, SMALL, MEDIUM, LARGE }

/** 仕様の設定値（既定値つき）。保存はまだしない。 */
data class NavSettings(
    /** 方位ソース（§5.2）。既定は GPS（車内ではコンパスが不安定なため。HYBRID と COMPASS は歩行用） */
    val sourceMode: SourceMode = SourceMode.GPS,
    /** GPS 方位の保持に入る速度 [m/s]（これ未満で保持。≒ 7km/h） */
    val holdEnterSpeedMps: Float = 2.0f,
    /** GPS 方位の保持を解く速度 [m/s]（これを超えたら GPS 方位に戻る。≒ 11km/h） */
    val holdExitSpeedMps: Float = 3.0f,
    /** GPS 方位を使う最大の水平精度 [m]（§5.2） */
    val maxGpsAccM: Float = 15f,
    /** GPS 方位を使う最大の方位の精度 [°]（値を出している端末のみ） */
    val maxGpsBearingAccDeg: Float = 20f,
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
    /**
     * 使う縮尺の段 [km]（RangeAuto.ALL_STEPS_KM の中から）。縮尺は ARC では基準の距離環が左右端に接する距離、
     * North Up では最外周の距離環。距離環の間隔は縮尺の 1/2
     */
    val rangeStepsKm: List<Double> = listOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0),
    /** 起動時の縮尺 [km] */
    val initialRangeKm: Double = 1.0,
    /** 縮尺の AUTO（次の WP が収まる最小の段）を起動時に ON にする */
    val autoRange: Boolean = true,
    /** AUTO: 狭める（拡大する）方向は、条件がこれだけ続いてから切り替える [秒] */
    val autoRangeZoomInDelaySec: Int = 5,
    /** AUTO の下限・上限 [km]（段。使う段から選ぶ。画面と設定画面では R1 = 1つ目の距離環の距離で出す） */
    val autoMinRangeKm: Double = Tuning.AUTO_MIN_RANGE_KM,
    val autoMaxRangeKm: Double = Tuning.AUTO_MAX_RANGE_KM,
    /** 横並びの WP ボタン列に一度に見せる数（ボタンの幅はこれで決まる。超える分は左右にスクロール） */
    val wpButtonsMax: Int = 4,
    /** HUD に描く WP の数（次の WP から先） */
    val hudWpCount: Int = 15,
    /** UI の色（上部バー・数値・操作列・WP ボタン列・標高プロファイル・再生の帯） */
    val uiTheme: ColorTheme = ColorTheme.WHITE,
    /** 地図の色（地図の Canvas に描くもの） */
    val mapTheme: ColorTheme = ColorTheme.GREEN,
    /** ARC の自機の位置 */
    val ownshipPosition: OwnshipPosition = OwnshipPosition.STANDARD,
    /** 起動時に前回の WP リストを自動で開く（起動時の選択を出さない） */
    val autoOpenLastList: Boolean = false,
    /** 標高プロファイルの表示サイズ（§6.6） */
    val profileSize: ProfileSize = ProfileSize.SMALL,
    /** PAN（ドラッグで地図を動かす）のあと、操作がないまま現在地へ戻るまで [秒] */
    val panReturnSec: Int = 15,
    /** 画面常時点灯（§6.8） */
    val keepScreenOn: Boolean = true,
    /** 地図に重ねるボタン（上部バー・操作列・WP ボタン列・再生の帯）の不透明度 [%]。20〜100、10 刻み */
    val buttonOpacityPct: Int = Tuning.BUTTON_OPACITY_DEFAULT_PCT,
    /** 数値（4行）の不透明度 [%]。20〜100、10 刻み。警告の表示は常に 100% */
    val numbersOpacityPct: Int = Tuning.NUMBERS_OPACITY_DEFAULT_PCT,
) {
    companion object {
        val WP_BUTTONS_MAX_RANGE = 3..6
        val PAN_RETURN_SEC_RANGE = 5..60
        val HUD_WP_COUNT_RANGE = 1..20
        val REACH_RADIUS_CHOICES_M = listOf(50.0, 100.0, 200.0, 500.0)
        val RATE_WINDOW_CHOICES_SEC = listOf(10, 30, 60)

        /** 不透明度 [%] の選べる値（20, 30, … 100） */
        val OPACITY_CHOICES_PCT = (Tuning.OPACITY_MIN_PCT..Tuning.OPACITY_MAX_PCT step Tuning.OPACITY_STEP_PCT).toList()
    }
}
