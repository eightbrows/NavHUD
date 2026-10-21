package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.io.MapLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 20261018-D04: 背景地図の設定の形（項目ごとに「どの縮尺まで描くか」、地図全体の線の太さ・明るさ）。
 * 初期値・保存と読み込み・初期値に戻す・使うレイヤ。
 */
class MapItemSettingsTest {

    @Test
    fun defaultsAreTheTemporaryValues() {
        val d = NavSettings()
        assertTrue(d.baseMap)
        // 高速道路・国道・空港は「すべて」、ほかは OFF（20261021-D01）
        val all = setOf(MapItem.MOTORWAY, MapItem.NATIONAL_ROAD, MapItem.PEAK, MapItem.AIRPORT)
        for (item in MapItem.entries) assertEquals(item.name, if (item in all) MapRange.ALL else MapRange.OFF, d.mapRangeOf(item))
        // 線の太さ・明るさは 4（今の太さ・色）。選べるのは 1〜6
        assertEquals(4, d.mapLineWidth)
        assertEquals(4, d.mapLineBrightness)
        assertEquals(1..6, NavSettings.MAP_LINE_LEVELS)
        // 使うレイヤ（開くレイヤ）は、OFF でない項目のレイヤ
        assertEquals(setOf(MapLayer.ROAD, MapLayer.PEAK, MapLayer.AIRPORT), d.mapLayersInUse())
        // 河川・湖沼は 2 つのレイヤ
        assertEquals(setOf(MapLayer.ROAD, MapLayer.PEAK, MapLayer.AIRPORT, MapLayer.RIVER, MapLayer.INLAND_WATER), d.withMapRange(MapItem.WATER, MapRange.ALL).mapLayersInUse())
        // 道路の項目は、どれか 1 つでも OFF でなければ道路のレイヤを使う
        val noRoads = d.withMapRange(MapItem.MOTORWAY, MapRange.OFF).withMapRange(MapItem.NATIONAL_ROAD, MapRange.OFF)
        assertFalse(MapLayer.ROAD in noRoads.mapLayersInUse())
        assertTrue(MapLayer.ROAD in noRoads.withMapRange(MapItem.PREF_ROAD, MapRange.UPTO_10KM).mapLayersInUse())
    }

    @Test
    fun rangeShowsWhileR1IsAtOrBelowTheValue() {
        assertFalse(MapRange.OFF.shows(25.0))
        assertTrue(MapRange.UPTO_10KM.shows(10_000.0))
        assertFalse(MapRange.UPTO_10KM.shows(25_000.0))
        assertTrue(MapRange.UPTO_25KM.shows(25_000.0))
        assertFalse(MapRange.UPTO_25KM.shows(50_000.0))
        assertTrue(MapRange.UPTO_50KM.shows(50_000.0))
        assertFalse(MapRange.UPTO_50KM.shows(100_000.0))
        assertTrue(MapRange.ALL.shows(100_000.0))
    }

