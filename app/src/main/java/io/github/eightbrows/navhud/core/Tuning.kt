package io.github.eightbrows.navhud.core

/**
 * 調整値（設定画面では変えられないもの）の一覧。実機で使いながら、しきい値や時間・寸法を調整するためにここへ集めた。
 * - 設定画面で変えられる値は NavSettings の既定値を見る（ここには置かない）。
 * - 元の場所の定数名（RateTracker.BASE_MAX_LAG_MS など）は残して、ここを参照している。値はここだけで変える。
 * - dp / sp は画面の寸法（ui で .dp / .sp にする）。core の描画計算（HudMetrics・ProfileMetrics）は dp の数値のまま使う。
 * - 値を変えると挙動が変わるので、変えたら SPEC の該当の節とテストも確認する。
 */
object Tuning {

    // ---- 方位・コンパス（§5.2・§6.8） ----

    /** コンパスの平滑化の係数（新しい値の重み。単位ベクトルの指数平滑） */
    const val COMPASS_SMOOTHING = 0.2

    /** 磁気偏角を計算し直す間隔 [ms] */
    const val COMPASS_DECLINATION_INTERVAL_MS = 10_000L

    /** 端末の姿勢: 画面の傾きがこれ以上で立て置き [°] */
    const val COMPASS_TO_UPRIGHT_DEG = 55.0

    /** 端末の姿勢: 画面の傾きがこれ以下で平置き [°] */
    const val COMPASS_TO_FLAT_DEG = 35.0

    // ---- 測位・欠損（§5.5・§6.8） ----

    /** GPS の更新間隔 [ms] */
    const val GPS_INTERVAL_MS = 1_000L

    /** ログの欠損検出: 隣接 Fix の間隔がこれを超えたら欠損 [ms] */
    const val GAP_THRESHOLD_MS = 1_500L

    // ---- RATE・ETA（§5.3・§5.4） ----

    /** RATE: base が窓の起点よりこれを超えて古ければ欠損とみなす [ms] */
    const val RATE_BASE_MAX_LAG_MS = 2_000L

    /** RATE: この間隔を超える区間は欠損として直線距離で数える [ms] */
    const val RATE_GAP_SEGMENT_MS = 2_000L

    /** RATE: 履歴を窓より余分に持つ時間 [ms] */
    const val RATE_KEEP_MARGIN_MS = 3_000L

    /** ETA: 窓に足りない履歴で ETA を出すときの最低の長さ [秒] */
    const val ETA_MIN_HISTORY_SEC = 10

    /** ETA: これより遅い平均速度では ETA を出さない [m/s] */
    const val ETA_MIN_SPEED_MPS = 0.5

    // ---- 縮尺（§6.1） ----

    /** 選べる縮尺の全段 [km]（使う段は設定で選ぶ） */
    val RANGE_ALL_STEPS_KM = listOf(0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0)

    /** 画面の大きさがまだ分からないときの AUTO: 次の WP が「縮尺 × これ」以内 */
    const val RANGE_DISTANCE_FIT_RATIO = 0.9

    // ---- 地図の描画（HudMetrics の既定値。dp） ----

    const val HUD_TICK_MINOR_DP = 8f
    const val HUD_TICK_MAJOR_DP = 16f
    /** 目盛りから方位の文字まで */
    const val HUD_LABEL_GAP_DP = 12f
    /** 避ける枠の縁から、画面外の矢印・WP の印を置く所まで */
    const val HUD_EDGE_INSET_DP = 22f
    /** 矢印から、その文字まで（自機側） */
    const val HUD_ARROW_TEXT_GAP_DP = 34f
    /** ARC の自機の位置（回避枠の下端 = WP ボタン列・リプレイの帯の上端から）: 標準 / 高め */
    const val HUD_ARC_ORIGIN_DP = 24f
    const val HUD_ARC_ORIGIN_HIGH_DP = 84f
    /** North Up: 最外周の距離環の外側の余白（目盛りと文字の分） */
    const val HUD_NORTH_UP_MARGIN_DP = 48f
    /** 方位マーカー（三角）の大きさ */
    const val HUD_POINTER_DP = 14f
    /** 矢印の文字の1行の高さ（重なったときにずらす量） */
    const val HUD_ARROW_LABEL_LINE_DP = 16f
    /** 矢印の文字の1文字の幅の目安（等幅 11sp） */
    const val HUD_LABEL_CHAR_WIDTH_DP = 7f
    /** AUTO 縮尺: 矢印の枠から、さらにこれだけ内側に収める */
    const val HUD_FIT_MARGIN_DP = 16f
    /** 自機の記号の大きさの目安（半幅・半高） */
    const val HUD_OWN_SHIP_CLEAR_DP = 16f
    /** WP の印の中心から名前の中心まで */
    const val HUD_WP_NAME_OFFSET_DP = 18f
    /** 方位目盛りの文字の大きさの目安（半幅・半高） */
    const val HUD_COMPASS_LABEL_HALF_DP = 10f

    // ---- 文字の重なりの避け方（§6.1） ----

