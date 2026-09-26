package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.model.SourceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class HeadingSelectorTest {

    private fun fix(speed: Float? = 10f, bearing: Float? = 90f, acc: Float? = 5f) =
        Fix(timeMs = 0, lat = 33.5, lon = 133.0, speedMps = speed, bearingDeg = bearing, horizAccM = acc)

    private val compass = 45f

    @Test
    fun hybridMovingAccurateUsesGps() {
        assertEquals(Heading(90f, HeadingSrc.GPS), HeadingSelector().select(SourceMode.HYBRID, fix(), compass))
    }

    @Test
    fun hybridFallsBackToCompass() {
        val toCompass = Heading(compass, HeadingSrc.COMPASS)
        val s = HeadingSelector()
        assertEquals("低速", toCompass, s.select(SourceMode.HYBRID, fix(speed = 1.0f), compass))
        assertEquals("低精度", toCompass, s.select(SourceMode.HYBRID, fix(acc = 20f), compass))
        assertEquals("方位 null", toCompass, s.select(SourceMode.HYBRID, fix(speed = 0f, bearing = null), compass))
    }

    @Test
    fun thresholdsAreInclusive() {
        val s = HeadingSelector()
        assertEquals(HeadingSrc.GPS, s.select(SourceMode.HYBRID, fix(speed = 1.4f, acc = 15f), compass).src)
    }

    @Test
    fun speedOrAccuracyUnknownStillUsesGps() {
        val s = HeadingSelector()
        assertEquals(HeadingSrc.GPS, s.select(SourceMode.HYBRID, fix(speed = null, acc = null), compass).src)
    }

    @Test
    fun gpsModeHoldsLastBearingWhenStopped() {
        val s = HeadingSelector()
        s.select(SourceMode.GPS, fix(bearing = 123f), null)
        assertEquals(Heading(123f, HeadingSrc.GPS), s.select(SourceMode.GPS, fix(speed = 0f, bearing = null), compass))
    }

    @Test
    fun hybridWithoutCompassHoldsGps() {
        val s = HeadingSelector()
        s.select(SourceMode.HYBRID, fix(bearing = 200f), null)
        assertEquals(Heading(200f, HeadingSrc.GPS), s.select(SourceMode.HYBRID, fix(speed = 0f, bearing = null), null))
    }

    @Test
    fun compassMode() {
        val s = HeadingSelector()
        assertEquals(Heading(compass, HeadingSrc.COMPASS), s.select(SourceMode.COMPASS, fix(), compass))
        assertEquals(Heading.NONE, s.select(SourceMode.COMPASS, fix(), null))
    }

    @Test
    fun noInputIsNone() {
        for (mode in SourceMode.entries) {
            assertEquals(mode.name, Heading.NONE, HeadingSelector().select(mode, null, null))
        }
    }
}