    @Test
    fun itemsAndLineLevelsAreSavedAndReadBack() {
        val s = NavSettings(mapLineWidth = 3, mapLineBrightness = 2)
            .withMapRange(MapItem.MOTORWAY, MapRange.ALL).withMapRange(MapItem.NATIONAL_ROAD, MapRange.UPTO_50KM)
            .withMapRange(MapItem.PREF_ROAD, MapRange.UPTO_10KM).withMapRange(MapItem.WATER, MapRange.UPTO_25KM)
        val enc = SettingsCodec.encode(s)
        // 全部の項目を「名前:縮尺」で（MapItem の順）
        assertTrue(enc.getValue("mapItems").startsWith("COASTLINE:OFF,PREF_BOUNDARY:OFF,MUNI_BOUNDARY:OFF,MOTORWAY:ALL,OTHER_MOTORWAY:OFF,NATIONAL_ROAD:UPTO_50KM,PREF_ROAD:UPTO_10KM,"))
        assertEquals("3", enc["mapLineWidthLevel"])
        val back = SettingsCodec.decode(enc)
        assertEquals(s.mapItems, back.mapItems)
        assertEquals(3, back.mapLineWidth)
        assertEquals(2, back.mapLineBrightness)
        // 保存がない・前の形（mapLayers など）だけの保存は初期値（前の形は引き継がない）
        val old = SettingsCodec.decode(mapOf("mapLayers" to "ROAD,CITY", "mapBrightness" to "ROAD:5", "mapPrefRoads" to "true"))
        assertEquals(NavSettings.DEFAULT_MAP_ITEMS, old.mapItems)
        assertEquals(NavSettings.DEFAULT_MAP_ITEMS, SettingsCodec.decode(emptyMap()).mapItems)
        // 読めない名前・縮尺は飛ばす（その項目は初期値）。保存にない項目も初期値
        val bad = SettingsCodec.decode(mapOf("mapItems" to "MOTORWAY:UPTO_25KM,FOO:ALL,COASTLINE:BAR,CITY,AIRPORT:UPTO_10KM", "mapLineWidthLevel" to "7", "mapLineBrightnessLevel" to "x"))
        assertEquals(MapRange.UPTO_25KM, bad.mapRangeOf(MapItem.MOTORWAY))
        assertEquals(NavSettings.DEFAULT_MAP_ITEMS.getValue(MapItem.COASTLINE), bad.mapRangeOf(MapItem.COASTLINE))
        assertEquals(MapRange.UPTO_10KM, bad.mapRangeOf(MapItem.AIRPORT))
        // なくした項目（港・駅・ダムと堰の点・都市・市街地は 20261020-D01、ダム・堰の線は 20261020-D02）の保存は、読めない名前として飛ばす
        val removed = SettingsCodec.decode(mapOf("mapItems" to "PORT:ALL,STATION:ALL,DAM_POINT:ALL,CITY:ALL,BUILT_UP_AREA:ALL,DAM_LINE:ALL,RAILWAY:UPTO_50KM"))
        assertEquals(MapRange.UPTO_50KM, removed.mapRangeOf(MapItem.RAILWAY))
        assertEquals(NavSettings.DEFAULT_MAP_ITEMS - MapItem.RAILWAY, removed.mapItems - MapItem.RAILWAY)
        val savedNames = SettingsCodec.encode(removed).getValue("mapItems").split(',').map { it.substringBefore(':') }
        assertTrue(listOf("PORT", "STATION", "DAM_POINT", "CITY", "BUILT_UP_AREA", "DAM_LINE").none { it in savedNames })
        assertEquals(4, bad.mapLineWidth)
        assertEquals(4, bad.mapLineBrightness)
        assertEquals(4, SettingsCodec.decode(mapOf("mapLineWidthLevel" to "0")).mapLineWidth)
        assertEquals(6, SettingsCodec.decode(mapOf("mapLineWidthLevel" to "6")).mapLineWidth)
        assertEquals(1, SettingsCodec.decode(mapOf("mapLineBrightnessLevel" to "1")).mapLineBrightness)
        // 3 段のころ（20261018-D06 まで）の保存（mapLineWidth・mapLineBrightness）は読まない: 段 1（当時の初期値）が、今のいちばん細い・暗い段にならない
        val three = SettingsCodec.decode(mapOf("mapLineWidth" to "1", "mapLineBrightness" to "3"))
        assertEquals(4, three.mapLineWidth)
        assertEquals(4, three.mapLineBrightness)
    }

    @Test
    fun resetSettingsResetsTheMapSettings() {
        val changed = NavSettings(baseMap = false, mapItems = MapItem.entries.associateWith { MapRange.UPTO_10KM }, mapLineWidth = 1, mapLineBrightness = 6)
        val reset = changed.resetKeepingCustomReach()
        assertTrue(reset.baseMap)
        assertEquals(NavSettings.DEFAULT_MAP_ITEMS, reset.mapItems)
        assertEquals(4, reset.mapLineWidth)
        assertEquals(4, reset.mapLineBrightness)
    }
}
