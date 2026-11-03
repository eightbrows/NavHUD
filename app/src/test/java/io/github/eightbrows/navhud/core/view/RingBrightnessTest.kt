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

/** 距離環の明るさ（20261103-D01）: 1〜6 段。掛ける数は背景地図の線の明るさと同じ。距離環の線だけに掛ける。 */
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
        // 初期値は 3（0.8 倍）。保存の名前は ringBrightnessLevel
        assertEquals(3, NavSettings().ringBrightness)
        assertEquals(0.8f, Tuning.BASEMAP_BRIGHTNESS_FACTORS[NavSettings().ringBrightness - 1], 0f)
        assertEquals("3", SettingsCodec.encode(NavSettings())["ringBrightnessLevel"])
        for (v in 1..6) assertEquals(v, SettingsCodec.decode(SettingsCodec.encode(NavSettings(ringBrightness = v))).ringBrightness)
        // 前の保存がない端末（キーがない）・範囲外・数でない値は初期値の 3
        assertEquals(3, SettingsCodec.decode(mapOf("travelMode" to "CAR")).ringBrightness)
        for (bad in listOf("0", "7", "-1", "x", "")) assertEquals(bad, 3, SettingsCodec.decode(mapOf("ringBrightnessLevel" to bad)).ringBrightness)
    }

    @Test
    fun onlyTheRingLinesChange() {
        val factors = listOf(0.4f, 0.6f, 0.8f, 1f, 1.3f, 1.6f)
        assertEquals(factors, Tuning.BASEMAP_BRIGHTNESS_FACTORS)
        val base = HudSceneBuilder.build(state(3), rect, m)
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
