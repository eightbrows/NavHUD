package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.io.AirportKind
import io.github.eightbrows.navhud.core.io.BaseMap
import io.github.eightbrows.navhud.core.io.BaseMapFile
import io.github.eightbrows.navhud.core.io.MapLayer
import io.github.eightbrows.navhud.core.io.MapLevel
import io.github.eightbrows.navhud.core.io.MapLine
import io.github.eightbrows.navhud.core.io.RunwayKind
import io.github.eightbrows.navhud.core.nav.LandmarkItem
import io.github.eightbrows.navhud.core.nav.MapRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 滑走路と延長線（20261024-D04。D05 で延長線を 40dp にし、項目を分けた）: ファイルの滑走路のレイヤ、種類の読み方、描く線（太さ・延長線・項目）。 */
class RunwayTest {

    private val rect = HudRect(0f, 0f, 720f, 1200f)

    /** 自機（基準の地点）を画面の (360, 600) に置く */
    private fun proj(pxPerM: Double) = HudProjection(P(360f, 600f), pxPerM, 0.0)

    private val allMarks = LandmarkItem.entries.associateWith { MapRange.UPTO_100KM }

    /** 東西に 3,000 m の滑走路（西端が東 −1,500 m、東端が +1,500 m） */
    private fun runway(airportKind: Int, widthM: Int) = MapLine.of(
        MapLayer.RUNWAY, doubleArrayOf(TestGeo.LAT0, TestGeo.LAT0), doubleArrayOf(TestGeo.lon(-1500.0), TestGeo.lon(1500.0)),
        RunwayKind.of(airportKind, widthM), unit = 10_000_000,
    )

    private fun build(map: BaseMap, pxPerM: Double, landmarks: Map<LandmarkItem, MapRange> = allMarks, brightness: Int = 4, m: HudMetrics = HudMetrics()) =
        BaseMapBuilder.build(map, emptyMap(), landmarks, TestGeo.LAT0, TestGeo.LON0, proj(pxPerM), rect, 500.0, landmarkBrightnessLevel = brightness, m = m, marginPx = 0f)

    @Test
    fun kindHoldsTheAirportKindAndWidth() {
        val k = RunwayKind.of(AirportKind.LARGE or AirportKind.OURAIRPORTS, 46)
        assertEquals(AirportKind.LARGE or AirportKind.OURAIRPORTS, RunwayKind.airportKind(k))
        // 幅は 5 m 単位（46 m → 45 m）。分からなければ 0
        assertEquals(45, RunwayKind.widthM(k))
        assertEquals(0, RunwayKind.widthM(RunwayKind.of(0, 0)))
        assertEquals(315, RunwayKind.widthM(RunwayKind.of(0, 999)))
    }

    @Test
    fun runwayIsABarWithDashedExtensionsAtBothEnds() {
        val map = BaseMap(listOf(MapLevel(0.0, listOf(runway(AirportKind.LARGE, 60)))))
        // 1 px = 10 m: 滑走路は 300 px。幅 60 m は 6 px だが、下限の 1.5dp（密度 1 なので 1.5 px）より太いので 6
        val d = build(map, 0.1)
        assertEquals(3, d.runways.size)
        val (ext1, ext2, bar) = d.runways
        assertEquals(Ink.MAP_RUNWAY, bar.ink)
        assertEquals(LineDash.SOLID, bar.dash)
        assertEquals(210f, bar.points.first().x, 0.5f)
        assertEquals(510f, bar.points.last().x, 0.5f)
        assertEquals(6f, bar.widthDp, 0.01f)
        // 延長線: 両端から外へ、画面上で 40dp・太さ 1.5dp の破線（20261024-D05。前は 20dp・1dp。延長線が先、滑走路があと）
        assertEquals(40f, Tuning.BASEMAP_RUNWAY_EXT_DP)
        assertEquals(1.5f, Tuning.BASEMAP_RUNWAY_EXT_LINE_DP)
        for (e in listOf(ext1, ext2)) {
            assertEquals(Ink.MAP_RUNWAY_EXT, e.ink)
            assertEquals(LineDash.SHORT_DASH, e.dash)
            assertEquals(Tuning.BASEMAP_RUNWAY_EXT_LINE_DP, e.widthDp, 0.001f)
            assertEquals(Tuning.BASEMAP_RUNWAY_EXT_DP, HudGeometry.dist(e.points.first(), e.points.last()), 0.01f)
        }
        assertEquals(210f, ext1.points.first().x, 0.5f)
        assertEquals(170f, ext1.points.last().x, 0.5f)
        assertEquals(510f, ext2.points.first().x, 0.5f)
        assertEquals(550f, ext2.points.last().x, 0.5f)
        // 広域（1 px = 1 km）: 太さは下限の 1.5dp。延長線は縮尺によらず 40dp
        val wide = build(map, 0.001)
        assertEquals(Tuning.BASEMAP_RUNWAY_MIN_DP, wide.runways.last().widthDp, 0.001f)
        assertEquals(Tuning.BASEMAP_RUNWAY_EXT_DP, HudGeometry.dist(wide.runways[0].points.first(), wide.runways[0].points.last()), 0.01f)
        // 画面密度 2: 延長線は 80 px、太さの下限は 1.5dp（太さは dp で持つ）
        val dense = build(map, 0.001, m = HudMetrics().scaled(2f))
        assertEquals(80f, HudGeometry.dist(dense.runways[0].points.first(), dense.runways[0].points.last()), 0.01f)
        assertEquals(Tuning.BASEMAP_RUNWAY_MIN_DP, dense.runways.last().widthDp, 0.001f)
        // 幅が分からない滑走路も下限の太さ
        assertEquals(Tuning.BASEMAP_RUNWAY_MIN_DP, build(BaseMap(listOf(MapLevel(0.0, listOf(runway(0, 0))))), 0.1).runways.last().widthDp, 0.001f)
    }

