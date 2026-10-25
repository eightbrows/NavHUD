package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.io.TerrainLookup
import io.github.eightbrows.navhud.core.model.Fix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

/**
 * 地上高（20261025-D07）: 海抜高度 − その場所の最高標高。海抜高度は Fix.mslAltM（AltitudeConverter）、なければ 楕円体高 − 標高オフセット。
 */
class AboveGroundTest {

    private val jst = ZoneId.of("Asia/Tokyo")

    /** 緯度 33 度より北は最高標高 1,200 m、33 度より南は海（0）、経度 134 度より東は unknown（null）、緯度 40 度より北は範囲外（null） */
    private val terrain = TerrainLookup { lat, lon ->
        when {
            lat > 40.0 || lon > 134.0 -> null
            lat >= 33.0 -> 1_200
            else -> 0
        }
    }

    private fun engine(withTerrain: Boolean = true) =
        NavEngine(NavSettings(altOffsetM = 36.0), jst, SourceKind.REPLAY).apply { if (withTerrain) setTerrain(terrain) }

    private fun fix(t: Long, lat: Double = 33.4, lon: Double = 133.0, alt: Double? = 1_336.0, msl: Double? = null) =
        Fix(timeMs = t, lat = lat, lon = lon, altRawM = alt, mslAltM = msl)

    @Test
    fun usesTheConvertedAltitudeWhenThereIsOne() {
        // AltitudeConverter の海抜高度 1,250 m: 地上高 50 m。ALT も海抜高度（20261025-D08。前は 楕円体高 − オフセット = 1,300 m）
        val s = engine().onFix(fix(0, msl = 1_250.0), 0)
        assertEquals(50.0, s.aglM!!, 1e-9)
        assertEquals(1_250.0, s.altM!!, 1e-9)
    }

    @Test
    fun fallsBackToTheAltitudeOffset() {
        // 海抜高度がない（API 33 以下など）: 楕円体高 1,336 − オフセット 36 = 1,300 m、地上高 100 m
        assertEquals(100.0, engine().onFix(fix(0), 0).aglM!!, 1e-9)
        // ALT も同じ 楕円体高 − オフセット
        assertEquals(1_300.0, engine().onFix(fix(0), 0).altM!!, 1e-9)
        // 海の上（最高標高 0）: 地上高 ＝ 海抜高度
        assertEquals(20.0, engine().onFix(fix(0, lat = 32.5, alt = 56.0), 0).aglM!!, 1e-9)
    }

    @Test
    fun negativeValuesStayNegative() {
        // 最高標高より低い（高さの誤差）: 0 にせず負のまま
        assertEquals(-30.0, engine().onFix(fix(0, msl = 1_170.0), 0).aglM!!, 1e-9)
    }

    @Test
    fun notShownWithoutData() {
        // unknown・範囲外・高さなし・最高標高のファイルをまだ読んでいない
        assertNull(engine().onFix(fix(0, lon = 134.5), 0).aglM)
        assertNull(engine().onFix(fix(0, lat = 41.0), 0).aglM)
        assertNull(engine().onFix(fix(0, alt = null), 0).aglM)
        assertNull(engine(withTerrain = false).onFix(fix(0), 0).aglM)
    }

    @Test
    fun hiddenDuringNoFixAndAppearsWhenTheFileIsLoaded() {
        val e = engine(withTerrain = false)
        e.onFix(fix(0), 0)
        assertNull(e.state.aglM)
        // あとから最高標高のファイルを読んだ: すぐ出る
        assertEquals(100.0, e.setTerrain(terrain).aglM!!, 1e-9)
        // NO FIX（10 秒より長く Fix が来ない）: 出さない（最後の値も出さない）
        val s = e.onTick(10_001)
        assertEquals(true, s.noFix)
        assertNull(s.aglM)
        // 位置が来れば、また出る
        assertEquals(100.0, e.onFix(fix(11_000), 11_000).aglM!!, 1e-9)
    }
}
