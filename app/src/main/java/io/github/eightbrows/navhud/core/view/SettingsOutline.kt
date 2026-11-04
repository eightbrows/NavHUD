package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.nav.LandmarkItem
import io.github.eightbrows.navhud.core.nav.MapItem
import io.github.eightbrows.navhud.core.nav.MapRange
import io.github.eightbrows.navhud.core.nav.RangeAuto
import kotlin.math.abs

/**
 * 設定画面のセクション（20261104-D02）。この並びで目次と本文を作る。
 * key は閉じたセクションを覚えるときの名前（保存に使うので変えない）。
 */
enum class SettingsSectionId(val key: String) {
    POSITION_HEADING("position"),
    RANGE("range"),
    WP("wp"),
    DISPLAY("display"),
    RINGS_TRACK("ringsTrack"),
    MAP("map"),
    LANDMARKS("landmarks"),
    OTHER("other"),
    INFO("info"),
}

/** 設定画面の項目（1 行、または表や欄のまとまり）。画面はこの並びのとおりに描く */
enum class SettingKey {
    // 測位と方位
    HEADING_SOURCE,
    HOLD_ENTER, HOLD_EXIT, MAX_GPS_ACC, MAX_GPS_BEARING_ACC, NO_FIX_TIMEOUT, ALT_OFFSET,

    // 縮尺
    RANGE_STEPS, INITIAL_RANGE, AUTO_ON_START, AUTO_MIN, AUTO_MAX,
    ZOOM_IN_DELAY, HOLD_AFTER_WP, ZOOM_IN_RATIO, PAN_RETURN,

    // WP
    TRAVEL_MODE, WP_BUTTONS, HUD_WP_COUNT,
    ARRIVAL_RADIUS, SIDE_PASS, SIDE_PASS_MAX, SIDE_PASS_DEPART, PASS_DETECTION, PASS_MAX, PASS_DEPART, PASS_HOLD,

    // 画面
    DISPLAY_MODE, UI_COLOR, MAP_COLOR, BUTTON_OPACITY, NUMBERS_OPACITY, OWNSHIP_POSITION, PROFILE, AGL_NEGATIVE,

    // 距離環と軌跡
    RING_BRIGHTNESS, RING_LABEL_SIZE, TRACK_COLOR, TRACK_BRIGHTNESS,

    // 地図
    BASE_MAP, MAP_LINE_WIDTH, MAP_LINE_BRIGHTNESS, MAP_TABLE,

    // ランドマーク
    LANDMARK_BRIGHTNESS, LANDMARK_TABLE,

    // その他
    KEEP_SCREEN_ON, AUTO_OPEN_LAST, APP_LANGUAGE,

    // 権限・情報・データの出典（中身は 20261104-D01 までと同じ）
    PERMISSIONS, ABOUT, SOURCES,
}

/** セクションの中身: 上の項目 items と、「細かい調整」の見出しの下の項目 fine（なければ空。たたまない） */
data class SettingsSectionDef(val id: SettingsSectionId, val items: List<SettingKey>, val fine: List<SettingKey> = emptyList())

object SettingsOutline {

    val SECTIONS: List<SettingsSectionDef> = listOf(
        SettingsSectionDef(
            SettingsSectionId.POSITION_HEADING,
            listOf(SettingKey.HEADING_SOURCE),
            listOf(SettingKey.HOLD_ENTER, SettingKey.HOLD_EXIT, SettingKey.MAX_GPS_ACC, SettingKey.MAX_GPS_BEARING_ACC, SettingKey.NO_FIX_TIMEOUT, SettingKey.ALT_OFFSET),
        ),
        SettingsSectionDef(
            SettingsSectionId.RANGE,
            listOf(SettingKey.RANGE_STEPS, SettingKey.INITIAL_RANGE, SettingKey.AUTO_ON_START, SettingKey.AUTO_MIN, SettingKey.AUTO_MAX),
            listOf(SettingKey.ZOOM_IN_DELAY, SettingKey.HOLD_AFTER_WP, SettingKey.ZOOM_IN_RATIO, SettingKey.PAN_RETURN),
        ),
        SettingsSectionDef(
            SettingsSectionId.WP,
            listOf(SettingKey.TRAVEL_MODE, SettingKey.WP_BUTTONS, SettingKey.HUD_WP_COUNT),
            // 到達の判定（並び・名前は 20261104-D01 までと同じ）
            listOf(
                SettingKey.ARRIVAL_RADIUS, SettingKey.SIDE_PASS, SettingKey.SIDE_PASS_MAX, SettingKey.SIDE_PASS_DEPART,
                SettingKey.PASS_DETECTION, SettingKey.PASS_MAX, SettingKey.PASS_DEPART, SettingKey.PASS_HOLD,
            ),
        ),
        SettingsSectionDef(
            SettingsSectionId.DISPLAY,
            listOf(
                SettingKey.DISPLAY_MODE, SettingKey.UI_COLOR, SettingKey.MAP_COLOR, SettingKey.BUTTON_OPACITY, SettingKey.NUMBERS_OPACITY,
                SettingKey.OWNSHIP_POSITION, SettingKey.PROFILE, SettingKey.AGL_NEGATIVE,
            ),
        ),
        SettingsSectionDef(
            SettingsSectionId.RINGS_TRACK,
            listOf(SettingKey.RING_BRIGHTNESS, SettingKey.RING_LABEL_SIZE, SettingKey.TRACK_COLOR, SettingKey.TRACK_BRIGHTNESS),
        ),
        SettingsSectionDef(SettingsSectionId.MAP, listOf(SettingKey.BASE_MAP, SettingKey.MAP_LINE_WIDTH, SettingKey.MAP_LINE_BRIGHTNESS, SettingKey.MAP_TABLE)),
        SettingsSectionDef(SettingsSectionId.LANDMARKS, listOf(SettingKey.LANDMARK_BRIGHTNESS, SettingKey.LANDMARK_TABLE)),
        SettingsSectionDef(SettingsSectionId.OTHER, listOf(SettingKey.KEEP_SCREEN_ON, SettingKey.AUTO_OPEN_LAST, SettingKey.APP_LANGUAGE)),
        SettingsSectionDef(SettingsSectionId.INFO, listOf(SettingKey.PERMISSIONS, SettingKey.ABOUT, SettingKey.SOURCES)),
    )

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
