package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.nav.LandmarkItem
import io.github.eightbrows.navhud.core.nav.MapItem
import io.github.eightbrows.navhud.core.nav.MapRange
import io.github.eightbrows.navhud.core.nav.RangeAuto
import kotlin.math.abs

/**
 * 設定画面のセクション（20261104-D02。20261121-D03 で並べ直し、到達の判定の欄を足し、移動のプリセットの欄をなくした）。
 * この並びで目次と本文を作る。key は閉じたセクションを覚えるときの名前（保存に使うので、前からの名前は変えない）。
 */
enum class SettingsSectionId(val key: String) {
    POSITION_HEADING("position"),
    /** 到達の判定（20261121-D03。前は WP の欄の細かい調整、20261121-D02 は移動のプリセットの欄） */
    REACH("reach"),
    RANGE("range"),
    MAP("map"),
    LANDMARKS("landmarks"),
    DISPLAY("display"),
    WP("wp"),
    RINGS_TRACK("ringsTrack"),
    OTHER("other"),
    INFO("info"),
}

/** 設定画面の項目（1 行、または表や欄のまとまり）。画面はこの並びのとおりに描く */
enum class SettingKey {
    // 測位と方位: プリセットの値（方位の出し方と、GPS の向き・位置から求める向きの 2 つの囲み。20261121-D03）
    HEADING_METHOD, GPS_BOX, TRK_BOX,
    HOLD_ENTER, HOLD_EXIT, MAX_GPS_BEARING_ACC, TRACK_DISTANCE,
    // 平均した方位線（20261122-D03。プリセットの値。方位の囲みのあと）
    AVG_LINE, AVG_SEC,
    // 測位と方位: 共通の値
    AGL_NEGATIVE, MAX_GPS_ACC, NO_FIX_TIMEOUT, ALT_OFFSET,

    // 到達の判定（プリセットの値）
    ARRIVAL_RADIUS, SIDE_PASS, SIDE_PASS_MAX, SIDE_PASS_DEPART, SIDE_PASS_MIN_SPEED, PASS_DETECTION, PASS_MAX, PASS_DEPART, PASS_HOLD,

    // 縮尺（プリセットの値。地図を動かしたあと戻るまでは共通）
    RANGE_STEPS, INITIAL_RANGE, AUTO_ON_START, AUTO_MIN, AUTO_MAX,
    ZOOM_IN_DELAY, HOLD_AFTER_WP, ZOOM_IN_RATIO, PAN_RETURN,

    // 地図（表はプリセットの値、背景地図・線の太さ・明るさは共通）
    MAP_TABLE, BASE_MAP, MAP_LINE_WIDTH, MAP_LINE_BRIGHTNESS,

    // ランドマーク（表はプリセットの値、明るさは共通）
    LANDMARK_TABLE, LANDMARK_BRIGHTNESS,

    // 画面（標高プロファイル・GS の平均・ETA の平均速度はプリセットの値。20261105-D01: 表示モードは設定画面からなくした）
    PROFILE, GS_AVERAGE, ETA_AVERAGE,
    UI_COLOR, MAP_COLOR, BUTTON_OPACITY, NUMBERS_OPACITY, OWNSHIP_POSITION,

    // WP（共通の値だけ）
    AUTO_OPEN_LAST, WP_BUTTONS, HUD_WP_COUNT,

    // 距離環と軌跡
    RING_BRIGHTNESS, RING_LABEL_SIZE, TRACK_COLOR, TRACK_BRIGHTNESS,
    // 軌跡の点の間隔（20261122-D03。プリセットの値）
    TRACK_STEP,

    // その他
    KEEP_SCREEN_ON, APP_LANGUAGE,

    // 権限・情報・データの出典（中身は 20261104-D01 までと同じ）
    PERMISSIONS, ABOUT, SOURCES,
}

/**
 * セクションの中身（20261121-D03）: 上に今のプリセットの値（preset。小見出し「自動車の値」など）、下に共通の値（common。小見出し「共通の値」）。
 * それぞれの「細かい調整」（presetFine・commonFine）は、その下に小さな見出しで続ける。値でない項目（権限・情報）は plain（小見出しなし）。
 * 片方しかないセクションは、その小見出しだけ
 */
data class SettingsSectionDef(
    val id: SettingsSectionId,
    val preset: List<SettingKey> = emptyList(),
    val presetFine: List<SettingKey> = emptyList(),
    val common: List<SettingKey> = emptyList(),
    val commonFine: List<SettingKey> = emptyList(),
    val plain: List<SettingKey> = emptyList(),
) {
    /** この欄の全部の項目（並びのとおり。囲みの中の項目も入れる） */
    val allItems: List<SettingKey>
        get() = (preset + presetFine + common + commonFine + plain).flatMap { k -> listOf(k) + SettingsOutline.boxItems(k) }

    /** プリセットの値の項目（囲みの中の項目も入れる） */
    val presetItems: List<SettingKey> get() = (preset + presetFine).flatMap { k -> listOf(k) + SettingsOutline.boxItems(k) }.filter { it !in SettingsOutline.BOXES }
}

