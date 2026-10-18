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
        // 海岸線・県境・市区町村境・フェリー・河川・湖沼は「すべて」、ほかは OFF
        val all = setOf(MapItem.COASTLINE, MapItem.PREF_BOUNDARY, MapItem.MUNI_BOUNDARY, MapItem.FERRY, MapItem.WATER)
        for (item in MapItem.entries) assertEquals(item.name, if (item in all) MapRange.ALL else MapRange.OFF, d.mapRangeOf(item))
        // 線の太さ・明るさは 1（今の値）。選べるのは 1〜3
        assertEquals(1, d.mapLineWidth)
        assertEquals(1, d.mapLineBrightness)
        assertEquals(1..3, NavSettings.MAP_LINE_LEVELS)
        // 使うレイヤ（開くレイヤ）は、OFF でない項目のレイヤ。河川・湖沼は 2 つのレイヤ
        assertEquals(
            setOf(MapLayer.COASTLINE, MapLayer.PREF_BOUNDARY, MapLayer.MUNI_BOUNDARY, MapLayer.FERRY, MapLayer.RIVER, MapLayer.INLAND_WATER),
            d.mapLayersInUse(),
        )
        // 道路の 3 項目は、どれか 1 つでも OFF でなければ道路のレイヤを使う
        assertTrue(MapLayer.ROAD in d.withMapRange(MapItem.PREF_ROAD, MapRange.UPTO_10KM).mapLayersInUse())
        assertFalse(MapLayer.ROAD in d.mapLayersInUse())
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
        assertTrue(enc.getValue("mapItems").startsWith("COASTLINE:ALL,PREF_BOUNDARY:ALL,MUNI_BOUNDARY:ALL,MOTORWAY:ALL,OTHER_MOTORWAY:OFF,NATIONAL_ROAD:UPTO_50KM,PREF_ROAD:UPTO_10KM,"))
        assertEquals("3", enc["mapLineWidth"])
        val back = SettingsCodec.decode(enc)
        assertEquals(s.mapItems, back.mapItems)
        assertEquals(3, back.mapLineWidth)
        assertEquals(2, back.mapLineBrightness)
        // 保存がない・前の形（mapLayers など）だけの保存は初期値（前の形は引き継がない）
        val old = SettingsCodec.decode(mapOf("mapLayers" to "ROAD,CITY", "mapBrightness" to "ROAD:5", "mapPrefRoads" to "true"))
        assertEquals(NavSettings.DEFAULT_MAP_ITEMS, old.mapItems)
        assertEquals(NavSettings.DEFAULT_MAP_ITEMS, SettingsCodec.decode(emptyMap()).mapItems)
        // 読めない名前・縮尺は飛ばす（その項目は初期値）。保存にない項目も初期値
        val bad = SettingsCodec.decode(mapOf("mapItems" to "MOTORWAY:UPTO_25KM,FOO:ALL,COASTLINE:BAR,CITY", "mapLineWidth" to "4", "mapLineBrightness" to "x"))
        assertEquals(MapRange.UPTO_25KM, bad.mapRangeOf(MapItem.MOTORWAY))
        assertEquals(MapRange.ALL, bad.mapRangeOf(MapItem.COASTLINE))
        assertEquals(MapRange.OFF, bad.mapRangeOf(MapItem.CITY))
        assertEquals(1, bad.mapLineWidth)
        assertEquals(1, bad.mapLineBrightness)
    }

    @Test
    fun resetSettingsResetsTheMapSettings() {
        val changed = NavSettings(baseMap = false, mapItems = MapItem.entries.associateWith { MapRange.UPTO_10KM }, mapLineWidth = 3, mapLineBrightness = 3)
        val reset = changed.resetKeepingCustomReach()
        assertTrue(reset.baseMap)
        assertEquals(NavSettings.DEFAULT_MAP_ITEMS, reset.mapItems)
        assertEquals(1, reset.mapLineWidth)
        assertEquals(1, reset.mapLineBrightness)
    }
}
