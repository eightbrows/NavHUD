package io.github.eightbrows.navhud.core.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 縮尺のボタン（操作列の真ん中、§6.1）の中身。 */
class RangeButtonFaceTest {

    @Test
    fun onlyTheR1DistanceWithoutAutoText() {
        // AUTO: 距離（R1 = 段の 1/2）だけを塗りつぶしで。「AUTO」の文字は出さない
        val auto = RangeButtonFace.of(1_000.0, auto = true, pan = false)
        assertEquals("500m", auto.text)
        assertTrue(auto.filled)
        assertFalse(auto.text.contains("AUTO"))
        // 手動: 同じ距離を枠だけで（「RNG」も出さない）
        val manual = RangeButtonFace.of(1_000.0, auto = false, pan = false)
        assertEquals("500m", manual.text)
        assertFalse(manual.filled)
        // R1 の書き方は今の表示と同じ
        assertEquals("2.5km", RangeButtonFace.of(5_000.0, auto = true, pan = false).text)
        assertEquals("50m", RangeButtonFace.of(100.0, auto = false, pan = false).text)
        assertEquals(HudFormat.rangeLabel(20_000.0), RangeButtonFace.of(20_000.0, auto = true, pan = false).text)
    }

    @Test
    fun panKeepsTheLabel() {
        // PAN 中は今までどおり「PAN」と距離（黄色、塗らない）
        val pan = RangeButtonFace.of(1_000.0, auto = true, pan = true)
        assertEquals("PAN\n500m", pan.text)
        assertTrue(pan.pan)
        assertFalse(pan.filled)
    }
}
