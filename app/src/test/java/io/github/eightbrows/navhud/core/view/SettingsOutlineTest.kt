package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.LandmarkItem
import io.github.eightbrows.navhud.core.nav.MapItem
import io.github.eightbrows.navhud.core.nav.MapRange
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.RangeAuto
import io.github.eightbrows.navhud.core.nav.SettingsCodec
import io.github.eightbrows.navhud.core.nav.resetAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 設定画面の作り直し（20261104-D02）: セクションと項目の並び、閉じたセクションの覚え、縮尺の段のマス、地図・ランドマークの表の選び方。
 * 画面（SettingsScreen）はこの並びのとおりに描く
 */
class SettingsOutlineTest {

    private val sections = SettingsOutline.SECTIONS

    @Test
    fun tenSectionsInOrder() {
        // 20261121-D03: 測位と方位・到達の判定・縮尺・地図・ランドマーク・画面・WP・距離環と軌跡・その他・権限…（移動のプリセットの欄はやめて、帯にした）
        assertEquals(
            listOf(
                SettingsSectionId.POSITION_HEADING, SettingsSectionId.REACH, SettingsSectionId.RANGE, SettingsSectionId.MAP, SettingsSectionId.LANDMARKS,
                SettingsSectionId.DISPLAY, SettingsSectionId.WP, SettingsSectionId.RINGS_TRACK, SettingsSectionId.OTHER, SettingsSectionId.INFO,
            ),
            sections.map { it.id },
        )
        // 目次と本文は同じ並び（セクションの enum の並びとも同じ）
        assertEquals(SettingsSectionId.entries.toList(), sections.map { it.id })
    }

