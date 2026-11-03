package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.Heading
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.nav.SettingsCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 距離環の明るさ（20261103-D01・D02）: 1〜6 段。掛ける数は 0.2〜1.0（背景地図の線の明るさとは別）。距離環の線だけに掛ける。 */
class RingBrightnessTest {

    private val rect = HudRect(0f, 0f, 720f, 900f)
    private val m = HudMetrics()

    private fun state(level: Int, mode: DisplayMode = DisplayMode.ARC) = NavState(
        nowMs = 0,
        fix = Fix(timeMs = 0, lat = TestGeo.LAT0, lon = TestGeo.LON0),
        noFix = false,
        heading = Heading(0f, HeadingSrc.GPS),
        settings = NavSettings(displayMode = mode, ringBrightness = level),
        rangeM = 2_000.0,
    )

    @Test
    fun defaultAndSaving() {
        // 初期値は 5（0.8 倍。D01 の初期値と同じ明るさ）。保存の名前は ringLineBrightnessLevel（D02 から）
        assertEquals(5, NavSettings().ringBrightness)
        assertEquals(0.8f, Tuning.RING_BRIGHTNESS_FACTORS[NavSettings().ringBrightness - 1], 0f)
        assertEquals("5", SettingsCodec.encode(NavSettings())["ringLineBrightnessLevel"])
        assertEquals(null, SettingsCodec.encode(NavSettings())["ringBrightnessLevel"])
        for (v in 1..6) assertEquals(v, SettingsCodec.decode(SettingsCodec.encode(NavSettings(ringBrightness = v))).ringBrightness)
        // 前の保存がない端末（キーがない）・範囲外・数でない値は初期値の 5
        assertEquals(5, SettingsCodec.decode(mapOf("travelMode" to "CAR")).ringBrightness)
        for (bad in listOf("0", "7", "-1", "x", "")) assertEquals(bad, 5, SettingsCodec.decode(mapOf("ringLineBrightnessLevel" to bad)).ringBrightness)
    }

    @Test
    fun d01LevelsAreReadAsTheSameBrightness() {
        // D01 の段（ringBrightnessLevel。0.4 / 0.6 / 0.8 / 1.0 / 1.3 / 1.6）→ 同じ明るさの今の段（1→3、2→4、3→5、4→6、5・6→6）
        val d01 = listOf(0.4f, 0.6f, 0.8f, 1f, 1.3f, 1.6f)
        val expected = mapOf(1 to 3, 2 to 4, 3 to 5, 4 to 6, 5 to 6, 6 to 6)
        for ((old, now) in expected) {
            val s = SettingsCodec.decode(mapOf("ringBrightnessLevel" to old.toString()))
            assertEquals("D01 $old", now, s.ringBrightness)
            // 1.0 までは同じ明るさ。1.3・1.6 はいちばん明るい 1.0
            assertEquals(minOf(d01[old - 1], 1f), Tuning.RING_BRIGHTNESS_FACTORS[now - 1], 1e-6f)
        }
        // D01 の段が範囲外・読めない値なら初期値の 5
        for (bad in listOf("0", "7", "x")) assertEquals(bad, 5, SettingsCodec.decode(mapOf("ringBrightnessLevel" to bad)).ringBrightness)
        // 今の名前の保存があれば、そちらを使う
        assertEquals(2, SettingsCodec.decode(mapOf("ringBrightnessLevel" to "1", "ringLineBrightnessLevel" to "2")).ringBrightness)
    }

    @Test
    fun onlyTheRingLinesChange() {
        val factors = listOf(0.2f, 0.3f, 0.4f, 0.6f, 0.8f, 1f)
        assertEquals(factors, Tuning.RING_BRIGHTNESS_FACTORS)
        // 背景地図の線の明るさの掛ける数は変わらない（別の値）
        assertEquals(listOf(0.4f, 0.6f, 0.8f, 1f, 1.3f, 1.6f), Tuning.BASEMAP_BRIGHTNESS_FACTORS)
        val base = HudSceneBuilder.build(state(5), rect, m)
        for (mode in DisplayMode.entries) for (level in 1..6) {
            val s = HudSceneBuilder.build(state(level, mode), rect, m)
            // 距離環の線（arcs）は全部、その段の掛ける数
            assertTrue(s.arcs.isNotEmpty())
            assertTrue("$mode $level", s.arcs.all { it.brightness == factors[level - 1] })
            if (mode == DisplayMode.ARC) {
                // 距離の数字・方位の目盛りと文字（labels・segments）は段で変わらない
                assertEquals(base.labels, s.labels)
                assertEquals(base.segments, s.segments)
                assertEquals(base.arcs.map { it.copy(brightness = 1f) }, s.arcs.map { it.copy(brightness = 1f) })
            }
        }
    }
}