object SettingsOutline {

    /** 測位と方位の囲み（20261121-D03）: GPS の向き（止める / 戻す速度・方位精度の上限）と、位置から求める向き（向きを求める距離） */
    val GPS_BOX_ITEMS = listOf(SettingKey.HOLD_ENTER, SettingKey.HOLD_EXIT, SettingKey.MAX_GPS_BEARING_ACC)
    val TRK_BOX_ITEMS = listOf(SettingKey.TRACK_DISTANCE)
    val BOXES = setOf(SettingKey.GPS_BOX, SettingKey.TRK_BOX)

    /** 囲みの中の項目（囲みでなければ空） */
    fun boxItems(k: SettingKey): List<SettingKey> = when (k) {
        SettingKey.GPS_BOX -> GPS_BOX_ITEMS
        SettingKey.TRK_BOX -> TRK_BOX_ITEMS
        else -> emptyList()
    }

    val SECTIONS: List<SettingsSectionDef> = listOf(
        SettingsSectionDef(
            SettingsSectionId.POSITION_HEADING,
            preset = listOf(SettingKey.HEADING_METHOD, SettingKey.GPS_BOX, SettingKey.TRK_BOX, SettingKey.AVG_LINE, SettingKey.AVG_SEC),
            common = listOf(SettingKey.AGL_NEGATIVE),
            commonFine = listOf(SettingKey.MAX_GPS_ACC, SettingKey.NO_FIX_TIMEOUT, SettingKey.ALT_OFFSET),
        ),
        // 到達の判定（並び・名前は 20261104-D01 までと同じ。20261119-D01 で真横通過の後ろに「判定を始める速さ」を足した）
        SettingsSectionDef(
            SettingsSectionId.REACH,
            preset = listOf(
                SettingKey.ARRIVAL_RADIUS, SettingKey.SIDE_PASS, SettingKey.SIDE_PASS_MAX, SettingKey.SIDE_PASS_DEPART, SettingKey.SIDE_PASS_MIN_SPEED,
                SettingKey.PASS_DETECTION, SettingKey.PASS_MAX, SettingKey.PASS_DEPART, SettingKey.PASS_HOLD,
            ),
        ),
        SettingsSectionDef(
            SettingsSectionId.RANGE,
            preset = listOf(SettingKey.RANGE_STEPS, SettingKey.INITIAL_RANGE, SettingKey.AUTO_ON_START, SettingKey.AUTO_MIN, SettingKey.AUTO_MAX),
            presetFine = listOf(SettingKey.ZOOM_IN_DELAY, SettingKey.HOLD_AFTER_WP, SettingKey.ZOOM_IN_RATIO),
            common = listOf(SettingKey.PAN_RETURN),
        ),
        SettingsSectionDef(
            SettingsSectionId.MAP,
            preset = listOf(SettingKey.MAP_TABLE),
            common = listOf(SettingKey.BASE_MAP, SettingKey.MAP_LINE_WIDTH, SettingKey.MAP_LINE_BRIGHTNESS),
        ),
        SettingsSectionDef(SettingsSectionId.LANDMARKS, preset = listOf(SettingKey.LANDMARK_TABLE), common = listOf(SettingKey.LANDMARK_BRIGHTNESS)),
        SettingsSectionDef(
            SettingsSectionId.DISPLAY,
            preset = listOf(SettingKey.PROFILE, SettingKey.GS_AVERAGE, SettingKey.ETA_AVERAGE),
            common = listOf(SettingKey.UI_COLOR, SettingKey.MAP_COLOR, SettingKey.BUTTON_OPACITY, SettingKey.NUMBERS_OPACITY, SettingKey.OWNSHIP_POSITION),
        ),
        SettingsSectionDef(SettingsSectionId.WP, common = listOf(SettingKey.AUTO_OPEN_LAST, SettingKey.WP_BUTTONS, SettingKey.HUD_WP_COUNT)),
        SettingsSectionDef(
            SettingsSectionId.RINGS_TRACK,
            // 軌跡の点の間隔はプリセットの値（20261122-D03）
            preset = listOf(SettingKey.TRACK_STEP),
            common = listOf(SettingKey.RING_BRIGHTNESS, SettingKey.RING_LABEL_SIZE, SettingKey.TRACK_COLOR, SettingKey.TRACK_BRIGHTNESS),
        ),
        SettingsSectionDef(SettingsSectionId.OTHER, common = listOf(SettingKey.KEEP_SCREEN_ON, SettingKey.APP_LANGUAGE)),
        SettingsSectionDef(SettingsSectionId.INFO, plain = listOf(SettingKey.PERMISSIONS, SettingKey.ABOUT, SettingKey.SOURCES)),
    )