    /** 20261121-D03: 欄の中は、プリセットの値（上）と共通の値（下）。片方しかない欄はその小見出しだけ */
    @Test
    fun presetAndCommonItemsOfEachSection() {
        fun sec(id: SettingsSectionId) = sections.single { it.id == id }
        with(sec(SettingsSectionId.POSITION_HEADING)) {
            // 方位の出し方の下に、GPS の向き・位置から求める向きの 2 つの囲み
            // 20261122-D03: 方位の囲みのあとに平均した方位線・平均する秒数
            assertEquals(listOf(SettingKey.HEADING_METHOD, SettingKey.GPS_BOX, SettingKey.TRK_BOX, SettingKey.AVG_LINE, SettingKey.AVG_SEC), preset)
            assertEquals(listOf(SettingKey.HOLD_ENTER, SettingKey.HOLD_EXIT, SettingKey.MAX_GPS_BEARING_ACC), SettingsOutline.boxItems(SettingKey.GPS_BOX))
            assertEquals(listOf(SettingKey.TRACK_DISTANCE), SettingsOutline.boxItems(SettingKey.TRK_BOX))
            assertEquals(listOf(SettingKey.AGL_NEGATIVE), common)
            assertEquals(listOf(SettingKey.MAX_GPS_ACC, SettingKey.NO_FIX_TIMEOUT, SettingKey.ALT_OFFSET), commonFine)
        }
        with(sec(SettingsSectionId.REACH)) {
            // 到達の判定は前と同じ並び
            assertEquals(
                listOf(
                    SettingKey.ARRIVAL_RADIUS, SettingKey.SIDE_PASS, SettingKey.SIDE_PASS_MAX, SettingKey.SIDE_PASS_DEPART, SettingKey.SIDE_PASS_MIN_SPEED,
                    SettingKey.PASS_DETECTION, SettingKey.PASS_MAX, SettingKey.PASS_DEPART, SettingKey.PASS_HOLD,
                ),
                preset,
            )
            assertTrue(common.isEmpty())
        }
        with(sec(SettingsSectionId.RANGE)) {
            assertEquals(listOf(SettingKey.RANGE_STEPS, SettingKey.INITIAL_RANGE, SettingKey.AUTO_ON_START, SettingKey.AUTO_MIN, SettingKey.AUTO_MAX), preset)
            assertEquals(listOf(SettingKey.ZOOM_IN_DELAY, SettingKey.HOLD_AFTER_WP, SettingKey.ZOOM_IN_RATIO), presetFine)
            assertEquals(listOf(SettingKey.PAN_RETURN), common)
        }
        with(sec(SettingsSectionId.MAP)) {
            assertEquals(listOf(SettingKey.MAP_TABLE), preset)
            assertEquals(listOf(SettingKey.BASE_MAP, SettingKey.MAP_LINE_WIDTH, SettingKey.MAP_LINE_BRIGHTNESS), common)
        }
        with(sec(SettingsSectionId.LANDMARKS)) {
            assertEquals(listOf(SettingKey.LANDMARK_TABLE), preset)
            assertEquals(listOf(SettingKey.LANDMARK_BRIGHTNESS), common)
        }
        with(sec(SettingsSectionId.DISPLAY)) {
            // 標高プロファイル・GS の平均・ETA の平均速度はプリセットの値（20261121-D03。前は WP・画面の細かい調整）
            assertEquals(listOf(SettingKey.PROFILE, SettingKey.GS_AVERAGE, SettingKey.ETA_AVERAGE), preset)
            // 20261105-D01: 表示モードはなくした（上部バーだけ）
            assertEquals(listOf(SettingKey.UI_COLOR, SettingKey.MAP_COLOR, SettingKey.BUTTON_OPACITY, SettingKey.NUMBERS_OPACITY, SettingKey.OWNSHIP_POSITION), common)
        }
        // WP は共通の値だけ
        with(sec(SettingsSectionId.WP)) {
            assertTrue(preset.isEmpty())
            assertEquals(listOf(SettingKey.AUTO_OPEN_LAST, SettingKey.WP_BUTTONS, SettingKey.HUD_WP_COUNT), common)
        }
        assertEquals(
            listOf(SettingKey.RING_BRIGHTNESS, SettingKey.RING_LABEL_SIZE, SettingKey.TRACK_COLOR, SettingKey.TRACK_BRIGHTNESS),
            sec(SettingsSectionId.RINGS_TRACK).common,
        )
        // 20261122-D03: 軌跡の点の間隔、20261122-D04: 軌跡のなめらかさ（その下）はプリセットの値
        assertEquals(listOf(SettingKey.TRACK_STEP, SettingKey.TRACK_SMOOTH), sec(SettingsSectionId.RINGS_TRACK).preset)
        assertEquals(listOf(SettingKey.KEEP_SCREEN_ON, SettingKey.APP_LANGUAGE), sec(SettingsSectionId.OTHER).common)
        assertEquals(listOf(SettingKey.PERMISSIONS, SettingKey.ABOUT, SettingKey.SOURCES), sec(SettingsSectionId.INFO).plain)
        // プリセットの値がある欄・共通の値がある欄
        assertEquals(
            listOf(
                SettingsSectionId.POSITION_HEADING, SettingsSectionId.REACH, SettingsSectionId.RANGE, SettingsSectionId.MAP, SettingsSectionId.LANDMARKS, SettingsSectionId.DISPLAY,
                SettingsSectionId.RINGS_TRACK,
            ),
            sections.filter { it.preset.isNotEmpty() }.map { it.id },
        )
        assertEquals(
            listOf(
                SettingsSectionId.POSITION_HEADING, SettingsSectionId.RANGE, SettingsSectionId.MAP, SettingsSectionId.LANDMARKS, SettingsSectionId.DISPLAY,
                SettingsSectionId.WP, SettingsSectionId.RINGS_TRACK, SettingsSectionId.OTHER,
            ),
            sections.filter { it.common.isNotEmpty() }.map { it.id },
        )
    }

