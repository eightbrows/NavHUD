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

/**
 * 移動手段（§5.4・§6.9）。自動車は到達判定の推奨値（Tuning.CAR_*）で固定。カスタム1〜3 は枠ごとに値を持つ。
 * 自転車・徒歩はあとで足す
 */
enum class TravelMode { CAR, CUSTOM1, CUSTOM2, CUSTOM3 }

/** 到達判定の値の組（移動手段ごと。§5.4）。 */
data class ReachProfile(
    val reachRadiusM: Double,
    val sidePass: Boolean,
    val sidePassMaxM: Double,
    val sidePassDepartM: Double,
    val passDetection: Boolean,
    val passMaxApproachM: Double,
    val passDepartM: Double,
    val passHoldSec: Int,
) {
    companion object {
        /** 自動車の推奨値 */
        val CAR = ReachProfile(
            reachRadiusM = Tuning.CAR_ARRIVAL_RADIUS_M,
            sidePass = Tuning.CAR_SIDE_PASS,
            sidePassMaxM = Tuning.CAR_SIDE_PASS_MAX_M,
            sidePassDepartM = Tuning.CAR_SIDE_PASS_DEPART_M,
            passDetection = Tuning.CAR_PASS_DETECTION,
            passMaxApproachM = Tuning.CAR_PASS_MAX_APPROACH_M,
            passDepartM = Tuning.CAR_PASS_DEPART_M,
            passHoldSec = Tuning.CAR_PASS_HOLD_SEC,
        )
    }
}

/** 仕様の設定値（既定値つき）。SettingsStore が SharedPreferences に保存する（§6.9）。 */
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
    /**
     * 移動手段（§5.4・§6.9）。下の到達判定の値（reachRadiusM〜passHoldSec）は、選んでいる移動手段の値
     * （自動車なら推奨値、カスタムならその枠の値）。切り替え・編集は selectTravelMode / editReach で行う
     */
    val travelMode: TravelMode = TravelMode.CAR,
    /** カスタム1〜3 の枠ごとの値（3つ）。最初は自動車の値 */
    val customReach: List<ReachProfile> = List(3) { ReachProfile.CAR },
    /** 到着半径 [m]（全体の到達半径。停車・目的地そのものへ行く場合）。30 / 50 / 100 / 200 / 500 から選ぶ（§5.4） */
    val reachRadiusM: Double = Tuning.CAR_ARRIVAL_RADIUS_M,
    /** 真横通過（§5.4）: 走行中に WP が真横か後ろになり、いちばん近づいた距離から離れたら到達 */
    val sidePass: Boolean = Tuning.CAR_SIDE_PASS,
    /** 真横通過: WP までの距離の上限 [m] */
    val sidePassMaxM: Double = Tuning.CAR_SIDE_PASS_MAX_M,
    /** 真横通過: いちばん近づいた距離からこれだけ離れたら到達 [m] */
    val sidePassDepartM: Double = Tuning.CAR_SIDE_PASS_DEPART_M,
    /** 通過判定（§5.4 の予備。方位が取れない場面用）。最接近後に離れていったら到達とみなす */
    val passDetection: Boolean = Tuning.CAR_PASS_DETECTION,
    /** 通過判定: 最接近距離の上限 [m] */
    val passMaxApproachM: Double = Tuning.CAR_PASS_MAX_APPROACH_M,
    /** 通過判定: 最接近距離からこれだけ離れたら「離れた」[m] */
    val passDepartM: Double = Tuning.CAR_PASS_DEPART_M,
    /** 通過判定: 離れた状態がこれだけ続いたら到達 [秒] */
    val passHoldSec: Int = Tuning.CAR_PASS_HOLD_SEC,
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
     * North Up では縮尺の距離環（方位サークルはその1つ外側）。距離環の間隔は縮尺の 1/2
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
    /**
     * 到達した WP を通り過ぎてから AUTO の段を動かさない時間 [秒]。0〜60。通り過ぎるまでも動かさない
     * （真横通過・手動で到達にしたときは、到達してから数える）
     */
    val autoHoldAfterWpSec: Int = Tuning.AUTO_HOLD_AFTER_WP_SEC,
    /** AUTO で狭め始める距離: 次の WP が「これ × 今の段の R1（1つ目の距離環）」以内のときだけ狭める。1.0〜3.0、0.1 刻み */
    val autoZoomInDistRatio: Double = Tuning.AUTO_ZOOM_IN_DIST_RATIO,
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
        val REACH_RADIUS_CHOICES_M = listOf(30.0, 50.0, 100.0, 200.0, 500.0)

        // 設定画面のステッパーで変えられる範囲。読み込み（SettingsCodec）でも、範囲外の値はその項目だけ初期値にする（§6.9）
        /** 保持に入る速度 [m/s] */
        val HOLD_ENTER_SPEED_MPS_RANGE = 0.5f..10f
        /** 保持を解く速度の上限 [m/s]（下限は 保持に入る速度 + 刻み） */
        const val HOLD_EXIT_SPEED_MAX_MPS = 15f
        /** 保持に入る速度と解く速度の最小の差 [m/s]（設定画面の刻みと同じ） */
        const val HOLD_SPEED_MIN_GAP_MPS = 0.1f
        val MAX_GPS_ACC_M_RANGE = 3f..100f
        val MAX_GPS_BEARING_ACC_DEG_RANGE = 5f..90f
        val SIDE_PASS_MAX_M_RANGE = 30.0..500.0
        val SIDE_PASS_DEPART_M_RANGE = 5.0..100.0
        val PASS_MAX_APPROACH_M_RANGE = 50.0..2000.0
        val PASS_DEPART_M_RANGE = 10.0..500.0
        val PASS_HOLD_SEC_RANGE = 1..60
        val NO_FIX_TIMEOUT_SEC_RANGE = 3..120
        val ALT_OFFSET_M_RANGE = -200.0..200.0
        val AUTO_RANGE_ZOOM_IN_DELAY_SEC_RANGE = 0..30
        val RATE_WINDOW_CHOICES_SEC = listOf(10, 30, 60)

        /** 不透明度 [%] の選べる値（20, 30, … 100） */
        val OPACITY_CHOICES_PCT = (Tuning.OPACITY_MIN_PCT..Tuning.OPACITY_MAX_PCT step Tuning.OPACITY_STEP_PCT).toList()

        val AUTO_HOLD_AFTER_WP_SEC_RANGE = 0..Tuning.AUTO_HOLD_AFTER_WP_MAX_SEC

        /** 狭め始める距離の倍率の選べる値（1.0, 1.1, … 3.0）。足し算の誤差が残らないよう、刻みの数から作って丸める */
        val AUTO_ZOOM_IN_DIST_RATIO_CHOICES: List<Double> = run {
            val n = Math.round((Tuning.AUTO_ZOOM_IN_DIST_RATIO_MAX - Tuning.AUTO_ZOOM_IN_DIST_RATIO_MIN) / Tuning.AUTO_ZOOM_IN_DIST_RATIO_STEP).toInt()
            (0..n).map { i -> Math.round((Tuning.AUTO_ZOOM_IN_DIST_RATIO_MIN + i * Tuning.AUTO_ZOOM_IN_DIST_RATIO_STEP) * 1e6) / 1e6 }
        }
    }
}

