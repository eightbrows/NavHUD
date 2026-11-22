package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.io.MapLayer

enum class DisplayMode { ARC, NORTH_UP }

/** 基本色（UI の色・地図の色それぞれに選ぶ）。マゼンタ・警告色・グレーは固定 */
enum class ColorTheme { WHITE, GREEN, AMBER }

/**
 * ARC の自機の位置（WP ボタン列の上端からの距離）。高め・さらに高めは後方の WP・矢印に余裕を持たせる。
 * 保存値は名前（STANDARD / HIGH / HIGHER）。前からの2つの名前は変えない
 */
/**
 * ARC の自機の位置（§6.2）。下から5段。画面では 後4 / 後3 / 後2 / 後1 / 中央 と出す（「後」の数字が大きいほど下）。
 * STANDARD・HIGH・HIGHER は前からの段（保存値の名前は変えない）。CENTER は North Up の自機と同じ高さ、
 * NEAR_CENTER は HIGHER と CENTER のちょうど中間。
 */
enum class OwnshipPosition { STANDARD, HIGH, HIGHER, NEAR_CENTER, CENTER }

/**
 * 読み込んだ軌跡（REPLAY のトラック全体の線）の色。実際の色は ui/HudColors で決め、明るさ（trackBrightnessPct）を掛ける。
 * 既定の白は、前の版のグレーの線と同じ見た目
 */
enum class TrackColor { WHITE, GREEN, AMBER, CYAN, YELLOW, BLUE }

/**
 * 地上高（AGL）が負（その場所の最高標高より低い）のときの出し方（20261025-D08）。保存値は名前。
 * ZERO = 「0 m」と出す（初期値）、SHOW = 負の値のまま警告の色で出す
 */
enum class AglNegative { ZERO, SHOW }

/** 標高プロファイルの表示サイズ（§6.6） */
enum class ProfileSize { OFF, SMALL, MEDIUM, LARGE }

/**
 * 移動のプリセット（§5.4・§6.9。20261121-D01 で「到達判定のプリセット」から改名し、歩行を足した）。
 * 自動車（Tuning.CAR_*）と歩行（Tuning.WALK_*）は値が決まっていて変えられない。カスタム1〜3 は枠ごとに値を持つ。
 * 保存は名前（並びを変えても前の保存は読める）。画面のボタンもこの並び
 */
enum class TravelMode { CAR, WALK, CUSTOM1, CUSTOM2, CUSTOM3 }

/**
 * 方位の出し方（20261121-D01。移動のプリセットの値）。GPS = GPS の方位だけ（遅いときは保持。前からの出し方）、
 * GPS_TRACK = GPS の方位が使えないとき（保持の間と、方位精度が上限を超えて前の値のままの間）は、位置から求める向き（TRK）、
 * TRACK_ONLY = GPS の方位は使わず、いつも位置から求める向き（TRK。20261121-D04）。向きがまだないとき（起動直後・欠けのあと、
 * 決めた距離を歩くまで）は GPS_TRACK と同じ扱い（GPS の値・保持の値）。保存は名前
 */
enum class HeadingMethod { GPS, GPS_TRACK, TRACK_ONLY }

/**
 * 移動のプリセットの値の組（§5.4。到達の判定・方位・数値の平均・縮尺・地図とランドマークの表・標高プロファイル）。名前は前からの ReachProfile のまま。
 * 20261121-D01 で、方位の出し方・方位精度の上限・GS の平均・ETA の平均速度を足した。
 * 20261121-D03 で、位置から求める向きの距離・縮尺（使う縮尺・起動時の縮尺・起動時に自動縮尺・自動縮尺の限度 2 つ・細かい調整 3 つ）・
 * 地図の項目の表・ランドマークの表・標高プロファイルを足した（歩行の初期値は自動車と同じ）
 */
