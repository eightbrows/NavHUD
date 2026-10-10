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

    /** AUTO の詳細の限度・広域の限度の既定 [km]（段。R1 = 1つ目の距離環はこの半分: 50m / 250m） */
    const val AUTO_MIN_RANGE_KM = 0.1
    const val AUTO_MAX_RANGE_KM = 0.5

    /** AUTO: 描画の枠の中に見えている隣り合う目標どうしの、画面上の最小の間隔 [dp]。これより近くなる段は使わない */
    const val AUTO_WP_MIN_SEP_DP = 40f

    /** AUTO: 1段詳細にするのは、次の WP を自機から 1 / これ 倍遠くに置いても1つ詳細側の段の枠に収まるとき（詳細にした直後に広域に戻さないための余裕） */
    const val AUTO_ZOOM_IN_FIT_RATIO = 0.8

    /** AUTO: 到達した WP を通り過ぎてから段を動かさない時間 [秒] の既定と範囲（通り過ぎるまでも動かさない） */
    const val AUTO_HOLD_AFTER_WP_SEC = 10


    /** AUTO: 詳細へ切り替える距離 = これ × 今の段の R1（1つ目の距離環。次の WP がこの距離以内のときだけ詳細にする）。既定と範囲・刻み */
    const val AUTO_ZOOM_IN_DIST_RATIO = 1.3
    const val AUTO_ZOOM_IN_DIST_RATIO_MIN = 1.0
    const val AUTO_ZOOM_IN_DIST_RATIO_MAX = 2.0
    const val AUTO_ZOOM_IN_DIST_RATIO_STEP = 0.1

    /**
     * AUTO: 次の WP に向けて一度詳細にしたあとは、到達するまで広域にしない。WP から離れたら広域にする:
     * 次の WP までの距離 > 「1つ広域側の段で詳細へ切り替える距離（詳細へ切り替える距離の倍率 × その段の R1）× これ」。
     * 既定（倍率 1.3）なら、R1 50m → 100m は 162.5m、100m → 250m は 406m、250m → 500m は 812m より遠いとき
     */
    const val AUTO_ZOOM_OUT_DIST_RATIO = 1.25

    /** ピンチ（§6.12）: 2本指の開き具合が、基準のこの倍以上で1段詳細へ（距離のレンジを小さく）、この倍以下で1段広域へ。1段変えたら基準を取り直す */
    const val PINCH_ZOOM_IN_RATIO = 1.25f
    const val PINCH_ZOOM_OUT_RATIO = 0.8f

    // ---- WP の到達判定（§5.4）。移動手段「自動車」の推奨値（自転車・徒歩はあとで足す） ----

    /** 到着半径 [m]（停車・目的地そのものへ行く場合）。入ったら到達 */
    const val CAR_ARRIVAL_RADIUS_M = 30.0
    /** 真横通過: ON / OFF、WP までの距離の上限 [m]、いちばん近づいた距離から離れたとみなす距離 [m] */
    const val CAR_SIDE_PASS = true
    const val CAR_SIDE_PASS_MAX_M = 150.0
    const val CAR_SIDE_PASS_DEPART_M = 10.0
    /** 通過判定（予備。方位が取れない場面用）: ON / OFF、最接近距離の上限 [m]、離れたとみなす距離 [m]、離れた状態が続く時間 [秒] */
    const val CAR_PASS_DETECTION = true
    const val CAR_PASS_MAX_APPROACH_M = 300.0
    const val CAR_PASS_DEPART_M = 50.0
    const val CAR_PASS_HOLD_SEC = 5

    // ---- 地図の描画（HudMetrics の既定値。dp） ----

    const val HUD_TICK_MINOR_DP = 8f
    const val HUD_TICK_MAJOR_DP = 16f
    /** 目盛りから方位の文字まで */
    const val HUD_LABEL_GAP_DP = 12f
    /** 矢印と AUTO の判定の枠（HudSceneBuilder.avoidFrame）の縁から、画面外の矢印・WP の印を置く所まで */
    const val HUD_EDGE_INSET_DP = 22f
    /** 矢印から、その文字まで（自機側） */
    const val HUD_ARROW_TEXT_GAP_DP = 34f
    /**
     * ARC の自機の位置（回避枠の下端 = WP ボタン列の上端から。LIVE・REPLAY とも）: 標準 / 高め / さらに高め（60dp ずつ）。
     * 上部の方位マーカーに自機の記号がかかるなら、かからない一番高い位置にする（HudSceneBuilder.projection）
     */
    const val HUD_ARC_ORIGIN_DP = 24f
    const val HUD_ARC_ORIGIN_HIGH_DP = 84f
    const val HUD_ARC_ORIGIN_HIGHER_DP = 144f

    /** GPS 方位の保持に入る / 解く速度の既定 [km/h]（設定画面では km/h の整数で選ぶ。D03 までは 2.0 / 3.0 m/s = 7.2 / 10.8 km/h） */
    const val HOLD_ENTER_SPEED_DEFAULT_KMH = 7
    const val HOLD_EXIT_SPEED_DEFAULT_KMH = 11
    /** North Up: 縮尺の距離環と画面の端・回避枠の上下の間の余白（方位サークルはその1つ外側の距離環で、画面からはみ出してよい） */
    const val HUD_NORTH_UP_EDGE_MARGIN_DP = 8f
    /** 方位マーカー（三角）の大きさ */
    const val HUD_POINTER_DP = 14f
    /** 文字の1行の高さの目安（HUD_TEXT_METRICS_SP の文字で。大きい文字はその比で伸ばす） */
    const val HUD_ARROW_LABEL_LINE_DP = 16f
    /** 文字の1文字の幅の目安（等幅、HUD_TEXT_METRICS_SP の文字で。全角は2文字分） */
    const val HUD_LABEL_CHAR_WIDTH_DP = 7f
    /** 上の2つ（1行の高さ・1文字の幅）を測った文字の大きさ [sp] */
    const val HUD_TEXT_METRICS_SP = 11f
    /** AUTO 縮尺: 矢印の枠から、さらにこれだけ内側に収める */
    const val HUD_FIT_MARGIN_DP = 16f
    /** 自機の記号の大きさの目安（半幅・半高） */
    const val HUD_OWN_SHIP_CLEAR_DP = 16f
    /** WP の印の中心から、いちばん近い行（名前）の中心まで（WP の文字を大きくしたので 18 → 20） */
    const val HUD_WP_NAME_OFFSET_DP = 20f
    /** 方位目盛りの文字の大きさの目安（半幅・半高） */
    const val HUD_COMPASS_LABEL_HALF_DP = 10f

    // ---- 文字の重なりの避け方（§6.1） ----

    /** 矢印の文字をずらして探す最大の行数 */
    const val ARROW_TEXT_MAX_SHIFT_LINES = 3

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

    /** 右の操作列（＋ / RNG / −。回避枠の縦中央）: 幅、ボタンの大きさ、ボタンの間隔、上下の余白 */
    const val SIDE_COLUMN_WIDTH_DP = 60f
    const val SIDE_BUTTON_DP = 52f
    const val SIDE_BUTTON_GAP_DP = 6f
    const val SIDE_COLUMN_PADDING_V_DP = 6f

    /**
     * 左の再生の操作列（REPLAY のときだけ。数値の下から WP ボタン列の上まで）: 幅は右の操作列と同じ（SIDE_COLUMN_WIDTH_DP）。
     * 左端の「戻る」ジェスチャーの範囲（システムの値）からさらにこれだけ内側に置く、倍速の ＋ / − の高さ、
     * 縦のスライダーの線の太さ・つまみの直径
     */
    const val REPLAY_COLUMN_EDGE_MARGIN_DP = 8f
    const val REPLAY_SPEED_BUTTON_HEIGHT_DP = 44f
    const val REPLAY_SEEK_TRACK_DP = 8f
    const val REPLAY_SEEK_THUMB_DP = 26f

    /** 横並びの WP ボタン列: 高さ、左端の「WP設定」の幅、ボタンの間隔 */
    const val WP_STRIP_HEIGHT_DP = 40f
    const val WP_SETTINGS_WIDTH_DP = 64f
    const val WP_BUTTON_GAP_DP = 6f

    /** WP ボタン列の上下の余白（ボタンの高さ = 列の高さ − 2 × これ） */
    const val WP_STRIP_PADDING_V_DP = 3f

    /** 数値の表示（上部バーの下、4行）: 上下の余白と、行の間隔 */
    const val NUMBERS_PADDING_V_DP = 4f
    const val NUMBERS_ROW_GAP_DP = 2f

    /** 標高プロファイルの地の不透明度（0〜1）。ボタン類は地を塗らない（枠と、縁取りした文字だけ） */
    const val OVERLAY_ALPHA = 0.6f

    /**
     * 地図に重ねるボタン（上部バー・操作列・WP ボタン列・再生の操作列）と数値の不透明度 [%]（設定で変える。既定値と範囲・刻み）。
     * 設定画面・WP 設定画面・案内の枠のボタンには使わない
     */
    const val BUTTON_OPACITY_DEFAULT_PCT = 70
    const val NUMBERS_OPACITY_DEFAULT_PCT = 100
    const val OPACITY_MIN_PCT = 20
    const val OPACITY_MAX_PCT = 100
    const val OPACITY_STEP_PCT = 10

    /**
     * 読み込んだ軌跡（REPLAY のトラック全体の線）の明るさ [%] の既定と選べる値。色（設定。既定は白）の RGB に p / 100 を掛ける
     * （白なら 25% = 0x40、50% = 0x80 のグレー）。D11 までの 0x3A は白の約 23%
     */
    const val TRACK_BRIGHTNESS_DEFAULT_PCT = 75
    val TRACK_BRIGHTNESS_CHOICES_PCT = listOf(25, 50, 75, 100)

    /**
     * 地図上の到達済みの WP（印・名前・そこへの線）のグレーの明るさ（0〜255）。D11 までは 0x4A。
     * 次の WP（マゼンタ）・まだ到達していない WP（テーマの色）・無効の WP（0x7A の破線）より目立たないように
     */
    const val WP_REACHED_MAP_GRAY = 0x68

    /** ON（反転）のボタンの塗りの不透明度 = ボタンの不透明度 × これ（裏の地図の線が透けて見える） */
    const val BUTTON_ON_FILL_ALPHA = 0.5f

    /** ON（反転）のボタンの黒の文字に付ける、ボタンの色の細い縁取りの線の太さ [dp]（外に見えるのは半分） */
    const val BUTTON_ON_TEXT_OUTLINE_DP = 2f

    // ---- 線の太さ・文字の大きさ（地図・プロファイル） ----

    /** 地図の線: 細（距離環・目盛り・WP の線）/ 太（次の WP への線・WP の印・自機） [dp] */
    const val LINE_THIN_DP = 1.2f
    const val LINE_BOLD_DP = 2.2f

    /** 破線（無効 WP）: 線 / すき間 [dp] */
    const val DASH_ON_DP = 6f
    const val DASH_OFF_DP = 5f

    /**
     * 次の WP への方位線（マゼンタ）の長い破線: 線 / すき間 [dp]。自機の方位線（ラバーライン）と重なっても、
     * すき間から自機の線が見えるように
     */
    const val ACTIVE_DASH_ON_DP = 18f
    const val ACTIVE_DASH_OFF_DP = 12f

    /** 数値の表示の文字の黒の縁取りの太さ [dp] */
    const val TEXT_OUTLINE_DP = 2.5f

    /** 縮尺のボタン（操作列の真ん中）: 距離の文字の大きさの上限 [sp]（ボタンの幅に収まるよう縮める）と、文字の左右の余白 [dp] */
    const val RANGE_BUTTON_MAX_SP = 22f
    const val RANGE_BUTTON_TEXT_PAD_DP = 4f

    /**
     * NO FIX の枠: 数値の欄の下端から下げる量・画面の左端から離す量 [dp]（左寄せで置く）と、枠の内側の余白（左右・上下）[dp]
     */
    const val NO_FIX_MARGIN_TOP_DP = 12f
    const val NO_FIX_MARGIN_START_DP = 12f
    const val NO_FIX_PADDING_H_DP = 18f
    const val NO_FIX_PADDING_V_DP = 10f

    /** 数値の表示: 欄の間のすき間 [dp]、見出しと値の間 [dp] */
    const val NUMBERS_CELL_GAP_DP = 10f
    const val NUMBERS_CAPTION_PAD_DP = 4f

    /**
     * 数値の1行目（TIME / ALT / RATE）の、いちばん長くなる見出しと値。この幅は必ず取る（切れないように）。
     * RATE は 60 秒の窓で 10 km 近く・高低差 3 桁まで
     */
    val NUMBERS_ROW1_SAMPLES = listOf("TIME" to "88:88:88", "ALT" to "8888 m", "RATE 60s" to "88.8 km  +888 m")

    /** 数値の1行目の、余った幅の分け方（TIME : ALT : RATE。前の欄の比率） */
    val NUMBERS_ROW1_WEIGHTS = listOf(1f, 0.85f, 1.55f)

    /** 文字の大きさ [sp]: 方位目盛り / 距離環（距離環の数字は、これに設定の大きさ ringLabelScalePct を掛ける） */
    const val LABEL_SP = 13f
    const val RING_LABEL_SP = 10f

    /** 距離環の数字の大きさ [%]（RING_LABEL_SP を 100% として）の既定と選べる値（保存値が選べる値にない数なら、一番近い段に読み替える） */
    const val RING_LABEL_SCALE_DEFAULT_PCT = 125
    val RING_LABEL_SCALE_CHOICES_PCT = listOf(100, 125, 150, 175, 200)

    /** WP の文字の大きさ [sp]: 地図上の名前・方位（前は 13）/ 画面外の矢印の距離・名前・方位（前は 11） */
    const val WP_LABEL_SP = 15f
    const val WP_ARROW_LABEL_SP = 13f

    /** 図形の大きさ [dp]: WP の印（ひし形の半径）/ 自機（三角の基準 / 方位なしの丸の半径） */
    const val WP_MARK_DP = 6f
    const val OWN_SHIP_DP = 11f
    const val OWN_SHIP_CIRCLE_DP = 7f

    /** 標高プロファイル: 線の太さ [dp]、点の半径 [dp]、文字 [sp] */
    const val PROFILE_LINE_DP = 1.8f
    const val PROFILE_POINT_RADIUS_DP = 3.5f
    const val PROFILE_TEXT_SP = 10f

    /** 標高プロファイル: 破線（標高のない WP を飛ばしてつないだ線）の線の長さ・すき間 [dp] */
    const val PROFILE_DASH_ON_DP = 5f
    const val PROFILE_DASH_OFF_DP = 4f

    /** 標高プロファイル: WP の名前を点の上に置く距離 [dp]（文字の中心まで） */
    const val PROFILE_NAME_OFFSET_DP = 10f

    // ---- リプレイ（§6.7） ----

    /** 倍速の選択肢（タップで順に切り替える） */
    val REPLAY_SPEEDS = listOf(1, 2, 5, 10, 30)

    /** リプレイの刻み（この間に来た Fix をまとめて入れ、画面を1回更新する）[ms] */
    const val REPLAY_POLL_MS = 50L

    /** 時刻だけを進める刻み（NO FIX・カウントダウンの更新）[ms] */
    const val TICK_MS = 200L
}
