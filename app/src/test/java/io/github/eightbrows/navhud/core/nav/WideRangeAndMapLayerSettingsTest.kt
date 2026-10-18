package io.github.eightbrows.navhud.core.nav

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/**
 * 20261017-D02: 縮尺の段 R1 50 km・100 km（ピンチ・＋/−・保存）。
 * 背景地図のレイヤの設定のテストは、20261018-D04 で設定の形を作り直したので MapItemSettingsTest に移した。
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
}