data class ReachProfile(
    val reachRadiusM: Double,
    val sidePass: Boolean,
    val sidePassMaxM: Double,
    val sidePassDepartM: Double,
    val passDetection: Boolean,
    val passMaxApproachM: Double,
    val passDepartM: Double,
    val passHoldSec: Int,
    /** 真横通過: 判定を始める速さ [km/h]（20261119-D01。前は方位を戻す速度を使っていた） */
    val sidePassMinKmh: Int = Tuning.CAR_SIDE_PASS_MIN_SPEED_KMH,
    /**
     * 方位を止める / 戻す速度 [km/h]（20261119-D03 からプリセットの値。前は全体の値）。
     * 戻す速度は止める速度より 2 km/h 以上大きい（NavSettings.holdSpeedsKmh）
     */
    val holdEnterKmh: Int = Tuning.HOLD_ENTER_SPEED_DEFAULT_KMH,
    val holdExitKmh: Int = Tuning.HOLD_EXIT_SPEED_DEFAULT_KMH,
    /** 方位の出し方（20261121-D01） */
    val headingMethod: HeadingMethod = HeadingMethod.GPS,
    /** 方位精度の上限 [°]（20261121-D01 からプリセットの値。前は全体の値） */
    val maxBearingAccDeg: Float = Tuning.CAR_MAX_BEARING_ACC_DEG,
    /** GS の平均 [秒]（20261121-D01。1 = 今の点の速さ） */
    val gsAverageSec: Int = Tuning.CAR_GS_AVERAGE_SEC,
    /** ETA の平均速度を測る時間 [秒]（20261121-D01 からプリセットの値。前は 10 秒で固定） */
    val etaAverageSec: Int = Tuning.CAR_ETA_AVERAGE_SEC,
    /** 位置から求める向きの距離 [m]（20261121-D03。3〜20 m） */
    val trackDistanceM: Int = Tuning.TRACK_HEADING_DISTANCE_M.toInt(),
    /** 平均した方位線のオン / オフと、平均する秒数（20261122-D03。3〜10 秒） */
    val avgHeadingLine: Boolean = false,
    val avgHeadingSec: Int = Tuning.AVG_HEADING_DEFAULT_SEC,
    /** 軌跡の点の間隔 [m]（20261122-D03。読み込んだ軌跡はこの間隔に間引き、LIVE の軌跡はその半分ずつ足す） */
    val trackStepM: Int = Tuning.TRACK_DECIMATE_M.toInt(),
    /** 縮尺（20261121-D03 からプリセットの値。意味は NavSettings の同じ名前の値） */
    val rangeStepsKm: List<Double> = Tuning.RANGE_DEFAULT_STEPS_KM,
    val initialRangeKm: Double = Tuning.INITIAL_RANGE_KM,
    val autoRange: Boolean = true,
    val autoMinRangeKm: Double = Tuning.AUTO_MIN_RANGE_KM,
    val autoMaxRangeKm: Double = Tuning.AUTO_MAX_RANGE_KM,
    val autoRangeZoomInDelaySec: Int = Tuning.AUTO_RANGE_ZOOM_IN_DELAY_SEC,
    val autoHoldAfterWpSec: Int = Tuning.AUTO_HOLD_AFTER_WP_SEC,
    val autoZoomInDistRatio: Double = Tuning.AUTO_ZOOM_IN_DIST_RATIO,
    /** 地図の項目の表・ランドマークの表・標高プロファイル（20261121-D03 からプリセットの値） */
    val mapItems: Map<MapItem, MapRange> = NavSettings.DEFAULT_MAP_ITEMS,
    val landmarks: Map<LandmarkItem, MapRange> = NavSettings.DEFAULT_LANDMARKS,
    val profileSize: ProfileSize = ProfileSize.OFF,
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
            sidePassMinKmh = Tuning.CAR_SIDE_PASS_MIN_SPEED_KMH,
            holdEnterKmh = Tuning.HOLD_ENTER_SPEED_DEFAULT_KMH,
            holdExitKmh = Tuning.HOLD_EXIT_SPEED_DEFAULT_KMH,
            headingMethod = HeadingMethod.GPS,
            maxBearingAccDeg = Tuning.CAR_MAX_BEARING_ACC_DEG,
            gsAverageSec = Tuning.CAR_GS_AVERAGE_SEC,
            etaAverageSec = Tuning.CAR_ETA_AVERAGE_SEC,
        )

        /** 歩行の初期値（20261121-D01。調べ 20261119-D03-WALK の案。20261121-D03 で足した値は自動車と同じ） */
        val WALK = ReachProfile(
            reachRadiusM = Tuning.WALK_ARRIVAL_RADIUS_M,
            sidePass = Tuning.WALK_SIDE_PASS,
            sidePassMaxM = Tuning.WALK_SIDE_PASS_MAX_M,
            sidePassDepartM = Tuning.WALK_SIDE_PASS_DEPART_M,
            passDetection = Tuning.WALK_PASS_DETECTION,
            passMaxApproachM = Tuning.WALK_PASS_MAX_APPROACH_M,
            passDepartM = Tuning.WALK_PASS_DEPART_M,
            passHoldSec = Tuning.WALK_PASS_HOLD_SEC,
            sidePassMinKmh = Tuning.WALK_SIDE_PASS_MIN_SPEED_KMH,
            holdEnterKmh = Tuning.WALK_HOLD_ENTER_KMH,
            holdExitKmh = Tuning.WALK_HOLD_EXIT_KMH,
            // 20261121-D06 から位置だけ（調べ 20261121-D05-HDGWALK の 4.1。前は GPS ＋ 位置）
            headingMethod = HeadingMethod.TRACK_ONLY,
            // 20261122-D01 から 4 m（調べ 20261122-HDGCORNER。前は 8 m）
            trackDistanceM = Tuning.WALK_TRACK_DISTANCE_M,
            // 20261122-D03: 平均した方位線はオン（5 秒）、軌跡は 3 m 間隔
            avgHeadingLine = true,
            trackStepM = Tuning.WALK_TRACK_STEP_M,
            maxBearingAccDeg = Tuning.WALK_MAX_BEARING_ACC_DEG,
            gsAverageSec = Tuning.WALK_GS_AVERAGE_SEC,
            etaAverageSec = Tuning.WALK_ETA_AVERAGE_SEC,
        )
    }
}

