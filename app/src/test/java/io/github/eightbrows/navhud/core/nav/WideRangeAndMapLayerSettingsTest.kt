package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.io.MapLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * 20261017-D02: 縮尺の段 R1 50 km・100 km（ピンチ・＋/−・保存）と、背景地図のレイヤの設定（保存・初期値・初期値に戻す）。
 */
class WideRangeAndMapLayerSettingsTest {

    private val all = RangeAuto.ALL_STEPS_KM

    @Test
    fun pinchAndButtonsReachTheNewWideSteps() {
        // 使う段を全部（12 段）にして、R1 25 km（50 km の段）から
        val e = NavEngine(NavSettings(rangeStepsKm = all, initialRangeKm = 50.0, autoRange = false), ZoneId.of("Asia/Tokyo"), SourceKind.REPLAY)
        assertEquals(50_000.0, e.state.rangeM, 0.0)
        // ピンチで広域へ 2 段: R1 100 km（200 km の段）。その先は止まる
        assertEquals(200_000.0, e.zoomBy(-2).rangeM, 0.0)
        assertEquals(200_000.0, e.zoomOut().rangeM, 0.0)
        // ＋ で 1 段詳細へ: R1 50 km
        assertEquals(100_000.0, e.zoomIn().rangeM, 0.0)
        assertEquals(50_000.0, e.zoomBy(1).rangeM, 0.0)
        // 初期値の使う段（R1 25 km まで）では、新しい段へは行かない
        val d = NavEngine(NavSettings(initialRangeKm = 50.0, autoRange = false), ZoneId.of("Asia/Tokyo"), SourceKind.REPLAY)
        assertEquals(50_000.0, d.zoomBy(-3).rangeM, 0.0)
    }

    @Test
    fun savedStepsAndLimitsWithTheNewStepsAreReadBack() {
        val s = NavSettings(rangeStepsKm = all, initialRangeKm = 100.0, autoMaxRangeKm = 200.0)
        val back = SettingsCodec.decode(SettingsCodec.encode(s))
        assertEquals(all, back.rangeStepsKm)
        assertEquals(100.0, back.initialRangeKm, 0.0)
        assertEquals(200.0, back.autoMaxRangeKm, 0.0)
        // 前の版で保存した使う段（R1 25 km までの 10 段）は、そのまま読む（新しい段は入らない）
        val old = mapOf("rangeStepsKm" to "0.05,0.1,0.2,0.5,1.0,2.0,5.0,10.0,20.0,50.0")
        assertEquals(all.take(10), SettingsCodec.decode(old).rangeStepsKm)
        // 段の一覧にない値（例: 300 km）は捨てる
        assertEquals(listOf(50.0, 200.0), SettingsCodec.decode(mapOf("rangeStepsKm" to "50.0,200.0,300.0")).rangeStepsKm)
    }

    @Test
    fun mapLayersAreSavedAndReadBack() {
        // 初期値: 背景地図 ON、レイヤは DEFAULT_MAP_LAYERS（20261018-D01 で 5 つに変えた。MapLayerLookSettingsTest）
        val d = NavSettings()
        assertTrue(d.baseMap)
        assertEquals(NavSettings.DEFAULT_MAP_LAYERS, d.mapLayers)
        // 往復
        val s = NavSettings(mapLayers = setOf(MapLayer.ROAD, MapLayer.RAILWAY, MapLayer.CITY))
        assertEquals(s.mapLayers, SettingsCodec.decode(SettingsCodec.encode(s)).mapLayers)
        assertEquals("ROAD,RAILWAY,CITY", SettingsCodec.encode(s)["mapLayers"])
        // 保存がない（前の版）なら初期値。空なら全部 OFF。知らない名前は飛ばす
        assertEquals(d.mapLayers, SettingsCodec.decode(emptyMap()).mapLayers)
        assertEquals(emptySet<MapLayer>(), SettingsCodec.decode(mapOf("mapLayers" to "")).mapLayers)
        assertEquals(setOf(MapLayer.ROAD, MapLayer.CITY), SettingsCodec.decode(mapOf("mapLayers" to "ROAD, FOO,CITY")).mapLayers)
        // D01 の「背景地図（海岸線・県境）」の保存（baseMap = false）は、まとめて消すスイッチに引き継ぐ。レイヤは初期値のまま
        val fromD01 = SettingsCodec.decode(mapOf("baseMap" to "false"))
        assertFalse(fromD01.baseMap)
        assertEquals(d.mapLayers, fromD01.mapLayers)
    }

    @Test
    fun resetSettingsAlsoResetsMapLayers() {
        val changed = NavSettings(baseMap = false, mapLayers = MapLayer.entries.toSet())
        val reset = changed.resetKeepingCustomReach()
        assertTrue(reset.baseMap)
        assertEquals(NavSettings.DEFAULT_MAP_LAYERS, reset.mapLayers)
    }
}