    @Test
    fun runwaysFollowTheRunwayItemAndTheAirportMarkItems() {
        val civil = runway(AirportKind.LARGE, 45)
        val mil = runway(AirportKind.OURAIRPORTS, 45)
        val map = BaseMap(listOf(MapLevel(0.0, listOf(civil, mil))))
        assertEquals(6, build(map, 0.1).runways.size)
        // 「滑走路」と「滑走路の延長線」は別の項目（20261024-D05）: 片方だけなら、そのほうだけ描く。両方 OFF なら描かない
        val extOnly = build(map, 0.1, allMarks + (LandmarkItem.RUNWAY to MapRange.OFF)).runways
        assertEquals(4, extOnly.size)
        assertTrue(extOnly.all { it.ink == Ink.MAP_RUNWAY_EXT })
        val barOnly = build(map, 0.1, allMarks + (LandmarkItem.RUNWAY_EXT to MapRange.OFF)).runways
        assertEquals(2, barOnly.size)
        assertTrue(barOnly.all { it.ink == Ink.MAP_RUNWAY })
        assertTrue(build(map, 0.1, allMarks + (LandmarkItem.RUNWAY to MapRange.OFF) + (LandmarkItem.RUNWAY_EXT to MapRange.OFF)).runways.isEmpty())
        // 延長線は項目の縮尺で: 〜10k なら R1 25 km では延長線を描かず、滑走路だけ
        val ext10 = allMarks + (LandmarkItem.RUNWAY_EXT to MapRange.UPTO_10KM)
        assertEquals(2, BaseMapBuilder.build(map, emptyMap(), ext10, TestGeo.LAT0, TestGeo.LON0, proj(0.1), rect, 25_000.0, marginPx = 0f).runways.size)
        assertEquals(6, BaseMapBuilder.build(map, emptyMap(), ext10, TestGeo.LAT0, TestGeo.LON0, proj(0.1), rect, 10_000.0, marginPx = 0f).runways.size)
        // 空港の記号の項目が OFF の空港（軍用（小））の滑走路は描かない
        val noMilSmall = build(map, 0.1, allMarks + (LandmarkItem.AIRPORT_MIL_SMALL to MapRange.OFF))
        assertEquals(3, noMilSmall.runways.size)
        // 延長線だけのときも同じ
        assertEquals(2, build(map, 0.1, allMarks + (LandmarkItem.AIRPORT_MIL_SMALL to MapRange.OFF) + (LandmarkItem.RUNWAY to MapRange.OFF)).runways.size)
        // 縮尺の項目: 〜5k なら R1 5 km まで描き、10 km では描かない（滑走路・延長線とも〜5k）
        val upTo5 = allMarks + (LandmarkItem.RUNWAY to MapRange.UPTO_5KM) + (LandmarkItem.RUNWAY_EXT to MapRange.UPTO_5KM)
        assertEquals(6, BaseMapBuilder.build(map, emptyMap(), upTo5, TestGeo.LAT0, TestGeo.LON0, proj(0.1), rect, 5_000.0, marginPx = 0f).runways.size)
        assertTrue(BaseMapBuilder.build(map, emptyMap(), upTo5, TestGeo.LAT0, TestGeo.LON0, proj(0.1), rect, 10_000.0, marginPx = 0f).runways.isEmpty())
        // ランドマークの明るさの段が効く（段 6）
        val k6 = Tuning.BASEMAP_BRIGHTNESS_FACTORS[5]
        assertTrue(build(map, 0.1, brightness = 6).runways.all { it.brightness == k6 })
    }

    @Test
    fun bundledAssetHasRunwaysWithBothEnds() {
        val asset = File("src/main/assets/basemap.bin")
        assertTrue("assets/basemap.bin がない", asset.isFile)
        val map = BaseMapFile.decode(asset.readBytes())
        // 地図に出す 120 の空港の、閉じていない滑走路 145 本のうち、両端の座標がある 132 本から、端が国土数値情報の敷地の面から
        // 50 m より外にある 4 本（岩国 02R/20L・北大東 03/21・隠岐 08/26・喜界 07/25）を除いた 128 本（20261024-D05。どの段にも同じだけ入れる）
        assertEquals(128, map.countOf(MapLayer.RUNWAY))
        // かたまりは読んでから使う（画面と同じ）
        map.preload(setOf(MapLayer.RUNWAY))
        for (level in map.levels) {
            val lines = level.candidates(MapLayer.RUNWAY, 20.0, 120.0, 50.0, 150.0)
            assertEquals(128, lines.size)
            for (l in lines) {
                assertEquals(2, l.size)
                // 日本の中（南鳥島の東経 154 度まで）、幅は 0〜100 m
                assertTrue(l.minLat > 20.0 && l.maxLat < 46.0 && l.minLon > 122.0 && l.maxLon < 154.5)
                assertTrue(RunwayKind.widthM(l.kind) in 0..100)
            }
            // 滑走路が 1 本だけで、その端が敷地から外れている空港（北大東・喜界・隠岐）には滑走路がない
            for ((lat, lon) in listOf(25.9447 to 131.3268, 28.3213 to 129.9281, 36.1781 to 133.3250)) {
                val near = level.candidates(MapLayer.RUNWAY, lat - 0.05, lon - 0.05, lat + 0.05, lon + 0.05)
                    .filter { it.maxLat > lat - 0.05 && it.minLat < lat + 0.05 && it.maxLon > lon - 0.05 && it.minLon < lon + 0.05 }
                assertTrue("$lat,$lon", near.isEmpty())
            }
        }
    }
}