/** 仕様の設定値（既定値つき）。SettingsStore が SharedPreferences に保存する（§6.9）。 */
data class NavSettings(
    /**
     * GPS 方位の保持に入る速度 [m/s]（これ未満で保持）。設定画面では km/h の整数（1〜34、既定 4 km/h。20261119-D01 まで 7 km/h）で選ぶ。
     * 20261119-D03 から到達判定のプリセットの値（ReachProfile.holdEnterKmh。自動車は 4 km/h で決まった値）。
     * ここには選んでいるプリセットの値が入る（selectTravelMode / editReach で変える）
     */
    val holdEnterSpeedMps: Float = kmhToMps(Tuning.HOLD_ENTER_SPEED_DEFAULT_KMH),
    /**
     * GPS 方位の保持を解く速度 [m/s]（これを超えたら GPS 方位に戻る）。km/h の整数（既定 6 km/h。20261119-D01 まで 11 km/h）。
     * 保持に入る速度より Tuning.HOLD_SPEED_MIN_GAP_KMH（2 km/h）以上大きい。真横通過の判定には使わない（sidePassMinKmh）。
     * 20261119-D03 から到達判定のプリセットの値（ReachProfile.holdExitKmh。自動車は 6 km/h で決まった値）
     */
    val holdExitSpeedMps: Float = kmhToMps(Tuning.HOLD_EXIT_SPEED_DEFAULT_KMH),
    /** GPS 方位を使う最大の水平精度 [m]（§5.2）。3 / 5 / 10 / 15 / 20 / 30 / 50 / 100 から選ぶ */
    val maxGpsAccM: Float = 15f,
    /**
     * GPS 方位を使う最大の方位の精度 [°]（値を出している端末のみ）。20261121-D01 から移動のプリセットの値（ReachProfile.maxBearingAccDeg。
     * 自動車 20°・歩行 30°）。ここには選んでいるプリセットの値が入る
     */
    val maxGpsBearingAccDeg: Float = Tuning.CAR_MAX_BEARING_ACC_DEG,
    /** 方位の出し方（20261121-D01。移動のプリセットの値。ここには選んでいるプリセットの値が入る） */
    val headingMethod: HeadingMethod = HeadingMethod.GPS,
    /** 画面の GS の平均 [秒]（20261121-D01。移動のプリセットの値。1 = 今の点の速さ） */
    val gsAverageSec: Int = Tuning.CAR_GS_AVERAGE_SEC,
    /** 画面の ETA の平均速度を測る時間 [秒]（20261121-D01。移動のプリセットの値） */
    val etaAverageSec: Int = Tuning.CAR_ETA_AVERAGE_SEC,
    /** 位置から求める向きの距離 [m]（20261121-D03。移動のプリセットの値） */
    val trackDistanceM: Int = Tuning.TRACK_HEADING_DISTANCE_M.toInt(),
    /** 平均した方位線・平均する秒数・軌跡の点の間隔（20261122-D03。移動のプリセットの値） */
    val avgHeadingLine: Boolean = false,
    val avgHeadingSec: Int = Tuning.AVG_HEADING_DEFAULT_SEC,
    val trackStepM: Int = Tuning.TRACK_DECIMATE_M.toInt(),
    /**
     * 移動手段（§5.4・§6.9）。下の到達判定の値（reachRadiusM〜passHoldSec）は、選んでいる移動手段の値
     * （自動車なら推奨値、カスタムならその枠の値）。切り替え・編集は selectTravelMode / editReach で行う
     */
    val travelMode: TravelMode = TravelMode.CAR,
    /** カスタム1〜3 の枠ごとの値（3つ）。最初は自動車の値 */
    val customReach: List<ReachProfile> = List(3) { ReachProfile.CAR },
    /**
     * 自動車・歩行の値（20261121-D03 から変えられる）。保存は初期値（ReachProfile.CAR / WALK）と違う所だけ。
     * 選んでいるプリセットの値は、下の今の値（reachRadiusM など）にも入っている（selectTravelMode / storeActive）
     */
    val carProfile: ReachProfile = ReachProfile.CAR,
    val walkProfile: ReachProfile = ReachProfile.WALK,
    /** カスタム1〜3 の元（自動車か歩行。20261121-D03）。変えたかどうかは元の初期値と比べる */
    val customBase: List<TravelMode> = List(3) { TravelMode.CAR },
    /** カスタム1〜3 を「今の自動車 / 今の歩行」からコピーしたか（帯の「（元：今の歩行）」の出し分け。20261121-D03） */
    val customCopied: List<Boolean> = List(3) { false },
    /** 到着半径 [m]（全体の到達半径。停車・目的地そのものへ行く場合）。10 / 15 / 20 / 30 / 50 / 100 / 200 / 500 から選ぶ（§5.4。10〜20 は 20261121-D01 から） */
    val reachRadiusM: Double = Tuning.CAR_ARRIVAL_RADIUS_M,
    /** 真横通過（§5.4）: 走行中に WP が真横か後ろになり、いちばん近づいた距離から離れたら到達 */
    val sidePass: Boolean = Tuning.CAR_SIDE_PASS,
    /** 真横通過: WP までの距離の上限 [m] */
    val sidePassMaxM: Double = Tuning.CAR_SIDE_PASS_MAX_M,
    /** 真横通過: いちばん近づいた距離からこれだけ離れたら到達 [m] */
    val sidePassDepartM: Double = Tuning.CAR_SIDE_PASS_DEPART_M,
    /** 真横通過: 判定を始める速さ [km/h]（20261119-D01。前は方位を戻す速度。AUTO の「通り過ぎたか」も同じ値） */
    val sidePassMinKmh: Int = Tuning.CAR_SIDE_PASS_MIN_SPEED_KMH,
    /** 通過判定（§5.4 の予備。方位が取れない場面用）。最接近後に離れていったら到達とみなす */
    val passDetection: Boolean = Tuning.CAR_PASS_DETECTION,
    /** 通過判定: 最接近距離の上限 [m] */
    val passMaxApproachM: Double = Tuning.CAR_PASS_MAX_APPROACH_M,
    /** 通過判定: 最接近距離からこれだけ離れたら「離れた」[m] */
    val passDepartM: Double = Tuning.CAR_PASS_DEPART_M,
    /** 通過判定: 離れた状態がこれだけ続いたら到達 [秒] */
    val passHoldSec: Int = Tuning.CAR_PASS_HOLD_SEC,
    /** NO FIX とみなす秒数（§5.5）。3 / 5 / 10 / 15 / 20 / 30 / 60 / 120 から選ぶ */
    val noFixTimeoutSec: Int = 10,
    /** 標高オフセット [m]。標高 = 楕円体高 − これ（§6.8） */
    val altOffsetM: Double = 36.0,
    /** 地上高の負の値の出し方（20261025-D08）。初期値は 0 にする */
    val aglNegative: AglNegative = AglNegative.ZERO,
    /** 表示モード（§6.1）。初期値は ARC（20261025-D03 で ARC に戻した。D01・D02 は実機の設定の North Up） */
    val displayMode: DisplayMode = DisplayMode.ARC,
    /**
     * 使う縮尺の段 [km]（RangeAuto.ALL_STEPS_KM の中から）。縮尺は ARC では基準の距離環が左右端に接する距離、
     * North Up では縮尺の距離環（方位サークルはその1つ外側）。距離環の間隔は縮尺の 1/2
     */
    val rangeStepsKm: List<Double> = Tuning.RANGE_DEFAULT_STEPS_KM,
    /** 起動時の縮尺 [km] */
    val initialRangeKm: Double = Tuning.INITIAL_RANGE_KM,
    /** 縮尺の AUTO（次の WP が収まる最小の段）を起動時に ON にする */
    val autoRange: Boolean = true,
    /** AUTO: 詳細にする方向は、条件がこれだけ続いてから切り替える [秒] */
    val autoRangeZoomInDelaySec: Int = Tuning.AUTO_RANGE_ZOOM_IN_DELAY_SEC,
    /** AUTO の詳細の限度・広域の限度 [km]（段。使う段から選ぶ。画面と設定画面では R1 = 1つ目の距離環の距離で出す） */
    val autoMinRangeKm: Double = Tuning.AUTO_MIN_RANGE_KM,
    val autoMaxRangeKm: Double = Tuning.AUTO_MAX_RANGE_KM,
    /**
     * 到達した WP を通り過ぎてから AUTO の段を動かさない時間 [秒]。0 / 5 / 10 / 15 / 20 / 30 / 60 から選ぶ。通り過ぎるまでも動かさない
     * （真横通過・手動で到達にしたときは、到達してから数える）
     */
    val autoHoldAfterWpSec: Int = Tuning.AUTO_HOLD_AFTER_WP_SEC,
    /** AUTO で詳細へ切り替える距離: 次の WP が「これ × 今の段の R1（1つ目の距離環）」以内のときだけ詳細にする。1.0〜2.0、0.1 刻み */
    val autoZoomInDistRatio: Double = Tuning.AUTO_ZOOM_IN_DIST_RATIO,
    /** 横並びの WP ボタン列に一度に見せる数（ボタンの幅はこれで決まる。超える分は左右にスクロール） */
    val wpButtonsMax: Int = 5,
    /** HUD に描く WP の数（次の WP から先） */
    val hudWpCount: Int = 10,
    /** UI の色（上部バー・数値・操作列・WP ボタン列・標高プロファイル・再生の操作列） */
    val uiTheme: ColorTheme = ColorTheme.WHITE,
    /** 地図の色（地図の Canvas に描くもの） */
    val mapTheme: ColorTheme = ColorTheme.GREEN,
    /** ARC の自機の位置（5段）。既定は下から4段目（NEAR_CENTER。保存がない・読めない値も同じ。20261025-D01 で実機の設定に合わせた。前は HIGHER） */
    val ownshipPosition: OwnshipPosition = OwnshipPosition.NEAR_CENTER,
    /** 起動時に前回の WP リストを自動で開く（起動時の選択を出さない） */
    val autoOpenLastList: Boolean = true,
    /** 標高プロファイルの表示サイズ（§6.6） */
    val profileSize: ProfileSize = ProfileSize.OFF,
    /** PAN（ドラッグで地図を動かす）のあと、操作がないまま現在地へ戻るまで [秒] */
    val panReturnSec: Int = 30,
    /** 画面常時点灯（§6.8） */
    val keepScreenOn: Boolean = true,
    /** 背景地図（§8）の線を描く。OFF なら、mapItems によらず線を全部描かない（ランドマーク landmarks は別。20261021-D04） */
    val baseMap: Boolean = true,
    /** 背景地図の項目ごとの、どの縮尺まで描くか（§8）。表にない項目は OFF（mapRangeOf で読む）。初期値は DEFAULT_MAP_ITEMS */
    val mapItems: Map<MapItem, MapRange> = DEFAULT_MAP_ITEMS,
    /** ランドマーク（空港・山頂の記号）の項目ごとの、どの縮尺まで描くか（20261021-D04）。baseMap によらない。初期値は DEFAULT_LANDMARKS */
    val landmarks: Map<LandmarkItem, MapRange> = DEFAULT_LANDMARKS,
    /**
     * ランドマークの明るさの段（1〜6。20261022-D01）。段 4 が掛ける数 1 の見え方。空港の記号・敷地・略号・山頂の三角の全部に効く
     * （掛ける数は線の明るさと同じ Tuning.BASEMAP_BRIGHTNESS_FACTORS。明るくする方は白に近づける。HudColors.landmark）
     */
    val landmarkBrightness: Int = DEFAULT_LANDMARK_BRIGHTNESS,
    /** 背景地図の線の太さの段（1〜6。Tuning.BASEMAP_WIDTH_FACTORS を全部の線の太さに掛ける） */
    val mapLineWidth: Int = DEFAULT_MAP_LINE_WIDTH,
    /** 背景地図の線の明るさの段（1〜6。Tuning.BASEMAP_BRIGHTNESS_FACTORS を全部の線の色に掛ける。ランドマークの記号には掛けない） */
    val mapLineBrightness: Int = DEFAULT_MAP_LINE_BRIGHTNESS,
    /** 地図に重ねるボタン（上部バー・操作列・WP ボタン列・再生の操作列）の不透明度 [%]。20〜100、10 刻み */
    val buttonOpacityPct: Int = Tuning.BUTTON_OPACITY_DEFAULT_PCT,
    /** 数値（4行）の不透明度 [%]。20〜100、10 刻み。警告の表示は常に 100% */
    val numbersOpacityPct: Int = Tuning.NUMBERS_OPACITY_DEFAULT_PCT,
    /** 読み込んだ軌跡（REPLAY のトラック全体の線）の色。既定は黄 */
    val trackColor: TrackColor = TrackColor.YELLOW,
    /** 読み込んだ軌跡の明るさ [%]。25 / 50 / 75 / 100（100 がいちばん明るい）。どの色にも掛ける */
    val trackBrightnessPct: Int = Tuning.TRACK_BRIGHTNESS_DEFAULT_PCT,
    /** 距離環の数字の大きさ [%]。Tuning.RING_LABEL_SP（10sp）を 100% として 100 / 125 / 150 / 175 / 200 */
    val ringLabelScalePct: Int = Tuning.RING_LABEL_SCALE_DEFAULT_PCT,
    /**
     * 距離環の明るさ [%]（20261104-D01 から %。20 / 40 / 60 / 80 / 100。20261103-D01・D02 は段）。距離環の線の色の RGB に p / 100 を掛ける。
     * 距離環の数字・方位の目盛りと文字には掛けない。初期値 80%（D02 の段 5、D01 の段 3 と同じ明るさ。D13 までの距離環の 0.8 倍）
     */
    val ringBrightnessPct: Int = Tuning.RING_BRIGHTNESS_DEFAULT_PCT,
) {
    /** その項目をどの縮尺まで描くか */
    fun mapRangeOf(item: MapItem): MapRange = mapItems[item] ?: MapRange.OFF

    /** その項目の描く縮尺を変えた設定 */
    fun withMapRange(item: MapItem, range: MapRange): NavSettings = copy(mapItems = mapItems + (item to range))

    /** そのランドマークの項目をどの縮尺まで描くか */
    fun landmarkRangeOf(item: LandmarkItem): MapRange = landmarks[item] ?: MapRange.OFF

    /** そのランドマークの項目の描く縮尺を変えた設定 */
    fun withLandmarkRange(item: LandmarkItem, range: MapRange): NavSettings = copy(landmarks = landmarks + (item to range))

    /**
     * OFF でない項目が使うファイルのレイヤ（使うときに開くレイヤ。BaseMap.preload）。点のレイヤは読み込みのときに全部読むので入れない。
     * 空港の敷地・滑走路・滑走路の延長線（線のレイヤ）は、ランドマークのその項目が OFF でなければ入れる（背景地図のスイッチによらない。20261022-D01・20261024-D04・D05）
     */
    fun mapLayersInUse(): Set<MapLayer> =
        MapItem.entries.filter { mapRangeOf(it) != MapRange.OFF }.flatMap { it.layers }.toSet() +
            listOf(LandmarkItem.AIRPORT_AREA, LandmarkItem.RUNWAY, LandmarkItem.RUNWAY_EXT).filter { landmarkRangeOf(it) != MapRange.OFF }.map { it.layer }

    companion object {
        /**
         * 背景地図の項目の初期値（20261025-D01 で実機の設定に合わせた）: 海岸線・県境・高速道路・新幹線・河川と湖沼・航路は「〜100k」、
         * 国道は「〜25k」、市町村界は「〜5k」、その他の自動車専用道路・県道・在来線は OFF。
         * 前（20261021-D01〜20261024-D09）は高速道路・国道が「〜100k」、ほかは OFF
         */
        val DEFAULT_MAP_ITEMS: Map<MapItem, MapRange> = MapItem.entries.associateWith {
            when (it) {
                MapItem.COASTLINE, MapItem.PREF_BOUNDARY, MapItem.MOTORWAY, MapItem.SHINKANSEN, MapItem.WATER, MapItem.FERRY -> MapRange.UPTO_100KM
                MapItem.NATIONAL_ROAD -> MapRange.UPTO_25KM
                MapItem.MUNI_BOUNDARY -> MapRange.UPTO_5KM
                MapItem.OTHER_MOTORWAY, MapItem.PREF_ROAD, MapItem.RAILWAY -> MapRange.OFF
            }
        }
        /**
         * ランドマークの初期値（20261025-D01 で実機の設定に合わせた）: 空港の敷地は「〜5k」、ほかは「〜100k」。
         * 前（20261022-D01〜20261024-D09）は空港の敷地が「〜10k」、空港の略号が「〜25k」
         */
        val DEFAULT_LANDMARKS: Map<LandmarkItem, MapRange> = LandmarkItem.entries.associateWith {
            if (it == LandmarkItem.AIRPORT_AREA) MapRange.UPTO_5KM else MapRange.UPTO_100KM
        }
        /** 背景地図の線の太さ・明るさの段（1〜6。1 がいちばん細い・暗い） */
        val MAP_LINE_LEVELS = 1..Tuning.BASEMAP_WIDTH_FACTORS.size
        /** 背景地図の線の太さ・明るさ、ランドマークの明るさの、掛ける数が 1（もとの太さ・色）の段 */
        const val MAP_LINE_DEFAULT_LEVEL = 4
        /** 設定の初期値（20261025-D01 で実機の設定に合わせた。前はどれも 4）: 線の太さ 2・線の明るさ 3・ランドマークの明るさ 3 */
        const val DEFAULT_MAP_LINE_WIDTH = 2
        const val DEFAULT_MAP_LINE_BRIGHTNESS = 3
        const val DEFAULT_LANDMARK_BRIGHTNESS = 3
        val WP_BUTTONS_MAX_RANGE = 3..6
        val PAN_RETURN_SEC_RANGE = 5..60
        val HUD_WP_COUNT_RANGE = 1..20
        /** 到着半径 [m] の選べる値（20261121-D01 で歩行のために 10・15・20 m を足した） */
        val REACH_RADIUS_CHOICES_M = listOf(10.0, 15.0, 20.0, 30.0, 50.0, 100.0, 200.0, 500.0)

        /** 位置から求める向きの距離 [m] の範囲（20261121-D03。1 m 刻み） */
        val TRACK_DISTANCE_M_RANGE = Tuning.TRACK_HEADING_DISTANCE_MIN_M..Tuning.TRACK_HEADING_DISTANCE_MAX_M

        /** 平均した方位線の秒数（20261122-D03） */
        val AVG_HEADING_SEC_RANGE = Tuning.AVG_HEADING_MIN_SEC..Tuning.AVG_HEADING_MAX_SEC

        /** 軌跡の点の間隔 [m]（20261122-D03。自動車 10 m・歩行 3 m） */
        val TRACK_STEP_CHOICES_M = listOf(2, 3, 5, 10, 20)

        /** GS の平均 [秒] の選べる値（20261121-D01。1 = 今の点の速さ） */
        val GS_AVERAGE_CHOICES_SEC = listOf(1, 3, 5, 10)

        /** ETA の平均速度を測る時間 [秒] の選べる値（20261121-D01） */
        val ETA_AVERAGE_CHOICES_SEC = listOf(10, 20, 30, 60, 120)

        // 設定画面のステッパーで変えられる範囲。読み込み（SettingsCodec）でも、範囲外の値はその項目だけ初期値にする（§6.9）
        /**
         * 保持に入る / 解く速度 [km/h]: 1〜36 の整数、1 km/h 刻み。解く速度は入る速度より Tuning.HOLD_SPEED_MIN_GAP_KMH（2 km/h）以上大きい
         * （入る速度は 1〜34、解く速度は 入る速度 + 2〜36。20261119-D01 まで差は 1 km/h 以上）。保存は m/s のままで、読むときに一番近い km/h の整数に合わせる
         */
        val HOLD_SPEED_KMH_RANGE = 1..36

        /** 保持に入る速度と解く速度の差の最小 [km/h] */
        const val HOLD_SPEED_MIN_GAP_KMH = Tuning.HOLD_SPEED_MIN_GAP_KMH

        /**
         * 保持に入る / 解く速度 [km/h] を、範囲と差の決まりに合わせる（設定画面・読み込み共通）。入る速度は 1〜34 に収め、
         * 解く速度は 入る速度 + 2〜36 に収める（差が足りなければ解く速度を上げる）
         */
        fun holdSpeedsKmh(enterKmh: Int, exitKmh: Int): Pair<Int, Int> {
            val r = HOLD_SPEED_KMH_RANGE
            val enter = enterKmh.coerceIn(r.first, r.last - HOLD_SPEED_MIN_GAP_KMH)
            return enter to exitKmh.coerceIn(enter + HOLD_SPEED_MIN_GAP_KMH, r.last)
        }

        /** 真横通過: 判定を始める速さ [km/h] の範囲（1 km/h 刻み） */
        val SIDE_PASS_MIN_SPEED_KMH_RANGE = 1..36

        /** km/h の整数 → m/s */
        fun kmhToMps(kmh: Int): Float = kmh / 3.6f

        /** m/s → 一番近い km/h の整数（1〜36 の中） */
        fun holdSpeedKmh(mps: Float): Int = Math.round(mps * 3.6f).coerceIn(HOLD_SPEED_KMH_RANGE)

        /** GPS の水平精度の上限 [m] の選べる値 */
        val MAX_GPS_ACC_CHOICES_M = listOf(3f, 5f, 10f, 15f, 20f, 30f, 50f, 100f)

        /** NO FIX とみなす時間 [秒] の選べる値 */
        val NO_FIX_TIMEOUT_CHOICES_SEC = listOf(3, 5, 10, 15, 20, 30, 60, 120)

        /** WP を通り過ぎてから縮尺を変えるまで [秒] の選べる値 */
        val AUTO_HOLD_AFTER_WP_CHOICES_SEC = listOf(0, 5, 10, 15, 20, 30, 60)

        /** 選べる値の中で v に一番近いもの（ちょうど真ん中なら小さい方） */
        fun nearestInt(choices: List<Int>, v: Int): Int = choices.minBy { kotlin.math.abs(it.toLong() - v) }

        fun nearestFloat(choices: List<Float>, v: Float): Float = choices.minBy { kotlin.math.abs(it - v) }
        val MAX_GPS_BEARING_ACC_DEG_RANGE = 5f..90f
        val SIDE_PASS_MAX_M_RANGE = 30.0..500.0
        val SIDE_PASS_DEPART_M_RANGE = 5.0..100.0
        val PASS_MAX_APPROACH_M_RANGE = 50.0..2000.0
        val PASS_DEPART_M_RANGE = 10.0..500.0
        val PASS_HOLD_SEC_RANGE = 1..60

        val ALT_OFFSET_M_RANGE = -200.0..200.0
        val AUTO_RANGE_ZOOM_IN_DELAY_SEC_RANGE = 0..30

        /** 不透明度・読み込んだ軌跡の明るさ・距離環の明るさ [%] の選べる値（20 / 40 / 60 / 80 / 100。20261104-D01） */
        val OPACITY_CHOICES_PCT = Tuning.PCT_CHOICES
        val TRACK_BRIGHTNESS_CHOICES_PCT = Tuning.PCT_CHOICES
        val RING_BRIGHTNESS_CHOICES_PCT = Tuning.PCT_CHOICES

        /** pct に一番近い選べる値（ちょうど真ん中なら大きい方。20261104-D01）。範囲の外も一番近い端 */
        fun nearestPct(pct: Int): Int = Tuning.PCT_CHOICES.minWith(compareBy<Int>({ kotlin.math.abs(it - pct) }, { -it }))

        /** % の保存値を読む（20261104-D01）: 20〜100 の中なら一番近い選べる値、外なら null（初期値にする） */
        fun readPct(pct: Int): Int? = if (pct in Tuning.PCT_MIN..Tuning.PCT_MAX) nearestPct(pct) else null

        /**
         * 前の版の距離環の明るさの段を、同じ明るさの % にしてから一番近い選べる値に読む（20261104-D01）。
         * D01（ringBrightnessLevel。0.4〜1.6）: 1→40、2→60、3→80、4→100、5・6→100（130%・160% は一番近い 100%）。
         * D02（ringLineBrightnessLevel。0.2〜1.0）: 1→20、2→40（30% はちょうど真ん中なので大きい方）、3→40、4→60、5→80、6→100。1〜6 の外は null
         */
        fun ringBrightnessPctFromLevel(level: Int, factors: List<Float>): Int? =
            factors.getOrNull(level - 1)?.let { nearestPct(Math.round(it * 100)) }

        /** 距離環の数字の大きさ [%] の選べる値 */
        val RING_LABEL_SCALE_CHOICES_PCT = Tuning.RING_LABEL_SCALE_CHOICES_PCT

        /** 距離環の数字の大きさ [%] を、一番近い選べる値にする（範囲の外は端の段。段の間はちょうど真ん中にならない） */
        fun nearestRingLabelScalePct(pct: Int): Int = RING_LABEL_SCALE_CHOICES_PCT.minBy { kotlin.math.abs(it.toLong() - pct) }


        /** 詳細へ切り替える距離の倍率の選べる値（1.0, 1.1, … 2.0）。足し算の誤差が残らないよう、刻みの数から作って丸める */
        val AUTO_ZOOM_IN_DIST_RATIO_CHOICES: List<Double> = run {
            val n = Math.round((Tuning.AUTO_ZOOM_IN_DIST_RATIO_MAX - Tuning.AUTO_ZOOM_IN_DIST_RATIO_MIN) / Tuning.AUTO_ZOOM_IN_DIST_RATIO_STEP).toInt()
            (0..n).map { i -> Math.round((Tuning.AUTO_ZOOM_IN_DIST_RATIO_MIN + i * Tuning.AUTO_ZOOM_IN_DIST_RATIO_STEP) * 1e6) / 1e6 }
        }

        /** 倍率を、一番近い選べる値にする（上限を超えていれば上限、下限より小さければ下限） */
        fun nearestZoomInDistRatio(v: Double): Double = AUTO_ZOOM_IN_DIST_RATIO_CHOICES.minBy { kotlin.math.abs(it - v) }
    }
}

