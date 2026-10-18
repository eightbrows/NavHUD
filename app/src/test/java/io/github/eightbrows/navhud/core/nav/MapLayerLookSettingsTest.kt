package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.io.MapLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 20261018-D01: 地図レイヤの初期値（5 つ）、レイヤごとの明るさ（1〜5）、道路の「県道も出す」の設定（保存・初期値・初期値に戻す）。
 */
class MapLayerLookSettingsTest {

    @Test
    fun defaultLayersAreTheFiveAndSavedLayersAreKept() {
        // 初期 ON: 海岸線・県境・市区町村境・湖沼など（輪郭）・フェリーの航路。ほかは OFF
        assertEquals(
            setOf(MapLayer.COASTLINE, MapLayer.PREF_BOUNDARY, MapLayer.MUNI_BOUNDARY, MapLayer.INLAND_WATER, MapLayer.FERRY),
            NavSettings.DEFAULT_MAP_LAYERS,
        )
        assertEquals(NavSettings.DEFAULT_MAP_LAYERS, NavSettings().mapLayers)
        // 保存がない人は新しい初期値
        assertEquals(NavSettings.DEFAULT_MAP_LAYERS, SettingsCodec.decode(emptyMap()).mapLayers)
        // 保存がある人（D02 の初期値 = 海岸線と県境のまま保存した人も）はそのまま
        assertEquals(setOf(MapLayer.COASTLINE, MapLayer.PREF_BOUNDARY), SettingsCodec.decode(mapOf("mapLayers" to "COASTLINE,PREF_BOUNDARY")).mapLayers)
        // 初期値に戻すと新しい初期値
        assertEquals(NavSettings.DEFAULT_MAP_LAYERS, NavSettings(mapLayers = setOf(MapLayer.ROAD)).resetKeepingCustomReach().mapLayers)
    }

    @Test
    fun brightnessIsOneToFivePerLayerAndThreeByDefault() {
        val d = NavSettings()
        assertTrue(MapLayer.entries.all { d.mapBrightnessOf(it) == 3 })
        assertEquals(1..5, NavSettings.MAP_BRIGHTNESS_RANGE)
        // 変える: 範囲の外は端に寄せる。3 に戻すと表から外す（全部 3 の設定は初期値と同じ）
        val s = d.withMapBrightness(MapLayer.ROAD, 5).withMapBrightness(MapLayer.CITY, 0).withMapBrightness(MapLayer.COASTLINE, 2)
        assertEquals(5, s.mapBrightnessOf(MapLayer.ROAD))
        assertEquals(1, s.mapBrightnessOf(MapLayer.CITY))
        assertEquals(2, s.mapBrightnessOf(MapLayer.COASTLINE))
        assertEquals(3, s.mapBrightnessOf(MapLayer.RIVER))
        assertEquals(5, s.withMapBrightness(MapLayer.ROAD, 9).mapBrightnessOf(MapLayer.ROAD))
        assertEquals(d, s.withMapBrightness(MapLayer.ROAD, 3).withMapBrightness(MapLayer.CITY, 3).withMapBrightness(MapLayer.COASTLINE, 3))
    }

    @Test
    fun brightnessAndPrefRoadsAreSavedAndReadBack() {
        val s = NavSettings(mapPrefRoads = true).withMapBrightness(MapLayer.ROAD, 5).withMapBrightness(MapLayer.AIRPORT, 1)
        val enc = SettingsCodec.encode(s)
        // 3 でないレイヤだけ「名前:段」（並びは MapLayer の順）
        assertEquals("ROAD:5,AIRPORT:1", enc["mapBrightness"])
        assertEquals("true", enc["mapPrefRoads"])
        val back = SettingsCodec.decode(enc)
        assertEquals(s.mapBrightness, back.mapBrightness)
        assertTrue(back.mapPrefRoads)
        // 全部 3 なら空の文字。保存がない（前の版）なら全部 3・県道も出すは OFF
        assertEquals("", SettingsCodec.encode(NavSettings())["mapBrightness"])
        val old = SettingsCodec.decode(emptyMap())
        assertTrue(old.mapBrightness.isEmpty())
        assertFalse(old.mapPrefRoads)
        // 読めない名前・段（1〜5 の外・数でない）は飛ばす。3 は表に入れない
        val bad = SettingsCodec.decode(mapOf("mapBrightness" to "ROAD:4,FOO:2,CITY:9,RIVER:x,COASTLINE:3,PORT"))
        assertEquals(mapOf(MapLayer.ROAD to 4), bad.mapBrightness)
    }

    @Test
    fun resetSettingsResetsBrightnessAndPrefRoads() {
        val changed = NavSettings(baseMap = false, mapPrefRoads = true).withMapBrightness(MapLayer.ROAD, 5).withMapBrightness(MapLayer.FERRY, 1)
        val reset = changed.resetKeepingCustomReach()
        assertTrue(reset.baseMap)
        assertTrue(MapLayer.entries.all { reset.mapBrightnessOf(it) == 3 })
        assertFalse(reset.mapPrefRoads)
    }
}