    /** プリセットの値の項目は、プリセットの値の数え方（PresetFields）と合う（表は行ごと、止める / 戻す速度は 2 つ） */
    @Test
    fun presetItemsMatchThePresetFields() {
        val items = sections.flatMap { it.presetItems }.toSet()
        assertEquals(
            setOf(
                SettingKey.HEADING_METHOD, SettingKey.HOLD_ENTER, SettingKey.HOLD_EXIT, SettingKey.MAX_GPS_BEARING_ACC, SettingKey.TRACK_DISTANCE,
                SettingKey.ARRIVAL_RADIUS, SettingKey.SIDE_PASS, SettingKey.SIDE_PASS_MAX, SettingKey.SIDE_PASS_DEPART, SettingKey.SIDE_PASS_MIN_SPEED,
                SettingKey.PASS_DETECTION, SettingKey.PASS_MAX, SettingKey.PASS_DEPART, SettingKey.PASS_HOLD,
                SettingKey.RANGE_STEPS, SettingKey.INITIAL_RANGE, SettingKey.AUTO_ON_START, SettingKey.AUTO_MIN, SettingKey.AUTO_MAX,
                SettingKey.ZOOM_IN_DELAY, SettingKey.HOLD_AFTER_WP, SettingKey.ZOOM_IN_RATIO,
                SettingKey.MAP_TABLE, SettingKey.LANDMARK_TABLE, SettingKey.PROFILE, SettingKey.GS_AVERAGE, SettingKey.ETA_AVERAGE,
                SettingKey.AVG_LINE, SettingKey.AVG_SEC, SettingKey.TRACK_STEP, SettingKey.TRACK_SMOOTH,
            ),
            items,
        )
        // 数え方: 表の行（11 + 11）を 1 つずつ、ほかは 1 項目 1 つ（止める / 戻す速度は 2 つ）
        assertEquals(items.size - 2 + 11 + 11, io.github.eightbrows.navhud.core.nav.PresetFields.ALL.size)
        assertEquals(11, SettingsOutline.MAP_TABLE_ROWS.size)
        assertEquals(11, SettingsOutline.LANDMARK_TABLE_ROWS.size)
    }

    @Test
    fun everyItemAppearsExactlyOnce() {
        val all = sections.flatMap { it.allItems }
        assertEquals(all.size, all.toSet().size)
        assertEquals(SettingKey.entries.toSet(), all.toSet())
        // INPUT（LIVE / REPLAY）は設定画面にない（上部バーで切り替える）
        assertFalse(SettingKey.entries.any { it.name.contains("INPUT") })
    }

    @Test
    fun displayModeIsOnlyOnTheTopBar() {
        // 20261105-D01: 表示モード（ARC / North Up）は設定画面にない。前のモードを覚える決まりと、設定を初期値に戻すと ARC になる決まりは今のまま
        assertFalse(SettingKey.entries.any { it.name.contains("DISPLAY_MODE") })
        val nu = NavSettings(displayMode = DisplayMode.NORTH_UP)
        assertEquals(DisplayMode.NORTH_UP, SettingsCodec.decode(SettingsCodec.encode(nu)).displayMode)
        assertEquals(DisplayMode.ARC, nu.resetAll().displayMode)
        assertEquals(DisplayMode.ARC, NavSettings().displayMode)
    }

    @Test
    fun closedSectionsAreRemembered() {
        // 初めて（覚えがない）は全部開く
        assertEquals(emptySet<SettingsSectionId>(), SettingsOutline.decodeClosed(null))
        assertEquals(emptySet<SettingsSectionId>(), SettingsOutline.decodeClosed(""))
        // 保存の文字はセクションの並びの順。読み戻すと同じ
        val closed = setOf(SettingsSectionId.MAP, SettingsSectionId.POSITION_HEADING, SettingsSectionId.INFO)
        assertEquals("position,map,info", SettingsOutline.encodeClosed(closed))
        assertEquals(closed, SettingsOutline.decodeClosed(SettingsOutline.encodeClosed(closed)))
        for (id in SettingsSectionId.entries) assertEquals(setOf(id), SettingsOutline.decodeClosed(SettingsOutline.encodeClosed(setOf(id))))
        assertEquals("", SettingsOutline.encodeClosed(emptySet()))
        // 知らない名前・空白は読み飛ばす
        assertEquals(setOf(SettingsSectionId.WP, SettingsSectionId.OTHER), SettingsOutline.decodeClosed(" wp ,old,,other"))
        // 保存の名前は変えない（前のバージョンで閉じたセクションがそのまま戻るように）
        assertEquals(
            listOf("position", "reach", "range", "map", "landmarks", "display", "wp", "ringsTrack", "other", "info"),
            SettingsSectionId.entries.map { it.key },
        )
        // 20261121-D03: 前の版の「移動のプリセット」の欄の覚え（travelPreset）は読み飛ばす。新しい到達の判定の欄は、前の覚えでは開いている
        assertEquals(setOf(SettingsSectionId.POSITION_HEADING, SettingsSectionId.WP), SettingsOutline.decodeClosed("travelPreset,position,wp"))
        assertFalse(SettingsSectionId.REACH in SettingsOutline.decodeClosed("travelPreset,position,range,wp,display,ringsTrack,map,landmarks,other,info"))
        // 閉じれば覚える。全部閉じるにも入る
        assertEquals("reach,map", SettingsOutline.encodeClosed(setOf(SettingsSectionId.MAP, SettingsSectionId.REACH)))
        assertTrue(SettingsSectionId.REACH in SettingsOutline.closeAll())
        // 設定の保存（NavSettings）とは別: 設定を初期値に戻しても変わらない（SettingsCodec の保存の名前に入っていない）
        assertFalse(io.github.eightbrows.navhud.core.nav.SettingsCodec.encode(NavSettings()).keys.any { it.contains("Closed") || it.contains("closed") })
    }