/** 今の値（選んでいるプリセットの値）。 */
val NavSettings.reachProfile: ReachProfile
    get() = ReachProfile(
        reachRadiusM, sidePass, sidePassMaxM, sidePassDepartM, passDetection, passMaxApproachM, passDepartM, passHoldSec, sidePassMinKmh,
        holdEnterKmh = NavSettings.holdSpeedKmh(holdEnterSpeedMps), holdExitKmh = NavSettings.holdSpeedKmh(holdExitSpeedMps),
        headingMethod = headingMethod, maxBearingAccDeg = maxGpsBearingAccDeg, gsAverageSec = gsAverageSec, etaAverageSec = etaAverageSec,
        trackDistanceM = trackDistanceM,
        avgHeadingLine = avgHeadingLine, avgHeadingSec = avgHeadingSec, trackStepM = trackStepM,
        rangeStepsKm = rangeStepsKm, initialRangeKm = initialRangeKm, autoRange = autoRange, autoMinRangeKm = autoMinRangeKm, autoMaxRangeKm = autoMaxRangeKm,
        autoRangeZoomInDelaySec = autoRangeZoomInDelaySec, autoHoldAfterWpSec = autoHoldAfterWpSec, autoZoomInDistRatio = autoZoomInDistRatio,
        mapItems = mapItems, landmarks = landmarks, profileSize = profileSize,
    )

