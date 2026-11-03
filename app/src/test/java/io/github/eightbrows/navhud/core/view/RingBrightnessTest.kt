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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 距離環の明るさ（20261103-D01・D02 は段、20261104-D01 から %）: 20 / 40 / 60 / 80 / 100%。距離環の線だけに掛ける。
 * 前の段（D01・D02）は、同じ明るさの % にしてから一番近い値（真ん中なら大きい方）に読む。
 */
class RingBrightnessTest {

    private val rect = HudRect(0f, 0f, 720f, 900f)
    private val m = HudMetrics()

    private fun state(pct: Int, mode: DisplayMode = DisplayMode.ARC) = NavState(
        nowMs = 0,
        fix = Fix(timeMs = 0, lat = TestGeo.LAT0, lon = TestGeo.LON0),
        noFix = false,
        heading = Heading(0f, HeadingSrc.GPS),
        settings = NavSettings(displayMode = mode, ringBrightnessPct = pct),
        rangeM = 2_000.0,
    )

    private fun decode(vararg kv: Pair<String, String>) = SettingsCodec.decode(mapOf(*kv)).ringBrightnessPct

    @Test
    fun choicesDefaultAndSaving() {
        // 初期値は 80%（D02 の段 5・D01 の段 3 と同じ明るさ）。保存の名前は ringLineBrightnessPct（20261104-D01 から）
        assertEquals(listOf(20, 40, 60, 80, 100), NavSettings.RING_BRIGHTNESS_CHOICES_PCT)
        assertEquals(80, NavSettings().ringBrightnessPct)
        val enc = SettingsCodec.encode(NavSettings())
        assertEquals("80", enc["ringLineBrightnessPct"])
        assertNull(enc["ringLineBrightnessLevel"])
        assertNull(enc["ringBrightnessLevel"])
        for (v in NavSettings.RING_BRIGHTNESS_CHOICES_PCT) assertEquals(v, SettingsCodec.decode(SettingsCodec.encode(NavSettings(ringBrightnessPct = v))).ringBrightnessPct)
        // 選べる値にない % は一番近い値（真ん中なら大きい方）。20〜100 の外・数でない値は、前の段もなければ初期値
        assertEquals(80, decode("ringLineBrightnessPct" to "70"))
        assertEquals(40, decode("ringLineBrightnessPct" to "30"))
        assertEquals(20, decode("ringLineBrightnessPct" to "25"))
        for (bad in listOf("0", "110", "x", "")) assertEquals(bad, 80, decode("ringLineBrightnessPct" to bad))
        // 前の保存がない端末は初期値
        assertEquals(80, decode("travelMode" to "CAR"))
    }

    @Test
    fun earlierLevelsAreReadAsTheSameBrightness() {
        // D02（ringLineBrightnessLevel。0.2 / 0.3 / 0.4 / 0.6 / 0.8 / 1.0）: 30% はちょうど真ん中なので 40%
        val d02 = mapOf(1 to 20, 2 to 40, 3 to 40, 4 to 60, 5 to 80, 6 to 100)
        for ((level, pct) in d02) assertEquals("D02 $level", pct, decode("ringLineBrightnessLevel" to level.toString()))
        // D01（ringBrightnessLevel。0.4 / 0.6 / 0.8 / 1.0 / 1.3 / 1.6）: 130%・160% は一番近い 100%
        val d01 = mapOf(1 to 40, 2 to 60, 3 to 80, 4 to 100, 5 to 100, 6 to 100)
        for ((level, pct) in d01) assertEquals("D01 $level", pct, decode("ringBrightnessLevel" to level.toString()))
        // 範囲の外の段は初期値
        for (bad in listOf("0", "7", "x")) {
            assertEquals(80, decode("ringLineBrightnessLevel" to bad))
            assertEquals(80, decode("ringBrightnessLevel" to bad))
        }
        // 新しいものを先に使う: % → D02 の段 → D01 の段
        assertEquals(20, decode("ringLineBrightnessPct" to "20", "ringLineBrightnessLevel" to "6", "ringBrightnessLevel" to "6"))
        assertEquals(60, decode("ringLineBrightnessLevel" to "4", "ringBrightnessLevel" to "1"))
        assertEquals(Tuning.RING_BRIGHTNESS_D02_FACTORS, listOf(0.2f, 0.3f, 0.4f, 0.6f, 0.8f, 1f))
        assertEquals(Tuning.RING_BRIGHTNESS_D01_FACTORS, listOf(0.4f, 0.6f, 0.8f, 1f, 1.3f, 1.6f))
    }

    @Test
    fun onlyTheRingLinesChange() {
        // 背景地図の線の明るさの掛ける数は変わらない（線の明るさ・ランドマークの明るさは 1〜6 段のまま）
        assertEquals(listOf(0.4f, 0.6f, 0.8f, 1f, 1.3f, 1.6f), Tuning.BASEMAP_BRIGHTNESS_FACTORS)
        val base = HudSceneBuilder.build(state(80), rect, m)
        for (mode in DisplayMode.entries) for (pct in NavSettings.RING_BRIGHTNESS_CHOICES_PCT) {
            val s = HudSceneBuilder.build(state(pct, mode), rect, m)
            // 距離環の線（arcs）は全部、p / 100 を掛ける
            assertTrue(s.arcs.isNotEmpty())
            assertTrue("$mode $pct", s.arcs.all { it.brightness == pct / 100f })
            if (mode == DisplayMode.ARC) {
                // 距離の数字・方位の目盛りと文字（labels・segments）は変わらない
                assertEquals(base.labels, s.labels)
                assertEquals(base.segments, s.segments)
                assertEquals(base.arcs.map { it.copy(brightness = 1f) }, s.arcs.map { it.copy(brightness = 1f) })
            }
        }
    }
}