/** 「設定を初期値に戻す」（§6.9）: すべて初期値にし、移動手段は自動車に戻す。カスタム1〜3 の値は残す。 */
fun NavSettings.resetKeepingCustomReach(): NavSettings = NavSettings(customReach = customReach)

/** 今の到達判定の値（選んでいる移動手段の値）。 */
val NavSettings.reachProfile: ReachProfile
    get() = ReachProfile(reachRadiusM, sidePass, sidePassMaxM, sidePassDepartM, passDetection, passMaxApproachM, passDepartM, passHoldSec)

/** 到達判定の値を p にする（移動手段・カスタムの枠は変えない）。 */
fun NavSettings.withReachValues(p: ReachProfile): NavSettings = copy(
    reachRadiusM = p.reachRadiusM,
    sidePass = p.sidePass,
    sidePassMaxM = p.sidePassMaxM,
    sidePassDepartM = p.sidePassDepartM,
    passDetection = p.passDetection,
    passMaxApproachM = p.passMaxApproachM,
    passDepartM = p.passDepartM,
    passHoldSec = p.passHoldSec,
)

/** カスタムの枠の番号（0〜2）。自動車なら null。 */
val TravelMode.customIndex: Int?
    get() = when (this) {
        TravelMode.CAR -> null
        TravelMode.CUSTOM1 -> 0
        TravelMode.CUSTOM2 -> 1
        TravelMode.CUSTOM3 -> 2
    }

/** 移動手段を切り替える。自動車なら推奨値、カスタムならその枠の値を到達判定の値にする（ほかの枠の値は残す）。 */
fun NavSettings.selectTravelMode(m: TravelMode): NavSettings {
    val p = m.customIndex?.let { customReach.getOrNull(it) } ?: ReachProfile.CAR
    return withReachValues(p).copy(travelMode = m)
}

/** カスタムの枠の値を変える（今の値と、その枠に保存する値の両方）。自動車のときは変えない（推奨値で固定）。 */
fun NavSettings.editReach(f: (ReachProfile) -> ReachProfile): NavSettings {
    val i = travelMode.customIndex ?: return this
    val p = f(reachProfile)
    return withReachValues(p).copy(customReach = customReach.toMutableList().also { it[i] = p })
}