/** 今の値を p にする（プリセットの選択・それぞれのプリセットの値は変えない）。 */
fun NavSettings.withReachValues(p: ReachProfile): NavSettings = copy(
    reachRadiusM = p.reachRadiusM,
    sidePass = p.sidePass,
    sidePassMaxM = p.sidePassMaxM,
    sidePassDepartM = p.sidePassDepartM,
    passDetection = p.passDetection,
    passMaxApproachM = p.passMaxApproachM,
    passDepartM = p.passDepartM,
    passHoldSec = p.passHoldSec,
    sidePassMinKmh = p.sidePassMinKmh,
    holdEnterSpeedMps = NavSettings.kmhToMps(p.holdEnterKmh),
    holdExitSpeedMps = NavSettings.kmhToMps(p.holdExitKmh),
    headingMethod = p.headingMethod,
    maxGpsBearingAccDeg = p.maxBearingAccDeg,
    gsAverageSec = p.gsAverageSec,
    etaAverageSec = p.etaAverageSec,
    trackDistanceM = p.trackDistanceM,
    avgHeadingLine = p.avgHeadingLine,
    avgHeadingSec = p.avgHeadingSec,
    trackStepM = p.trackStepM,
    rangeStepsKm = p.rangeStepsKm,
    initialRangeKm = p.initialRangeKm,
    autoRange = p.autoRange,
    autoMinRangeKm = p.autoMinRangeKm,
    autoMaxRangeKm = p.autoMaxRangeKm,
    autoRangeZoomInDelaySec = p.autoRangeZoomInDelaySec,
    autoHoldAfterWpSec = p.autoHoldAfterWpSec,
    autoZoomInDistRatio = p.autoZoomInDistRatio,
    mapItems = p.mapItems,
    landmarks = p.landmarks,
    profileSize = p.profileSize,
)