    /** 矢印の文字をずらして探す最大の行数 */
    const val ARROW_TEXT_MAX_SHIFT_LINES = 3

    /** 矢印（三角）を縁に沿ってずらす最大の段数（1段 = 三角の大きさ） */
    const val ARROW_SLIDE_MAX_STEPS = 4

    /** ARC の方位マーカーの周り: 左右の幅（方位マーカーの大きさの何倍か） */
    const val ARROW_MARKER_ZONE_HALF_WIDTH = 2f

    // ---- 標高プロファイル（§6.6） ----

    /** 縦軸の最小の幅 [m] */
    const val PROFILE_MIN_SPAN_M = 50.0

    /** 表示サイズ 小 / 中 / 大 の高さ [dp] */
    const val PROFILE_HEIGHT_SMALL_DP = 56f
    const val PROFILE_HEIGHT_MEDIUM_DP = 88f
    const val PROFILE_HEIGHT_LARGE_DP = 128f

    /** グラフの余白（左は縦軸の数字の分）[dp] */
    const val PROFILE_MARGIN_LEFT_DP = 40f
    const val PROFILE_MARGIN_RIGHT_DP = 14f
    const val PROFILE_MARGIN_TOP_DP = 10f
    const val PROFILE_MARGIN_BOTTOM_DP = 8f

    // ---- 軌跡（§6.7・§6.8） ----

    /** REPLAY のトラック全体: この間隔未満の点を間引く [m] */
    const val TRACK_DECIMATE_M = 10.0

    /** LIVE の軌跡: 前の点からこれ以上動いたら点を足す [m] */
    const val LIVE_TRAIL_MIN_STEP_M = 5.0

    /** LIVE の軌跡: 点の上限（超えたら古い方から捨てる） */
    const val LIVE_TRAIL_MAX_POINTS = 5_000

    /** 線の太さ [dp]: トラック全体 / 再生済み / LIVE の軌跡 */
    const val TRACK_LINE_DP = 1f
    const val TRACK_DONE_LINE_DP = 2f
    const val LIVE_TRAIL_LINE_DP = 1.5f

    // ---- 画面の配置（§6.1・§6.4・§6.7。dp） ----

    /** 右の操作列の幅と、ボタンの大きさ */
    const val SIDE_COLUMN_WIDTH_DP = 60f
    const val SIDE_BUTTON_DP = 52f

    /** リプレイの帯の高さ */
    const val REPLAY_BAND_HEIGHT_DP = 40f

    /** 横並びの WP ボタン列: 高さ、左端の「WP設定」の幅、ボタンの間隔 */
    const val WP_STRIP_HEIGHT_DP = 48f
    const val WP_SETTINGS_WIDTH_DP = 64f
    const val WP_BUTTON_GAP_DP = 6f

    /** 地図に重ねる部品の背景の不透明度（0〜1） */
    const val OVERLAY_ALPHA = 0.6f

    // ---- 線の太さ・文字の大きさ（地図・プロファイル） ----

    /** 地図の線: 細（距離環・目盛り・WP の線）/ 太（次の WP への線・WP の印・自機） [dp] */
    const val LINE_THIN_DP = 1.2f
    const val LINE_BOLD_DP = 2.2f

    /** 破線（無効 WP）: 線 / すき間 [dp] */
    const val DASH_ON_DP = 6f
    const val DASH_OFF_DP = 5f

    /** 数値の表示（情報帯・下部パネル）の文字の黒の縁取りの太さ [dp] */
    const val TEXT_OUTLINE_DP = 2.5f

    /** 文字の大きさ [sp]: 方位目盛り・WP の名前 / 画面外の矢印 / 距離環 */
    const val LABEL_SP = 13f
    const val ARROW_LABEL_SP = 11f
    const val RING_LABEL_SP = 10f

    /** 図形の大きさ [dp]: WP の印（ひし形の半径）/ 画面外の矢印（三角）/ 自機（三角の基準 / 方位なしの丸の半径） */
    const val WP_MARK_DP = 6f
    const val EDGE_ARROW_DP = 12f
    const val OWN_SHIP_DP = 11f
    const val OWN_SHIP_CIRCLE_DP = 7f

    /** 距離環の文字: 距離環から外側へ [dp] */
    const val RING_LABEL_OFFSET_DP = 6f

    /** 標高プロファイル: 線の太さ [dp]、点の半径 [dp]、文字 [sp] */
    const val PROFILE_LINE_DP = 1.8f
    const val PROFILE_POINT_RADIUS_DP = 3.5f
    const val PROFILE_TEXT_SP = 10f

    // ---- リプレイ（§6.7） ----

    /** 倍速の選択肢（タップで順に切り替える） */
    val REPLAY_SPEEDS = listOf(1, 2, 5, 10, 30)

    /** リプレイの刻み（この間に来た Fix をまとめて入れ、画面を1回更新する）[ms] */
    const val REPLAY_POLL_MS = 50L

    /** 時刻だけを進める刻み（NO FIX・カウントダウンの更新）[ms] */
    const val TICK_MS = 200L
}