    @Test
    fun openCloseAndJump() {
        var closed = emptySet<SettingsSectionId>()
        closed = SettingsOutline.toggle(closed, SettingsSectionId.RANGE)
        assertEquals(setOf(SettingsSectionId.RANGE), closed)
        closed = SettingsOutline.toggle(closed, SettingsSectionId.MAP)
        closed = SettingsOutline.toggle(closed, SettingsSectionId.RANGE)
        assertEquals(setOf(SettingsSectionId.MAP), closed)
        // 目次のボタン: 閉じていれば開く。開いていればそのまま
        assertEquals(emptySet<SettingsSectionId>(), SettingsOutline.openForJump(closed, SettingsSectionId.MAP))
        assertEquals(closed, SettingsOutline.openForJump(closed, SettingsSectionId.WP))
        // 全て開く・全て閉じる
        assertEquals(emptySet<SettingsSectionId>(), SettingsOutline.openAll())
        assertEquals(SettingsSectionId.entries.toSet(), SettingsOutline.closeAll())
        assertEquals("position,reach,range,map,landmarks,display,wp,ringsTrack,other,info", SettingsOutline.encodeClosed(SettingsOutline.closeAll()))
    }

    @Test
    fun rangeGridKeepsTheColumns() {
        assertEquals(6, SettingsOutline.RANGE_GRID_COLUMNS)
        val all = RangeAuto.ALL_STEPS_KM
        assertEquals(12, all.size)
        // 使用する縮尺: 12 段が全部のマスに入る（上の行 R1 25〜1k、下の行 2.5k〜100k）
        val slots = SettingsOutline.rangeGridSlots(all)
        assertEquals(all, slots)
        assertEquals(listOf("25", "50", "100", "250", "500", "1k"), slots.take(6).map { HudFormat.ringLabel(it!! * 1000 / 2) })
        assertEquals(listOf("2.5k", "5k", "10k", "25k", "50k", "100k"), slots.drop(6).map { HudFormat.ringLabel(it!! * 1000 / 2) })
        // 自動縮尺での広域の上限（初期値）: 詳細の下限 R1 50 から上なので、左上の R1 25 のマスだけ空く
        val d = NavSettings()
        val (lo, _) = RangeAuto.limitsKm(d.rangeStepsKm, d.autoMinRangeKm, d.autoMaxRangeKm)
        val wide = SettingsOutline.rangeGridSlots(d.rangeStepsKm.filter { it >= lo })
        assertEquals(12, wide.size)
        assertEquals(null, wide[0])
        assertEquals(all.drop(1), wide.drop(1))
        // 使う段を減らすと、その段のマスが空く（ほかのマスの列は動かない）
        val few = SettingsOutline.rangeGridSlots(listOf(0.5, 5.0, 50.0))
        assertEquals(listOf(null, null, null, 0.5, null, null, 5.0, null, null, 50.0, null, null), few)
    }