    /**
     * 使う縮尺の初期値の短い書き方（20261121-D03。「（初期値：全部）」「（初期値：25〜2.5k）」）: 全部の段なら null（「全部」と書く）、
     * そうでなければ いちばん詳細の段と いちばん広域の段の組 [km]
     */
    fun rangeStepsSpan(stepsKm: List<Double>): Pair<Double, Double>? =
        if (RangeAuto.ALL_STEPS_KM.all { a -> stepsKm.any { abs(it - a) < 1e-9 } }) null else stepsKm.min() to stepsKm.max()

    // ---- 閉じたセクションの覚え（スクロール位置と同じ、画面の状態。NavSettings の外なので「設定を初期値に戻す」では変わらない）----

    /** 閉じたセクションを保存の文字にする（セクションの並びの順に key をカンマでつなぐ。全部開いていれば空の文字） */
    fun encodeClosed(closed: Set<SettingsSectionId>): String =
        SettingsSectionId.entries.filter { it in closed }.joinToString(",") { it.key }

    /** 保存の文字から読む。覚えがなければ（初めて）全部開く。知らない名前は読み飛ばす */
    fun decodeClosed(s: String?): Set<SettingsSectionId> {
        if (s.isNullOrBlank()) return emptySet()
        val keys = s.split(',').map { it.trim() }.toSet()
        return SettingsSectionId.entries.filter { it.key in keys }.toSet()
    }

    /** 見出しを押したとき: 開いていれば閉じ、閉じていれば開く */
    fun toggle(closed: Set<SettingsSectionId>, id: SettingsSectionId): Set<SettingsSectionId> =
        if (id in closed) closed - id else closed + id

    /** 目次のボタンを押したとき: 閉じていれば開く（そのあとそのセクションへスクロールする） */
    fun openForJump(closed: Set<SettingsSectionId>, id: SettingsSectionId): Set<SettingsSectionId> = closed - id

    /** 「▼ 全て開く」 */
    fun openAll(): Set<SettingsSectionId> = emptySet()

    /** 「▲ 全て閉じる」 */
    fun closeAll(): Set<SettingsSectionId> = SettingsSectionId.entries.toSet()

    // ---- 縮尺の段のマス（使用する縮尺・起動時の縮尺・自動縮尺での広域の上限）----

    /** 1 行のマスの数。上の行が R1 25 / 50 / 100 / 250 / 500 / 1k、下の行が 2.5k / 5k / 10k / 25k / 50k / 100k */
    const val RANGE_GRID_COLUMNS = 6

    /**
     * 12 段のマス（RangeAuto.ALL_STEPS_KM の順）に、選べる段を置く。選べない段のマスは null（空けて、列を揃える。
     * 例: 自動縮尺での広域の上限は詳細の下限（初期値 R1 50）から上なので、左上の R1 25 のマスは空く）
     */
    fun rangeGridSlots(choicesKm: List<Double>): List<Double?> =
        RangeAuto.ALL_STEPS_KM.map { step -> choicesKm.firstOrNull { abs(it - step) < 1e-9 } }

    /** 使用する縮尺のマスを押したとき: 入っていれば外し、なければ入れる。1 つも残らなくなるときは変えない */
    fun toggleRangeStep(stepsKm: List<Double>, km: Double): List<Double> {
        val on = stepsKm.any { abs(it - km) < 1e-9 }
        val next = if (on) stepsKm.filter { abs(it - km) >= 1e-9 } else (stepsKm + km).sorted()
        return next.ifEmpty { stepsKm }
    }

    // ---- 地図・ランドマークの表（行が項目、列が OFF / 〜5k / 〜10k / 〜25k / 〜50k / 〜100k）----

    /** 表の列（左から）。列の見出しは表の上に 1 回だけ */
    val TABLE_COLUMNS: List<MapRange> = MapRange.entries.toList()

    /** 地図の表の行（上から）: 海岸線・境・道路・鉄道・水・フェリー（空港・山頂はランドマークの表） */
    val MAP_TABLE_ROWS: List<MapItem> = listOf(
        MapItem.COASTLINE, MapItem.PREF_BOUNDARY, MapItem.MUNI_BOUNDARY, MapItem.MOTORWAY, MapItem.OTHER_MOTORWAY, MapItem.NATIONAL_ROAD, MapItem.PREF_ROAD,
        MapItem.SHINKANSEN, MapItem.RAILWAY, MapItem.WATER, MapItem.FERRY,
    )

    /** ランドマークの表の行（上から）: 空港の民間・軍用の大小、敷地、略号、滑走路、延長線、山頂の高中低 */
    val LANDMARK_TABLE_ROWS: List<LandmarkItem> = LandmarkItem.entries.toList()

    /** 表の 1 マスの見た目: selected（水色の丸）は今の値、isDefault（灰色の塗り）は初期値 */
    data class TableCell(val column: MapRange, val selected: Boolean, val isDefault: Boolean)

    /** 1 行のマス（左から）。今の値の列だけ selected、初期値の列だけ isDefault */
    fun tableRow(current: MapRange, default: MapRange): List<TableCell> =
        TABLE_COLUMNS.map { TableCell(it, selected = it == current, isDefault = it == default) }
}