/** カスタムの枠の番号（0〜2）。自動車・歩行なら null。 */
val TravelMode.customIndex: Int?
    get() = when (this) {
        TravelMode.CAR, TravelMode.WALK -> null
        TravelMode.CUSTOM1 -> 0
        TravelMode.CUSTOM2 -> 1
        TravelMode.CUSTOM3 -> 2
    }

/** そのプリセットの値（選んでいなくても）。 */
fun NavSettings.profileOf(m: TravelMode): ReachProfile = when (m) {
    TravelMode.CAR -> carProfile
    TravelMode.WALK -> walkProfile
    else -> customReach.getOrNull(m.customIndex!!) ?: ReachProfile.CAR
}

/** そのプリセットの値を p にする。選んでいるプリセットなら、今の値も p にする。 */
fun NavSettings.withProfile(m: TravelMode, p: ReachProfile): NavSettings {
    val next = when (m) {
        TravelMode.CAR -> copy(carProfile = p)
        TravelMode.WALK -> copy(walkProfile = p)
        else -> copy(customReach = customReach.toMutableList().also { it[m.customIndex!!] = p })
    }
    return if (m == travelMode) next.withReachValues(p) else next
}

/**
 * 今の値（設定画面で変えた値）を、選んでいるプリセットの値に入れる（20261121-D03）。設定を変えたあと（NavViewModel.updateSettings）に呼ぶ。
 * 縮尺・地図の表などを今の値（copy）で変えても、プリセットに残るようにするため
 */
fun NavSettings.storeActive(): NavSettings = withProfile(travelMode, reachProfile)

/** 移動のプリセットを切り替える。そのプリセットの値を今の値にする（ほかのプリセットの値は残す）。 */
fun NavSettings.selectTravelMode(m: TravelMode): NavSettings = withReachValues(profileOf(m)).copy(travelMode = m)

/** 選んでいるプリセットの値を変える（今の値と、そのプリセットに残す値の両方。20261121-D03 から自動車・歩行も変えられる）。 */
fun NavSettings.editReach(f: (ReachProfile) -> ReachProfile): NavSettings = withProfile(travelMode, f(reachProfile))