    @Test
    fun rangeStepToggleKeepsAtLeastOne() {
        assertEquals(listOf(0.5, 1.0), SettingsOutline.toggleRangeStep(listOf(0.5), 1.0))
        assertEquals(listOf(0.1, 0.5), SettingsOutline.toggleRangeStep(listOf(0.5), 0.1))
        assertEquals(listOf(0.5), SettingsOutline.toggleRangeStep(listOf(0.5, 1.0), 1.0))
        // 最後の 1 つは外せない
        assertEquals(listOf(0.5), SettingsOutline.toggleRangeStep(listOf(0.5), 0.5))
    }

    @Test
    fun tablesHaveElevenRowsAndSixColumns() {
        assertEquals(listOf(MapRange.OFF, MapRange.UPTO_5KM, MapRange.UPTO_10KM, MapRange.UPTO_25KM, MapRange.UPTO_50KM, MapRange.UPTO_100KM), SettingsOutline.TABLE_COLUMNS)
        assertEquals(11, SettingsOutline.MAP_TABLE_ROWS.size)
        assertEquals(11, SettingsOutline.LANDMARK_TABLE_ROWS.size)
        // 地図の表は全部の線の項目（空港・山頂はランドマークの表）。ランドマークの表は全部のランドマークの項目
        assertEquals(MapItem.entries.toSet(), SettingsOutline.MAP_TABLE_ROWS.toSet())
        assertEquals(LandmarkItem.entries.toList(), SettingsOutline.LANDMARK_TABLE_ROWS)
        assertEquals(MapItem.COASTLINE, SettingsOutline.MAP_TABLE_ROWS.first())
        assertEquals(MapItem.FERRY, SettingsOutline.MAP_TABLE_ROWS.last())
    }

    @Test
    fun tableSelection() {
        // 1 行のマス: 今の値の列だけ水色の丸、初期値の列だけ灰色
        val row = SettingsOutline.tableRow(current = MapRange.UPTO_25KM, default = MapRange.UPTO_100KM)
        assertEquals(SettingsOutline.TABLE_COLUMNS, row.map { it.column })
        assertEquals(listOf(MapRange.UPTO_25KM), row.filter { it.selected }.map { it.column })
        assertEquals(listOf(MapRange.UPTO_100KM), row.filter { it.isDefault }.map { it.column })
        // 初期値を選んでいるときは、同じマスが両方
        val same = SettingsOutline.tableRow(MapRange.OFF, MapRange.OFF)
        assertTrue(same[0].selected && same[0].isDefault)
        assertEquals(1, same.count { it.selected || it.isDefault })

        // マスを押すと、その行の項目だけがその列の値になる（地図・ランドマークとも。保存の値は前と同じ）
        val d = NavSettings()
        for (item in SettingsOutline.MAP_TABLE_ROWS) for (col in SettingsOutline.TABLE_COLUMNS) {
            val s = d.withMapRange(item, col)
            val cells = SettingsOutline.tableRow(s.mapRangeOf(item), d.mapRangeOf(item))
            assertEquals("$item $col", listOf(col), cells.filter { it.selected }.map { it.column })
            for (other in SettingsOutline.MAP_TABLE_ROWS - item) assertEquals(d.mapRangeOf(other), s.mapRangeOf(other))
        }
        for (item in SettingsOutline.LANDMARK_TABLE_ROWS) for (col in SettingsOutline.TABLE_COLUMNS) {
            val s = d.withLandmarkRange(item, col)
            assertEquals(col, s.landmarkRangeOf(item))
            assertEquals(listOf(col), SettingsOutline.tableRow(s.landmarkRangeOf(item), d.landmarkRangeOf(item)).filter { it.selected }.map { it.column })
            for (other in SettingsOutline.LANDMARK_TABLE_ROWS - item) assertEquals(d.landmarkRangeOf(other), s.landmarkRangeOf(other))
        }
        // 初期値の灰色のマスは、どの行も 1 つだけ
        for (item in SettingsOutline.MAP_TABLE_ROWS) assertEquals(1, SettingsOutline.tableRow(d.mapRangeOf(item), d.mapRangeOf(item)).count { it.isDefault })
    }
}
