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
        // 線（20261025-D01 で実機の設定に合わせた）: 海岸線・県境・高速道路・新幹線・河川と湖沼・航路は「〜100k」、国道は「〜25k」、
        // 市町村界は「〜5k」、その他の自動車専用道路・県道・在来線は OFF（前は高速道路・国道が「〜100k」、ほかは OFF）
        val expected = mapOf(
            MapItem.COASTLINE to MapRange.UPTO_100KM, MapItem.PREF_BOUNDARY to MapRange.UPTO_100KM, MapItem.MUNI_BOUNDARY to MapRange.UPTO_5KM,
            MapItem.MOTORWAY to MapRange.UPTO_100KM, MapItem.OTHER_MOTORWAY to MapRange.OFF, MapItem.NATIONAL_ROAD to MapRange.UPTO_25KM,
            MapItem.PREF_ROAD to MapRange.OFF, MapItem.SHINKANSEN to MapRange.UPTO_100KM, MapItem.RAILWAY to MapRange.OFF,
            MapItem.WATER to MapRange.UPTO_100KM, MapItem.FERRY to MapRange.UPTO_100KM,
        )
        assertEquals(MapItem.entries.toSet(), expected.keys)
        for (item in MapItem.entries) assertEquals(item.name, expected.getValue(item), d.mapRangeOf(item))
        // 海岸線は初期値で出る（20261024-D09 の伊江島の調べ。変換の basemap.bin は変えない）
        assertEquals(MapRange.UPTO_100KM, d.mapRangeOf(MapItem.COASTLINE))
        // 空港・山頂は線の項目にない（20261021-D04 でランドマークに移した）。ランドマークは 11 項目（20261024-D01 で空港を民間・軍用に分けた。
        // 20261024-D04 で滑走路、20261024-D05 で滑走路の延長線を足した）: 空港の敷地は〜5k、ほかは〜100k、明るさの段は 3
        // （20261025-D01 で実機の設定に合わせた。前は空港の敷地〜10k・空港の略号〜25k・明るさの段 4）
        assertTrue(MapItem.entries.none { it.name == "AIRPORT" || it.name == "PEAK" })
        assertEquals(
            listOf(
                "AIRPORT_CIVIL_LARGE", "AIRPORT_CIVIL_SMALL", "AIRPORT_MIL_LARGE", "AIRPORT_MIL_SMALL", "AIRPORT_AREA", "AIRPORT_CODE",
                "RUNWAY", "RUNWAY_EXT", "PEAK_HIGH", "PEAK_MID", "PEAK_LOW",
            ),
            LandmarkItem.entries.map { it.name },
        )
        for (item in LandmarkItem.entries) {
            assertEquals(item.name, if (item == LandmarkItem.AIRPORT_AREA) MapRange.UPTO_5KM else MapRange.UPTO_100KM, d.landmarkRangeOf(item))
        }
        assertEquals(3, d.landmarkBrightness)
        // 線の太さは 2、明るさは 3（20261025-D01 から。前はどちらも掛ける数が 1 の段 4）。選べるのは 1〜6
        assertEquals(2, d.mapLineWidth)
        assertEquals(3, d.mapLineBrightness)
        assertEquals(4, NavSettings.MAP_LINE_DEFAULT_LEVEL)
        assertEquals(1..6, NavSettings.MAP_LINE_LEVELS)
        // 使うレイヤ（開くレイヤ）は、OFF でない項目のレイヤ。初期値では在来線が OFF でも新幹線が〜100k なので鉄道のレイヤを使う
        assertEquals(
            setOf(
                MapLayer.COASTLINE, MapLayer.PREF_BOUNDARY, MapLayer.MUNI_BOUNDARY, MapLayer.ROAD, MapLayer.RAILWAY, MapLayer.RIVER, MapLayer.INLAND_WATER,
                MapLayer.FERRY, MapLayer.AIRPORT_AREA, MapLayer.RUNWAY,
            ),
            d.mapLayersInUse(),
        )
        // 以下は使うレイヤの決まりを、20261024-D09 までの初期値（高速道路・国道だけ）で確かめる
        val old = io.github.eightbrows.navhud.core.TestSettings.BEFORE_1025_D01
        assertEquals(setOf(MapLayer.ROAD, MapLayer.AIRPORT_AREA, MapLayer.RUNWAY), old.mapLayersInUse())
        // 空港の敷地・滑走路（線のレイヤ）は、ランドマークのその項目が OFF なら読まない（20261022-D01・20261024-D04）。
        // 滑走路のレイヤは、滑走路と滑走路の延長線の両方が OFF のときだけ読まない（20261024-D05）
        assertEquals(setOf(MapLayer.ROAD, MapLayer.RUNWAY), old.withLandmarkRange(LandmarkItem.AIRPORT_AREA, MapRange.OFF).mapLayersInUse())
        assertEquals(setOf(MapLayer.ROAD, MapLayer.AIRPORT_AREA, MapLayer.RUNWAY), old.withLandmarkRange(LandmarkItem.RUNWAY, MapRange.OFF).mapLayersInUse())
        assertEquals(setOf(MapLayer.ROAD, MapLayer.AIRPORT_AREA, MapLayer.RUNWAY), old.withLandmarkRange(LandmarkItem.RUNWAY_EXT, MapRange.OFF).mapLayersInUse())
        assertEquals(
            setOf(MapLayer.ROAD, MapLayer.AIRPORT_AREA),
            old.withLandmarkRange(LandmarkItem.RUNWAY, MapRange.OFF).withLandmarkRange(LandmarkItem.RUNWAY_EXT, MapRange.OFF).mapLayersInUse(),
        )
        // 河川・湖沼は 2 つのレイヤ
        assertEquals(setOf(MapLayer.ROAD, MapLayer.AIRPORT_AREA, MapLayer.RUNWAY, MapLayer.RIVER, MapLayer.INLAND_WATER), old.withMapRange(MapItem.WATER, MapRange.UPTO_100KM).mapLayersInUse())
        // 道路の項目は、どれか 1 つでも OFF でなければ道路のレイヤを使う
        val noRoads = old.withMapRange(MapItem.MOTORWAY, MapRange.OFF).withMapRange(MapItem.NATIONAL_ROAD, MapRange.OFF)
        assertFalse(MapLayer.ROAD in noRoads.mapLayersInUse())
        assertTrue(MapLayer.ROAD in noRoads.withMapRange(MapItem.PREF_ROAD, MapRange.UPTO_10KM).mapLayersInUse())
    }

    @Test
    fun rangeShowsWhileR1IsAtOrBelowTheValue() {
        assertFalse(MapRange.OFF.shows(25.0))
        // 〜5k（20261024-D04 で足した）。「すべて」（ALL）は 20261024-D05 で〜100k（UPTO_100KM）に置き換えた
        assertEquals(listOf("OFF", "UPTO_5KM", "UPTO_10KM", "UPTO_25KM", "UPTO_50KM", "UPTO_100KM"), MapRange.entries.map { it.name })
        assertTrue(MapRange.UPTO_5KM.shows(5_000.0))
        assertFalse(MapRange.UPTO_5KM.shows(10_000.0))
        assertTrue(MapRange.UPTO_10KM.shows(10_000.0))
        assertFalse(MapRange.UPTO_10KM.shows(25_000.0))
        assertTrue(MapRange.UPTO_25KM.shows(25_000.0))
        assertFalse(MapRange.UPTO_25KM.shows(50_000.0))
        assertTrue(MapRange.UPTO_50KM.shows(50_000.0))
        assertFalse(MapRange.UPTO_50KM.shows(100_000.0))
        assertTrue(MapRange.UPTO_100KM.shows(100_000.0))
        // 〜100k は、いちばん広域の段（R1 100 km）でも描く（前の「すべて」と同じ）
        val widestR1M = RangeAuto.ALL_STEPS_KM.max() * 1000 / 2
        assertEquals(100_000.0, widestR1M, 1e-9)
        assertTrue(MapRange.UPTO_100KM.shows(widestR1M))
    }

    @Test
    fun oldAllIsReadAsUpTo100k() {
        // 20261024-D05 より前の保存の「ALL」は〜100k として読む（線・ランドマークとも）。保存し直すと UPTO_100KM になる
        assertEquals(MapRange.UPTO_100KM, MapRange.fromSavedName("ALL"))
        assertEquals(MapRange.UPTO_25KM, MapRange.fromSavedName("UPTO_25KM"))
        assertEquals(null, MapRange.fromSavedName("FOO"))
        val old = SettingsCodec.decode(mapOf("mapItems" to "COASTLINE:ALL,MOTORWAY:OFF", "landmarkItems" to "PEAK_LOW:ALL,AIRPORT_CODE:UPTO_10KM,RUNWAY:ALL"))
        assertEquals(MapRange.UPTO_100KM, old.mapRangeOf(MapItem.COASTLINE))
        assertEquals(MapRange.OFF, old.mapRangeOf(MapItem.MOTORWAY))
        assertEquals(MapRange.UPTO_100KM, old.landmarkRangeOf(LandmarkItem.PEAK_LOW))
        assertEquals(MapRange.UPTO_10KM, old.landmarkRangeOf(LandmarkItem.AIRPORT_CODE))
        assertEquals(MapRange.UPTO_100KM, old.landmarkRangeOf(LandmarkItem.RUNWAY))
        // 滑走路の延長線は前の保存にないので初期値（〜100k）
        assertEquals(MapRange.UPTO_100KM, old.landmarkRangeOf(LandmarkItem.RUNWAY_EXT))
        val enc = SettingsCodec.encode(old)
        assertTrue(enc.getValue("mapItems").startsWith("COASTLINE:UPTO_100KM,"))
        assertTrue(enc.getValue("landmarkItems").contains("RUNWAY:UPTO_100KM,RUNWAY_EXT:UPTO_100KM,"))
        assertTrue(enc.values.none { it.contains(":ALL") })
    }

    @Test
    fun itemsAndLineLevelsAreSavedAndReadBack() {
        val s = NavSettings(mapLineWidth = 3, mapLineBrightness = 2)
            .withMapRange(MapItem.MOTORWAY, MapRange.UPTO_100KM).withMapRange(MapItem.NATIONAL_ROAD, MapRange.UPTO_50KM)
            .withMapRange(MapItem.PREF_ROAD, MapRange.UPTO_10KM).withMapRange(MapItem.WATER, MapRange.UPTO_25KM)
        val enc = SettingsCodec.encode(s)
        // 全部の項目を「名前:縮尺」で（MapItem の順）
        assertTrue(enc.getValue("mapItems").startsWith("COASTLINE:UPTO_100KM,PREF_BOUNDARY:UPTO_100KM,MUNI_BOUNDARY:UPTO_5KM,MOTORWAY:UPTO_100KM,OTHER_MOTORWAY:OFF,NATIONAL_ROAD:UPTO_50KM,PREF_ROAD:UPTO_10KM,"))
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
        // 前の保存の線の項目の中の空港・山頂（AIRPORT・PEAK）は読み飛ばし、ランドマークは初期値で始める（20261021-D04。引き継がない）
        val oldPoints = SettingsCodec.decode(mapOf("mapItems" to "MOTORWAY:OFF,PEAK:OFF,AIRPORT:UPTO_10KM"))
        assertEquals(MapRange.OFF, oldPoints.mapRangeOf(MapItem.MOTORWAY))
        assertEquals(NavSettings.DEFAULT_LANDMARKS, oldPoints.landmarks)
        assertTrue(SettingsCodec.encode(oldPoints).getValue("mapItems").split(',').none { it.startsWith("AIRPORT:") || it.startsWith("PEAK:") })
        // ランドマークは「landmarkItems」に「名前:縮尺」で保存し、読み戻せる（20261022-D01）。読めない名前・縮尺は飛ばす（その項目は初期値）
        val lm = NavSettings(landmarkBrightness = 6).withLandmarkRange(LandmarkItem.PEAK_LOW, MapRange.OFF).withLandmarkRange(LandmarkItem.AIRPORT_CODE, MapRange.UPTO_100KM)
        val lmEnc = SettingsCodec.encode(lm)
        assertEquals(
            "AIRPORT_CIVIL_LARGE:UPTO_100KM,AIRPORT_CIVIL_SMALL:UPTO_100KM,AIRPORT_MIL_LARGE:UPTO_100KM,AIRPORT_MIL_SMALL:UPTO_100KM," +
                "AIRPORT_AREA:UPTO_5KM,AIRPORT_CODE:UPTO_100KM,RUNWAY:UPTO_100KM,RUNWAY_EXT:UPTO_100KM,PEAK_HIGH:UPTO_100KM,PEAK_MID:UPTO_100KM,PEAK_LOW:OFF",
            lmEnc["landmarkItems"],
        )
        assertEquals("6", lmEnc["landmarkBrightnessLevel"])
        assertEquals(lm.landmarks, SettingsCodec.decode(lmEnc).landmarks)
        assertEquals(6, SettingsCodec.decode(lmEnc).landmarkBrightness)
        val badLm = SettingsCodec.decode(mapOf("landmarkItems" to "AIRPORT_MIL_SMALL:OFF,PEAK:OFF,PEAK_HIGH:FOO,CITY", "landmarkBrightnessLevel" to "9"))
        assertEquals(MapRange.OFF, badLm.landmarkRangeOf(LandmarkItem.AIRPORT_MIL_SMALL))
        // 前の版（20261024-D01 より前）の空港の項目（AIRPORT_LARGE・AIRPORT_SMALL）は引き継がない: 空港の 4 項目は初期値（〜100k）
        val oldAirports = SettingsCodec.decode(mapOf("landmarkItems" to "AIRPORT_LARGE:OFF,AIRPORT_SMALL:OFF,AIRPORT_CODE:OFF"))
        for (item in LandmarkItem.AIRPORT_MARKS) assertEquals(item.name, MapRange.UPTO_100KM, oldAirports.landmarkRangeOf(item))
        assertEquals(MapRange.OFF, oldAirports.landmarkRangeOf(LandmarkItem.AIRPORT_CODE))
        assertEquals(MapRange.UPTO_100KM, badLm.landmarkRangeOf(LandmarkItem.PEAK_HIGH))
        assertEquals(NavSettings.DEFAULT_LANDMARK_BRIGHTNESS, badLm.landmarkBrightness)
        // 前の版（20261021-D04）の「landmarks」は読まない（PEAK_HIGH・PEAK_LOW は名前が同じでも中身が違うので、引き継がない）
        val oldLm = SettingsCodec.decode(mapOf("landmarks" to "AIRPORT:OFF,PEAK_HIGH:OFF,PEAK_LOW:OFF,PEAK_OTHER:OFF"))
        assertEquals(NavSettings.DEFAULT_LANDMARKS, oldLm.landmarks)
        assertTrue("landmarks" !in SettingsCodec.encode(oldLm))
        // なくした項目（港・駅・ダムと堰の点・都市・市街地は 20261020-D01、ダム・堰の線は 20261020-D02）の保存は、読めない名前として飛ばす
        val removed = SettingsCodec.decode(mapOf("mapItems" to "PORT:ALL,STATION:ALL,DAM_POINT:ALL,CITY:ALL,BUILT_UP_AREA:ALL,DAM_LINE:ALL,RAILWAY:UPTO_50KM"))
        assertEquals(MapRange.UPTO_50KM, removed.mapRangeOf(MapItem.RAILWAY))
        assertEquals(NavSettings.DEFAULT_MAP_ITEMS - MapItem.RAILWAY, removed.mapItems - MapItem.RAILWAY)
        val savedNames = SettingsCodec.encode(removed).getValue("mapItems").split(',').map { it.substringBefore(':') }
        assertTrue(listOf("PORT", "STATION", "DAM_POINT", "CITY", "BUILT_UP_AREA", "DAM_LINE").none { it in savedNames })
        assertEquals(NavSettings.DEFAULT_MAP_LINE_WIDTH, bad.mapLineWidth)
        assertEquals(NavSettings.DEFAULT_MAP_LINE_BRIGHTNESS, bad.mapLineBrightness)
        assertEquals(NavSettings.DEFAULT_MAP_LINE_WIDTH, SettingsCodec.decode(mapOf("mapLineWidthLevel" to "0")).mapLineWidth)
        assertEquals(6, SettingsCodec.decode(mapOf("mapLineWidthLevel" to "6")).mapLineWidth)
        assertEquals(1, SettingsCodec.decode(mapOf("mapLineBrightnessLevel" to "1")).mapLineBrightness)
        // 3 段のころ（20261018-D06 まで）の保存（mapLineWidth・mapLineBrightness）は読まない: 段 1（当時の初期値）が、今のいちばん細い・暗い段にならない
        val three = SettingsCodec.decode(mapOf("mapLineWidth" to "1", "mapLineBrightness" to "3"))
        assertEquals(NavSettings.DEFAULT_MAP_LINE_WIDTH, three.mapLineWidth)
        assertEquals(NavSettings.DEFAULT_MAP_LINE_BRIGHTNESS, three.mapLineBrightness)
    }

    @Test
    fun resetSettingsResetsTheMapSettings() {
        val changed = NavSettings(
            baseMap = false, mapItems = MapItem.entries.associateWith { MapRange.UPTO_10KM }, mapLineWidth = 1, mapLineBrightness = 6,
            landmarks = LandmarkItem.entries.associateWith { MapRange.OFF }, landmarkBrightness = 1,
        )
        val reset = changed.resetKeepingCustomReach()
        assertTrue(reset.baseMap)
        assertEquals(NavSettings.DEFAULT_MAP_ITEMS, reset.mapItems)
        assertEquals(NavSettings.DEFAULT_LANDMARKS, reset.landmarks)
        assertEquals(NavSettings.DEFAULT_LANDMARK_BRIGHTNESS, reset.landmarkBrightness)
        assertEquals(NavSettings.DEFAULT_MAP_LINE_WIDTH, reset.mapLineWidth)
        assertEquals(NavSettings.DEFAULT_MAP_LINE_BRIGHTNESS, reset.mapLineBrightness)
    }
}
